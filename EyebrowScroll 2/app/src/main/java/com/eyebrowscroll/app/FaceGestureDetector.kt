package com.eyebrowscroll.app

import android.content.Context
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceContour
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions

/**
 * Detects tongue-out and teeth-visible gestures using ML Kit face contours.
 *
 *  TONGUE OUT   → scroll DOWN
 *  TEETH SHOWN  → scroll UP
 *
 * Uses lip contour Y-gap relative to face height as the signal.
 */
class FaceGestureDetector(
    private val context: Context,
    private val onGestureDetected: (Gesture) -> Unit
) {
    enum class Gesture { TONGUE_OUT, TEETH_VISIBLE, NONE }

    private var lastGestureTime = 0L
    private val COOLDOWN_MS = 900L

    var sensitivity: Int = 50
        set(value) {
            field = value.coerceIn(0, 100)
            val factor = field / 100f
            teethMinRatio  = 0.06f - factor * 0.03f   // 0.03 – 0.06
            tongueMinRatio = 0.12f - factor * 0.05f   // 0.07 – 0.12
        }

    private var teethMinRatio  = 0.06f
    private var tongueMinRatio = 0.12f

    private val detector: FaceDetector by lazy {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .setMinFaceSize(0.2f)
            .build()
        FaceDetection.getClient(options)
    }

    fun processImage(image: InputImage) {
        detector.process(image)
            .addOnSuccessListener { faces ->
                if (faces.isNotEmpty()) analyzeFace(faces[0])
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Detection failed", e)
            }
    }

    private fun analyzeFace(face: Face) {
        val now = System.currentTimeMillis()
        if (now - lastGestureTime < COOLDOWN_MS) return

        val upperLip = face.getContour(FaceContour.UPPER_LIP_TOP)?.points
        val lowerLip = face.getContour(FaceContour.LOWER_LIP_BOTTOM)?.points

        if (upperLip.isNullOrEmpty() || lowerLip.isNullOrEmpty()) return

        val faceHeight = face.boundingBox.height().toFloat()
        if (faceHeight < 1f) return

        val upperLipY  = upperLip.map { it.y }.average().toFloat()
        val lowerLipY  = lowerLip.map { it.y }.average().toFloat()
        val openRatio  = (lowerLipY - upperLipY) / faceHeight

        Log.v(TAG, "openRatio=$openRatio teeth>=$teethMinRatio tongue>=$tongueMinRatio")

        when {
            openRatio >= tongueMinRatio -> {
                lastGestureTime = now
                Log.d(TAG, "TONGUE OUT → scroll DOWN")
                onGestureDetected(Gesture.TONGUE_OUT)
            }
            openRatio >= teethMinRatio -> {
                lastGestureTime = now
                Log.d(TAG, "TEETH VISIBLE → scroll UP")
                onGestureDetected(Gesture.TEETH_VISIBLE)
            }
        }
    }

    fun close() = detector.close()

    companion object {
        private const val TAG = "FaceGestureDetector"
    }
}
