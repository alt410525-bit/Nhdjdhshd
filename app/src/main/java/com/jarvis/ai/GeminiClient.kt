package com.jarvis.ai

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object GeminiClient {

    private const val BASE = "https://generativelanguage.googleapis.com/v1beta/models/"

    fun generate(
        apiKey: String,
        model: String,
        systemPrompt: String,
        history: List<Pair<String, String>>
    ): String {
        val url = URL(BASE + model + ":generateContent")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15000
            readTimeout = 60000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("x-goog-api-key", apiKey)
        }

        val contents = JSONArray()
        for ((role, text) in history) {
            contents.put(JSONObject().apply {
                put("role", role)
                put("parts", JSONArray().put(JSONObject().put("text", text)))
            })
        }

        val body = JSONObject().apply {
            put("systemInstruction", JSONObject().apply {
                put("parts", JSONArray().put(JSONObject().put("text", systemPrompt)))
            })
            put("contents", contents)
            put("generationConfig", JSONObject().put("temperature", 0.9))
        }

        try {
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: ""

            if (code !in 200..299) {
                throw Exception(friendlyError(code, text))
            }

            val json = JSONObject(text)
            val candidates = json.optJSONArray("candidates")
                ?: throw Exception("Gemini ne koi jawab nahi diya (shayad safety filter ne rok diya).")
            val parts = candidates.getJSONObject(0).getJSONObject("content").getJSONArray("parts")
            val sb = StringBuilder()
            for (i in 0 until parts.length()) {
                sb.append(parts.getJSONObject(i).optString("text"))
            }
            return sb.toString().trim()
        } finally {
            conn.disconnect()
        }
    }

    fun testConnection(apiKey: String, model: String): String {
        return generate(
            apiKey, model,
            "Short reply only.",
            listOf("user" to "Sirf ek shabd mein jawab do: ready")
        )
    }

    private fun friendlyError(code: Int, raw: String): String = when (code) {
        400 -> "Request galat hai (400). Model name ya key check karo."
        401, 403 -> "API key galat hai ya is key ko permission nahi hai ($code)."
        404 -> "Model nahi mila (404). Settings mein dusra model chuno."
        429 -> "Limit khatam ho gayi (429). Thodi der baad try karo."
        in 500..599 -> "Gemini server mein problem hai ($code). Baad mein try karo."
        else -> "Error $code: ${raw.take(200)}"
    }
}
