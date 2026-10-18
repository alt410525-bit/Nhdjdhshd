package com.jarvis.ai

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast

object CodeBlocks {

    data class Segment(val isCode: Boolean, val text: String, val lang: String)

    private val fenceRegex = Regex("```([a-zA-Z0-9+#._-]*)\\n([\\s\\S]*?)```")

    // Reply ko text aur code blocks mein todta hai
    fun split(reply: String): List<Segment> {
        val out = mutableListOf<Segment>()
        var last = 0
        for (m in fenceRegex.findAll(reply)) {
            if (m.range.first > last) {
                out.add(Segment(false, reply.substring(last, m.range.first).trim(), ""))
            }
            out.add(Segment(true, m.groupValues[2].trimEnd(), m.groupValues[1]))
            last = m.range.last + 1
        }
        if (last < reply.length) {
            out.add(Segment(false, reply.substring(last).trim(), ""))
        }
        return out.filter { it.text.isNotEmpty() }
    }

    // Bolkar sunate waqt code ki jagah ek chhota message
    fun spokenText(reply: String): String =
        fenceRegex.replace(reply, " code screen par dekh lo. ")

    fun copy(ctx: Context, code: String) {
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("code", code))
        Toast.makeText(ctx, "Code copy ho gaya", Toast.LENGTH_SHORT).show()
    }
}
