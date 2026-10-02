package com.aicamera.app

import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.aicamera.app.ai.AIEditResult
import com.aicamera.app.ai.AIEnhanceClient
import com.aicamera.app.ai.AISettings
import com.aicamera.app.databinding.ActivityPreviewBinding
import com.aicamera.app.util.ImageEnhancer
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PhotoPreviewActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPreviewBinding
    private var photoPath: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPreviewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        photoPath = intent.getStringExtra(EXTRA_PHOTO_PATH)
        val bitmap = photoPath?.let { BitmapFactory.decodeFile(it) }
        if (bitmap == null) {
            Toast.makeText(this, "Could not load photo", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        binding.previewImage.setImageBitmap(bitmap)

        binding.btnAutoEnhance.setOnClickListener {
            val current = (binding.previewImage.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
                ?: bitmap
            val enhanced = ImageEnhancer.autoEnhance(current)
            binding.previewImage.setImageBitmap(enhanced)
        }

        binding.btnAiEdit.setOnClickListener {
            if (!AISettings.isAiEditEnabled(this)) {
                Toast.makeText(this, "Enable AI Edit in Settings first", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            val current = (binding.previewImage.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
                ?: bitmap
            runAiEdit(current)
        }

        binding.btnSavePhoto.setOnClickListener {
            val current = (binding.previewImage.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
                ?: bitmap
            savePhoto(current)
        }
    }

    private fun runAiEdit(bitmap: android.graphics.Bitmap) {
        binding.progressBar.visibility = android.view.View.VISIBLE
        lifecycleScope.launch {
            val result = AIEnhanceClient.enhance(
                provider = AISettings.getProvider(this@PhotoPreviewActivity),
                apiKey = AISettings.getApiKey(this@PhotoPreviewActivity),
                customUrl = AISettings.getCustomUrl(this@PhotoPreviewActivity),
                useFreeTier = AISettings.useFreeTier(this@PhotoPreviewActivity),
                photo = bitmap
            )
            binding.progressBar.visibility = android.view.View.GONE
            when (result) {
                is AIEditResult.Success -> binding.previewImage.setImageBitmap(result.bitmap)
                is AIEditResult.Failure -> Toast.makeText(
                    this@PhotoPreviewActivity,
                    "AI Edit failed: ${result.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun savePhoto(bitmap: android.graphics.Bitmap) {
        val dir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        val name = "AI_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".jpg"
        val file = File(dir, name)
        FileOutputStream(file).use { out ->
            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 95, out)
        }
        Toast.makeText(this, "Saved: ${file.absolutePath}", Toast.LENGTH_LONG).show()
        finish()
    }

    companion object {
        const val EXTRA_PHOTO_PATH = "extra_photo_path"
    }
}
