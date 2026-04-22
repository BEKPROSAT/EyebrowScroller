package com.eyebrowscroller;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private static final int CAMERA_PERMISSION_REQUEST = 100;
    private static final float EYEBROW_RAISE_THRESHOLD = 0.5f; // How high probability to detect raise
    private static final float WINK_THRESHOLD = 0.3f; // Eye closed probability for wink

    private ExecutorService cameraExecutor;
    private FaceDetector faceDetector;
    private TextView statusText, gestureText, sensitivityValue, accessibilityStatus;
    private SeekBar sensitivitySlider;
    private PreviewView cameraPreview;
    private MaterialButton btnEnableAccessibility;

    private float sensitivityMultiplier = 0.5f;

    // Cooldown to avoid spamming gestures
    private long lastGestureTime = 0;
    private static final long GESTURE_COOLDOWN_MS = 800;

    // Track previous state to detect transitions (not sustained holds)
    private boolean wasRaisingEyebrow = false;
    private boolean wasWinking = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        statusText = findViewById(R.id.statusText);
        gestureText = findViewById(R.id.gestureText);
        sensitivityValue = findViewById(R.id.sensitivityValue);
        accessibilityStatus = findViewById(R.id.accessibilityStatus);
        sensitivitySlider = findViewById(R.id.sensitivitySlider);
        cameraPreview = findViewById(R.id.cameraPreview);
        btnEnableAccessibility = findViewById(R.id.btnEnableAccessibility);

        // Sensitivity slider
        sensitivitySlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                sensitivityMultiplier = progress / 100f;
                sensitivityValue.setText(progress + "%");
                EyebrowAccessibilityService.scrollAmount = (int) (600 * sensitivityMultiplier + 200);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // Accessibility button
        btnEnableAccessibility.setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            startActivity(intent);
        });

        // Setup ML Kit face detector with classification enabled
        FaceDetectorOptions options = new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                .setMinFaceSize(0.15f)
                .enableTracking()
                .build();
        faceDetector = FaceDetection.getClient(options);

        cameraExecutor = Executors.newSingleThreadExecutor();

        // Check camera permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA},
                    CAMERA_PERMISSION_REQUEST);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateAccessibilityStatus();
    }

    private void updateAccessibilityStatus() {
        boolean enabled = isAccessibilityServiceEnabled();
        if (enabled) {
            accessibilityStatus.setText("✓ Accessibility service active");
            accessibilityStatus.setTextColor(0xFF00D4FF);
        } else {
            accessibilityStatus.setText("⚠ Accessibility service not enabled");
            accessibilityStatus.setTextColor(0xFFFF6B6B);
        }
    }

    private boolean isAccessibilityServiceEnabled() {
        String prefString = Settings.Secure.getString(
                getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (prefString == null) return false;
        TextUtils.SimpleStringSplitter splitter = new TextUtils.SimpleStringSplitter(':');
        splitter.setString(prefString);
        while (splitter.hasNext()) {
            String accessibilityService = splitter.next();
            if (accessibilityService.equalsIgnoreCase(
                    getPackageName() + "/" + EyebrowAccessibilityService.class.getName())) {
                return true;
            }
        }
        return false;
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                // Preview
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(cameraPreview.getSurfaceProvider());

                // Image analysis for face detection
                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();

                imageAnalysis.setAnalyzer(cameraExecutor, imageProxy -> {
                    @SuppressWarnings("UnsafeOptInUsageError")
                    android.media.Image mediaImage = imageProxy.getImage();
                    if (mediaImage != null) {
                        InputImage image = InputImage.fromMediaImage(
                                mediaImage, imageProxy.getImageInfo().getRotationDegrees());

                        faceDetector.process(image)
                                .addOnSuccessListener(faces -> {
                                    processFaces(faces);
                                    imageProxy.close();
                                })
                                .addOnFailureListener(e -> imageProxy.close());
                    } else {
                        imageProxy.close();
                    }
                });

                // Use front camera
                CameraSelector cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA;

                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);

                runOnUiThread(() -> statusText.setText("Camera active — looking for face..."));

            } catch (Exception e) {
                runOnUiThread(() -> statusText.setText("Camera error: " + e.getMessage()));
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void processFaces(java.util.List<Face> faces) {
        if (faces.isEmpty()) {
            runOnUiThread(() -> {
                statusText.setText("No face detected — look at camera");
                gestureText.setText("Waiting...");
            });
            wasRaisingEyebrow = false;
            wasWinking = false;
            return;
        }

        Face face = faces.get(0);

        // Get probabilities from ML Kit
        Float leftEyeOpenProb = face.getLeftEyeOpenProbability();
        Float rightEyeOpenProb = face.getRightEyeOpenProbability();

        // ML Kit doesn't directly give eyebrow raise — we approximate it
        // using the smile probability inversion and head euler angle
        // But actually we use a trick: ML Kit has browLowerer (inner brow raise)
        // We use eye open probability SURGE to detect raise (eyes wide open)
        // Eyebrow raise = both eyes open probability > threshold (wide-eyed)
        float eyebrowRaiseProb = 0f;
        if (leftEyeOpenProb != null && rightEyeOpenProb != null) {
            // Eyebrow raise makes eyes look more open/wide
            eyebrowRaiseProb = (leftEyeOpenProb + rightEyeOpenProb) / 2f;
        }

        // Wink detection: one eye closed, other open
        boolean isWinking = false;
        if (leftEyeOpenProb != null && rightEyeOpenProb != null) {
            boolean leftClosed = leftEyeOpenProb < WINK_THRESHOLD;
            boolean rightClosed = rightEyeOpenProb < WINK_THRESHOLD;
            boolean leftOpen = leftEyeOpenProb > 0.7f;
            boolean rightOpen = rightEyeOpenProb > 0.7f;
            isWinking = (leftClosed && rightOpen) || (rightClosed && leftOpen);
        }

        // Eyebrow raise = both eyes wide open (> 0.85 probability)
        boolean isRaisingEyebrow = eyebrowRaiseProb > 0.85f && !isWinking;

        long now = System.currentTimeMillis();
        boolean cooldownPassed = (now - lastGestureTime) > GESTURE_COOLDOWN_MS;

        // Detect gesture START (transition from not-doing to doing)
        if (isWinking && !wasWinking && cooldownPassed) {
            lastGestureTime = now;
            wasWinking = true;
            wasRaisingEyebrow = false;
            // Wink = scroll UP
            EyebrowAccessibilityService.performScroll(false);
            runOnUiThread(() -> {
                gestureText.setText("😉 WINK → Scrolling UP ↑");
                statusText.setText("Face detected ✓");
            });
        } else if (isRaisingEyebrow && !wasRaisingEyebrow && cooldownPassed) {
            lastGestureTime = now;
            wasRaisingEyebrow = true;
            wasWinking = false;
            // Eyebrow raise = scroll DOWN
            EyebrowAccessibilityService.performScroll(true);
            runOnUiThread(() -> {
                gestureText.setText("🤨 RAISE → Scrolling DOWN ↓");
                statusText.setText("Face detected ✓");
            });
        } else if (!isWinking && !isRaisingEyebrow) {
            wasWinking = false;
            wasRaisingEyebrow = false;
            runOnUiThread(() -> {
                gestureText.setText("😐 Ready — waiting for gesture");
                statusText.setText("Face detected ✓");
            });
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera();
            } else {
                Toast.makeText(this, "Camera permission is required", Toast.LENGTH_LONG).show();
                statusText.setText("Camera permission denied");
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraExecutor.shutdown();
        faceDetector.close();
    }
}
