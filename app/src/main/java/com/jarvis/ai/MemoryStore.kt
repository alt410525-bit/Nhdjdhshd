package com.jarvis.ai

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class MemoryItem(val id: Long, val text: String, val createdAt: Long)

object MemoryStore {
    private const val FACTS_FILE = "memories.json"
    private const val CHAT_FILE = "chat_log.json"
    private const val MAX_CHAT = 300

    private val sensitiveRegex = Regex(
        "\\b(password|passcode|pin|otp|cvv|card|credit|debit|bank|account number|aadhaar|aadhar|pan card|passport|upi|salary|income|loan|medical|disease|bimari|dawai|medicine|diagnosis|depression|anxiety)\\b",
        RegexOption.IGNORE_CASE
    )

    fun isSensitive(text: String): Boolean = sensitiveRegex.containsMatchIn(text)

    // ---------- Facts (yaad rakho wali baatein) ----------

    @Synchronized
    fun loadFacts(ctx: Context): List<MemoryItem> {
        val f = File(ctx.filesDir, FACTS_FILE)
        if (!f.exists()) return emptyList()
        return try {
            val arr = JSONArray(f.readText())
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                MemoryItem(o.getLong("id"), o.getString("text"), o.getLong("createdAt"))
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    @Synchronized
    private fun saveFacts(ctx: Context, items: List<MemoryItem>) {
        val arr = JSONArray()
        for (m in items) {
            arr.put(JSONObject().put("id", m.id).put("text", m.text).put("createdAt", m.createdAt))
        }
        File(ctx.filesDir, FACTS_FILE).writeText(arr.toString())
    }

    @Synchronized
    fun addFact(ctx: Context, text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        val now = System.currentTimeMillis()
        val list = loadFacts(ctx).toMutableList()
        list.add(MemoryItem(now, clean, now))
        saveFacts(ctx, list)
    }

    @Synchronized
    fun updateFact(ctx: Context, id: Long, newText: String) {
        val clean = newText.trim()
        if (clean.isEmpty()) return
        val list = loadFacts(ctx).map { if (it.id == id) it.copy(text = clean) else it }
        saveFacts(ctx, list)
    }

    @Synchronized
    fun deleteFact(ctx: Context, id: Long) {
        val list = loadFacts(ctx).filter { it.id != id }
        saveFacts(ctx, list)
    }

    @Synchronized
    fun clearFacts(ctx: Context) {
        File(ctx.filesDir, FACTS_FILE).delete()
    }

    // ---------- Chat log (sab baatein) ----------

    // role is "user" or "model". Returns oldest first.
    @Synchronized
    fun loadChat(ctx: Context): List<Pair<String, String>> {
        val f = File(ctx.filesDir, CHAT_FILE)
        if (!f.exists()) return emptyList()
        return try {
            val arr = JSONArray(f.readText())
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                o.getString("role") to o.getString("text")
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    @Synchronized
    fun appendChat(ctx: Context, role: String, text: String) {
        val list = loadChat(ctx).toMutableList()
        list.add(role to text)
        val arr = JSONArray()
        for ((r, t) in list.takeLast(MAX_CHAT)) {
            arr.put(JSONObject().put("role", r).put("text", t))
        }
        File(ctx.filesDir, CHAT_FILE).writeText(arr.toString())
    }

    @Synchronized
    fun clearChat(ctx: Context) {
        File(ctx.filesDir, CHAT_FILE).delete()
    }
}
