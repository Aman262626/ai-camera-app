package com.aicamera.app.ai

import android.content.Context

enum class AIProvider(val displayName: String) {
    GEMINI("Google Gemini"),
    OPENAI("OpenAI"),
    DEEPSEEK("DeepSeek"),
    CUSTOM("Custom API")
}

/** Thin wrapper over SharedPreferences for the AI settings screen. */
object AISettings {
    private const val PREFS = "ai_camera_settings"
    private const val KEY_PROVIDER = "provider"
    private const val KEY_API_KEY = "api_key"
    private const val KEY_CUSTOM_URL = "custom_url"
    private const val KEY_FREE_TIER = "free_tier"
    private const val KEY_ENABLE_AI_EDIT = "enable_ai_edit"

    fun save(
        context: Context,
        provider: AIProvider,
        apiKey: String,
        customUrl: String,
        useFreeTier: Boolean,
        enableAiEdit: Boolean
    ) {
        prefs(context).edit()
            .putString(KEY_PROVIDER, provider.name)
            .putString(KEY_API_KEY, apiKey)
            .putString(KEY_CUSTOM_URL, customUrl)
            .putBoolean(KEY_FREE_TIER, useFreeTier)
            .putBoolean(KEY_ENABLE_AI_EDIT, enableAiEdit)
            .apply()
    }

    fun getProvider(context: Context): AIProvider {
        val name = prefs(context).getString(KEY_PROVIDER, AIProvider.GEMINI.name)
        return AIProvider.values().firstOrNull { it.name == name } ?: AIProvider.GEMINI
    }

    fun getApiKey(context: Context): String = prefs(context).getString(KEY_API_KEY, "") ?: ""
    fun getCustomUrl(context: Context): String = prefs(context).getString(KEY_CUSTOM_URL, "") ?: ""
    fun useFreeTier(context: Context): Boolean = prefs(context).getBoolean(KEY_FREE_TIER, true)
    fun isAiEditEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLE_AI_EDIT, false)

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
