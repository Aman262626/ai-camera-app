package com.aicamera.app.ai

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

sealed class AIEditResult {
    data class Success(val bitmap: Bitmap) : AIEditResult()
    data class Failure(val message: String) : AIEditResult()
}

/**
 * Sends a captured photo to the AI provider configured in Settings and
 * returns the enhanced/edited image. Every provider branch degrades
 * gracefully: if a provider has no image-edit endpoint (DeepSeek today)
 * or the request fails, it reports a clear Failure instead of crashing,
 * so the caller can fall back to the free on-device enhancer.
 *
 * A "free option" (no key) path is left pluggable via FREE_ENDPOINT so a
 * no-key public enhancement endpoint can be wired in later without
 * touching the UI layer.
 */
object AIEnhanceClient {

    private const val FREE_ENDPOINT = "" // optional: fill with a no-key enhancement endpoint
    private const val GEMINI_MODEL = "gemini-2.5-flash-image"
    private const val PROMPT =
        "Enhance this photo: increase sharpness and clarity, reduce noise and blur, " +
            "improve dynamic range, keep colors natural and realistic (no artificial tint), " +
            "do not add or remove objects, do not change composition."

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun enhance(
        provider: AIProvider,
        apiKey: String,
        customUrl: String,
        useFreeTier: Boolean,
        photo: Bitmap
    ): AIEditResult = withContext(Dispatchers.IO) {
        try {
            when (provider) {
                AIProvider.GEMINI -> geminiEdit(apiKey, useFreeTier, photo)
                AIProvider.OPENAI -> openAiEdit(apiKey, photo)
                AIProvider.DEEPSEEK -> AIEditResult.Failure(
                    "DeepSeek does not currently offer an image-edit endpoint. " +
                        "Switch to Gemini, OpenAI, or a Custom endpoint for AI Edit."
                )
                AIProvider.CUSTOM -> customEdit(customUrl.ifBlank { FREE_ENDPOINT }, apiKey, photo)
            }
        } catch (e: Exception) {
            Log.e("AIEnhanceClient", "AI edit failed", e)
            AIEditResult.Failure(e.message ?: "Unknown error calling AI provider")
        }
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
        return Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
    }

    private fun geminiEdit(apiKey: String, useFreeTier: Boolean, photo: Bitmap): AIEditResult {
        if (apiKey.isBlank() && !useFreeTier) {
            return AIEditResult.Failure("Add a Gemini API key in Settings (or enable the free option).")
        }
        val url =
            "https://generativelanguage.googleapis.com/v1beta/models/$GEMINI_MODEL:generateContent?key=$apiKey"
        val base64Image = bitmapToBase64(photo)
        val body = JSONObject().apply {
            put("contents", org.json.JSONArray().put(
                JSONObject().put("parts", org.json.JSONArray()
                    .put(JSONObject().put("text", PROMPT))
                    .put(JSONObject().put("inline_data", JSONObject()
                        .put("mime_type", "image/jpeg")
                        .put("data", base64Image)))
                )
            ))
        }
        val request = Request.Builder()
            .url(url)
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                return AIEditResult.Failure("Gemini error: HTTP ${response.code}")
            }
            val json = JSONObject(response.body?.string().orEmpty())
            val parts = json.optJSONArray("candidates")
                ?.optJSONObject(0)
                ?.optJSONObject("content")
                ?.optJSONArray("parts") ?: return AIEditResult.Failure("No image returned by Gemini")
            for (i in 0 until parts.length()) {
                val inline = parts.optJSONObject(i)?.optJSONObject("inline_data")
                val data = inline?.optString("data")
                if (!data.isNullOrBlank()) {
                    val bytes = Base64.decode(data, Base64.NO_WRAP)
                    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    if (bmp != null) return AIEditResult.Success(bmp)
                }
            }
            return AIEditResult.Failure("Gemini responded without image data")
        }
    }

    private fun openAiEdit(apiKey: String, photo: Bitmap): AIEditResult {
        if (apiKey.isBlank()) return AIEditResult.Failure("Add an OpenAI API key in Settings.")
        val stream = ByteArrayOutputStream()
        photo.compress(Bitmap.CompressFormat.PNG, 100, stream)
        val imageBytes = stream.toByteArray()

        val multipart = okhttp3.MultipartBody.Builder()
            .setType(okhttp3.MultipartBody.FORM)
            .addFormDataPart("model", "gpt-image-1")
            .addFormDataPart("prompt", PROMPT)
            .addFormDataPart(
                "image", "photo.png",
                imageBytes.toRequestBody("image/png".toMediaType())
            )
            .build()

        val request = Request.Builder()
            .url("https://api.openai.com/v1/images/edits")
            .addHeader("Authorization", "Bearer $apiKey")
            .post(multipart)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                return AIEditResult.Failure("OpenAI error: HTTP ${response.code}")
            }
            val json = JSONObject(response.body?.string().orEmpty())
            val b64 = json.optJSONArray("data")?.optJSONObject(0)?.optString("b64_json")
            if (b64.isNullOrBlank()) return AIEditResult.Failure("OpenAI responded without image data")
            val bytes = Base64.decode(b64, Base64.NO_WRAP)
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                ?: return AIEditResult.Failure("Could not decode OpenAI image")
            return AIEditResult.Success(bmp)
        }
    }

    /**
     * Generic custom endpoint: POST { "image_base64": ..., "prompt": ... }
     * expects back { "image_base64": "..." }. Lets you point this at your
     * own server or any other provider that follows this simple contract.
     */
    private fun customEdit(url: String, apiKey: String, photo: Bitmap): AIEditResult {
        if (url.isBlank()) return AIEditResult.Failure("Add a custom endpoint URL in Settings.")
        val payload = JSONObject().apply {
            put("image_base64", bitmapToBase64(photo))
            put("prompt", PROMPT)
        }
        val builder = Request.Builder()
            .url(url)
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
        if (apiKey.isNotBlank()) builder.addHeader("Authorization", "Bearer $apiKey")

        client.newCall(builder.build()).execute().use { response ->
            if (!response.isSuccessful) {
                return AIEditResult.Failure("Custom API error: HTTP ${response.code}")
            }
            val json = JSONObject(response.body?.string().orEmpty())
            val b64 = json.optString("image_base64")
            if (b64.isBlank()) return AIEditResult.Failure("Custom API responded without image_base64")
            val bytes = Base64.decode(b64, Base64.NO_WRAP)
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                ?: return AIEditResult.Failure("Could not decode custom API image")
            return AIEditResult.Success(bmp)
        }
    }
}
