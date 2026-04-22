package com.eyebrowscroll.app

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.eyebrowscroll.app.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val requestCameraPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        updateUI()
        if (!granted) Toast.makeText(this, "Camera permission is required", Toast.LENGTH_LONG).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupButtons()
        setupSensitivitySlider()
    }

    override fun onResume() {
        super.onResume()
        updateUI()
    }

    // -------------------------------------------------------------------------
    // Setup
    // -------------------------------------------------------------------------

    private fun setupButtons() {
        binding.btnAccessibility.setOnClickListener { openAccessibilitySettings() }

        binding.btnCamera.setOnClickListener {
            if (!hasCameraPermission()) requestCameraPermission.launch(Manifest.permission.CAMERA)
            else Toast.makeText(this, "Already granted ✓", Toast.LENGTH_SHORT).show()
        }

        binding.btnStartStop.setOnClickListener {
            if (CameraForegroundService.isRunning) stopControl() else startControl()
        }
    }

    private fun setupSensitivitySlider() {
        binding.seekSensitivity.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val label = when {
                    progress < 30 -> "Low"
                    progress < 70 -> "Medium"
                    else          -> "High"
                }
                binding.tvSensitivityValue.text = "$label ($progress)"
                CameraForegroundService.sensitivity = progress
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    // -------------------------------------------------------------------------
    // Start / Stop
    // -------------------------------------------------------------------------

    private fun startControl() {
        if (!hasAccessibilityPermission()) {
            Toast.makeText(this, "Enable the Accessibility Service first", Toast.LENGTH_LONG).show()
            openAccessibilitySettings()
            return
        }
        if (!hasCameraPermission()) {
            requestCameraPermission.launch(Manifest.permission.CAMERA)
            return
        }

        // Ask Samsung / Android to not kill the service due to battery optimisation
        requestIgnoreBatteryOptimisation()

        CameraForegroundService.start(this)
        updateUI()
        Toast.makeText(this, "👅 FaceScroll started!", Toast.LENGTH_SHORT).show()
    }

    private fun stopControl() {
        CameraForegroundService.stop(this)
        updateUI()
        Toast.makeText(this, "FaceScroll stopped", Toast.LENGTH_SHORT).show()
    }

    // -------------------------------------------------------------------------
    // Battery optimisation (key fix for background on Samsung)
    // -------------------------------------------------------------------------

    private fun requestIgnoreBatteryOptimisation() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            } catch (e: Exception) {
                // Some ROMs don't support the direct intent — open general battery settings
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
        }
    }

    // -------------------------------------------------------------------------
    // UI state
    // -------------------------------------------------------------------------

    private fun updateUI() {
        val a11yOk   = hasAccessibilityPermission()
        val camOk    = hasCameraPermission()
        val running  = CameraForegroundService.isRunning

        binding.dotAccessibility.setBackgroundResource(
            if (a11yOk) R.drawable.status_dot_green else R.drawable.status_dot_red)
        binding.tvAccessibilityStatus.text =
            if (a11yOk) "Enabled ✓" else "Not enabled — tap to fix"
        binding.btnAccessibility.text = if (a11yOk) "Settings" else "Enable"

        binding.dotCamera.setBackgroundResource(
            if (camOk) R.drawable.status_dot_green else R.drawable.status_dot_red)
        binding.tvCameraStatus.text =
            if (camOk) "Granted ✓" else "Not granted — tap to fix"
        binding.btnCamera.text = if (camOk) "Granted" else "Grant"

        if (running) {
            binding.btnStartStop.text = "STOP FACE CONTROL"
            binding.btnStartStop.backgroundTintList =
                ContextCompat.getColorStateList(this, R.color.status_red)
            binding.tvLiveStatus.text = "● Running — show tongue or teeth to scroll"
            binding.tvLiveStatus.setTextColor(ContextCompat.getColor(this, R.color.status_green))
        } else {
            binding.btnStartStop.text = "START FACE CONTROL"
            binding.btnStartStop.backgroundTintList =
                ContextCompat.getColorStateList(this, R.color.accent_blue)
            binding.tvLiveStatus.text = "● Stopped"
            binding.tvLiveStatus.setTextColor(ContextCompat.getColor(this, android.R.color.darker_gray))
        }
    }

    // -------------------------------------------------------------------------
    // Permission helpers
    // -------------------------------------------------------------------------

    private fun hasCameraPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED

    private fun hasAccessibilityPermission(): Boolean {
        val am = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        return am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { it.resolveInfo.serviceInfo.packageName == packageName }
    }

    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        Toast.makeText(this, "Find 'FaceScroll' and enable it", Toast.LENGTH_LONG).show()
    }
}
