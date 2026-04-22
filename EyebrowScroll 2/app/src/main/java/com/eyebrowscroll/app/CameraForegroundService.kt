package com.eyebrowscroll.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Foreground service that keeps the front camera alive in the background.
 * Uses a WakeLock to prevent the CPU from sleeping during detection.
 */
class CameraForegroundService : LifecycleService() {

    private lateinit var cameraExecutor: ExecutorService
    private lateinit var gestureDetector: FaceGestureDetector
    private var cameraProvider: ProcessCameraProvider? = null
    private var wakeLock: PowerManager.WakeLock? = null

    companion object {
        private const val TAG             = "CameraService"
        const val  CHANNEL_ID             = "eyebrow_scroll_channel"
        private const val NOTIFICATION_ID = 1001

        var isRunning = false
            private set

        var sensitivity: Int = 50

        fun start(context: Context) {
            val intent = Intent(context, CameraForegroundService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, CameraForegroundService::class.java))
        }
    }

    override fun onCreate() {
        super.onCreate()
        cameraExecutor  = Executors.newSingleThreadExecutor()
        gestureDetector = FaceGestureDetector(this) { gesture -> handleGesture(gesture) }
        gestureDetector.sensitivity = sensitivity
        createNotificationChannel()

        // WakeLock: keeps CPU running so detection doesn't freeze when screen is off
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "EyebrowScroll::CameraWakeLock"
        ).also { it.acquire(12 * 60 * 60 * 1000L) } // max 12 hours
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        isRunning = true
        startForeground(NOTIFICATION_ID, buildNotification())
        startCamera()
        Log.d(TAG, "Service started ✓")
        // START_STICKY: Android will restart this service if killed
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        cameraProvider?.unbindAll()
        cameraExecutor.shutdown()
        gestureDetector.close()
        wakeLock?.let { if (it.isHeld) it.release() }
        Log.d(TAG, "Service destroyed")
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    // -------------------------------------------------------------------------
    // Camera
    // -------------------------------------------------------------------------

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            try {
                cameraProvider = future.get()

                val analyzer = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .apply {
                        setAnalyzer(cameraExecutor) { imageProxy -> processFrame(imageProxy) }
                    }

                cameraProvider?.unbindAll()
                cameraProvider?.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_FRONT_CAMERA,
                    analyzer
                )
                Log.d(TAG, "Camera bound ✓")
            } catch (e: Exception) {
                Log.e(TAG, "Camera bind failed", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    @androidx.camera.core.ExperimentalGetImage
    private fun processFrame(imageProxy: ImageProxy) {
        imageProxy.image?.let { mediaImage ->
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
            gestureDetector.processImage(image)
        }
        imageProxy.close()
    }

    // -------------------------------------------------------------------------
    // Gesture → Scroll
    // -------------------------------------------------------------------------

    private fun handleGesture(gesture: FaceGestureDetector.Gesture) {
        val service = EyebrowAccessibilityService.instance
        if (service == null) {
            Log.w(TAG, "Accessibility service not connected")
            return
        }
        when (gesture) {
            FaceGestureDetector.Gesture.TONGUE_OUT      -> service.scrollDown()
            FaceGestureDetector.Gesture.TEETH_VISIBLE   -> service.scrollUp()
            FaceGestureDetector.Gesture.NONE            -> {}
        }
    }

    // -------------------------------------------------------------------------
    // Notification
    // -------------------------------------------------------------------------

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "EyebrowScroll Active",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shown while gesture control is running"
            setShowBadge(false)
        }
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val pi = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("👅 FaceScroll is active")
            .setContentText("Show tongue = scroll down  |  Show teeth = scroll up")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentIntent(pi)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }
}
