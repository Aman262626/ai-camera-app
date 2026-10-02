package com.aicamera.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.RectShape
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.aicamera.app.databinding.ActivityMainBinding
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var imageCapture: ImageCapture? = null
    private var camera: Camera? = null
    private lateinit var cameraExecutor: ExecutorService
    private var lensFacing = CameraSelector.LENS_FACING_BACK
    private var flashMode = ImageCapture.FLASH_MODE_OFF
    private var timerSeconds = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        cameraExecutor = Executors.newSingleThreadExecutor()

        setupTimerSpinner()
        setupGridOverlay()

        if (hasCameraPermission()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), REQUEST_CODE_PERMISSIONS)
        }

        binding.btnCapture.setOnClickListener { onCaptureClicked() }
        binding.btnSwitchCamera.setOnClickListener { toggleCameraLens() }
        binding.btnFlash.setOnClickListener { cycleFlashMode() }
        binding.btnHdr.setOnCheckedChangeListener { _, isChecked ->
            Toast.makeText(
                this,
                if (isChecked) "HDR enabled (best-effort, device dependent)" else "HDR disabled",
                Toast.LENGTH_SHORT
            ).show()
        }
        binding.btnGrid.setOnCheckedChangeListener { _, isChecked ->
            binding.gridOverlay.visibility = if (isChecked) android.view.View.VISIBLE else android.view.View.GONE
        }
        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        binding.zoomSeekBar.max = 100
        binding.zoomSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) camera?.cameraControl?.setLinearZoom(progress / 100f)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun setupTimerSpinner() {
        val options = listOf("Timer: Off", "3s", "5s", "10s")
        binding.timerSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, options)
        binding.timerSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                timerSeconds = when (position) {
                    1 -> 3
                    2 -> 5
                    3 -> 10
                    else -> 0
                }
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }
    }

    private fun setupGridOverlay() {
        val drawable = object : ShapeDrawable(RectShape()) {
            override fun draw(canvas: Canvas) {
                val paint = Paint().apply {
                    color = Color.argb(140, 255, 255, 255)
                    strokeWidth = 2f
                }
                val w = bounds.width().toFloat()
                val h = bounds.height().toFloat()
                canvas.drawLine(w / 3, 0f, w / 3, h, paint)
                canvas.drawLine(2 * w / 3, 0f, 2 * w / 3, h, paint)
                canvas.drawLine(0f, h / 3, w, h / 3, paint)
                canvas.drawLine(0f, 2 * h / 3, w, 2 * h / 3, paint)
            }
        }
        binding.gridOverlay.background = drawable
    }

    private fun hasCameraPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS && hasCameraPermission()) {
            startCamera()
        } else if (requestCode == REQUEST_CODE_PERMISSIONS) {
            Toast.makeText(this, "Camera permission is required", Toast.LENGTH_LONG).show()
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.previewView.surfaceProvider)
            }

            imageCapture = ImageCapture.Builder()
                .setFlashMode(flashMode)
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .build()

            val cameraSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()

            try {
                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture)
            } catch (exc: Exception) {
                Log.e(TAG, "Camera bind failed", exc)
                Toast.makeText(this, "Could not start camera: ${exc.message}", Toast.LENGTH_LONG).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun toggleCameraLens() {
        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
            CameraSelector.LENS_FACING_FRONT
        } else {
            CameraSelector.LENS_FACING_BACK
        }
        startCamera()
    }

    private fun cycleFlashMode() {
        flashMode = when (flashMode) {
            ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_AUTO
            ImageCapture.FLASH_MODE_AUTO -> ImageCapture.FLASH_MODE_ON
            else -> ImageCapture.FLASH_MODE_OFF
        }
        imageCapture?.flashMode = flashMode
        val label = when (flashMode) {
            ImageCapture.FLASH_MODE_AUTO -> "Flash: Auto"
            ImageCapture.FLASH_MODE_ON -> "Flash: On"
            else -> "Flash: Off"
        }
        Toast.makeText(this, label, Toast.LENGTH_SHORT).show()
    }

    private fun onCaptureClicked() {
        if (timerSeconds > 0) {
            Toast.makeText(this, "Capturing in ${timerSeconds}s...", Toast.LENGTH_SHORT).show()
            Handler(Looper.getMainLooper()).postDelayed({ takePhoto() }, timerSeconds * 1000L)
        } else {
            takePhoto()
        }
    }

    private fun takePhoto() {
        val imageCapture = imageCapture ?: return
        val name = "IMG_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(java.util.Date()) + ".jpg"
        val dir = getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES)
        val photoFile = File(dir, name)
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "Capture failed", exc)
                    Toast.makeText(this@MainActivity, "Capture failed: ${exc.message}", Toast.LENGTH_LONG).show()
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val intent = Intent(this@MainActivity, PhotoPreviewActivity::class.java)
                    intent.putExtra(PhotoPreviewActivity.EXTRA_PHOTO_PATH, photoFile.absolutePath)
                    startActivity(intent)
                }
            }
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    companion object {
        private const val TAG = "AICameraApp"
        private const val REQUEST_CODE_PERMISSIONS = 10
    }
}
