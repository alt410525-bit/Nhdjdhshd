package com.jarvis.ai

import android.content.Context

object Prefs {
    private const val FILE = "jarvis_prefs"
    private const val KEY_API = "gemini_api_key"
    private const val KEY_MODEL = "gemini_model"
    private const val KEY_PERSONA = "persona_name"
    const val DEFAULT_MODEL = "gemini-3.5-flash"
    const val DEFAULT_PERSONA = "Jarvis"

    private fun prefs(ctx: Context) =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun getApiKey(ctx: Context): String = prefs(ctx).getString(KEY_API, "") ?: ""

    fun saveApiKey(ctx: Context, key: String) {
        prefs(ctx).edit().putString(KEY_API, key.trim()).apply()
    }

    fun removeApiKey(ctx: Context) {
        prefs(ctx).edit().remove(KEY_API).apply()
    }

    fun getModel(ctx: Context): String =
        prefs(ctx).getString(KEY_MODEL, DEFAULT_MODEL) ?: DEFAULT_MODEL

    fun saveModel(ctx: Context, model: String) {
        prefs(ctx).edit().putString(KEY_MODEL, model.trim()).apply()
    }

    fun getPersona(ctx: Context): String =
        prefs(ctx).getString(KEY_PERSONA, DEFAULT_PERSONA) ?: DEFAULT_PERSONA

    fun savePersona(ctx: Context, name: String) {
        prefs(ctx).edit().putString(KEY_PERSONA, name.trim().ifEmpty { DEFAULT_PERSONA }).apply()
    }
}
