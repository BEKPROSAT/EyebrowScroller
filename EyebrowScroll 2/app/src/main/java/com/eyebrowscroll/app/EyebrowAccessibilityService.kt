package com.eyebrowscroll.app

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent

/**
 * Accessibility Service that performs swipe gestures on whatever app is
 * currently in the foreground — exactly like a real finger swipe.
 *
 * Scroll DOWN = swipe UP  (finger moves from center toward top)
 * Scroll UP   = swipe DOWN (finger moves from center toward bottom)
 */
class EyebrowAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "EyebrowA11y"

        // Static reference so CameraForegroundService can call it
        var instance: EyebrowAccessibilityService? = null
            private set

        fun isRunning() = instance != null
    }

    private var screenWidth  = 1080
    private var screenHeight = 2400

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        updateScreenSize()
        Log.d(TAG, "Accessibility service connected ✓")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        Log.d(TAG, "Accessibility service destroyed")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) { /* not used */ }
    override fun onInterrupt() { /* not used */ }

    // -------------------------------------------------------------------------
    // Public API called by CameraForegroundService
    // -------------------------------------------------------------------------

    fun scrollDown() {
        Log.d(TAG, "Performing scroll DOWN (swipe UP)")
        performSwipe(
            startY  = screenHeight * 0.65f,
            endY    = screenHeight * 0.25f
        )
    }

    fun scrollUp() {
        Log.d(TAG, "Performing scroll UP (swipe DOWN)")
        performSwipe(
            startY  = screenHeight * 0.35f,
            endY    = screenHeight * 0.75f
        )
    }

    // -------------------------------------------------------------------------
    // Internal
    // -------------------------------------------------------------------------

    private fun performSwipe(startY: Float, endY: Float) {
        val centerX = screenWidth / 2f

        val path = Path().apply {
            moveTo(centerX, startY)
            lineTo(centerX, endY)
        }

        val gesture = GestureDescription.Builder()
            .addStroke(
                GestureDescription.StrokeDescription(
                    path,
                    0L,          // start time
                    350L         // duration ms — feels natural
                )
            )
            .build()

        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription) {
                Log.d(TAG, "Swipe gesture completed")
            }
            override fun onCancelled(gestureDescription: GestureDescription) {
                Log.w(TAG, "Swipe gesture cancelled")
            }
        }, null)
    }

    private fun updateScreenSize() {
        try {
            val wm = getSystemService(WINDOW_SERVICE) as WindowManager
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(metrics)
            screenWidth  = metrics.widthPixels
            screenHeight = metrics.heightPixels
            Log.d(TAG, "Screen: ${screenWidth}x${screenHeight}")
        } catch (e: Exception) {
            Log.e(TAG, "Could not get screen size", e)
        }
    }
}
