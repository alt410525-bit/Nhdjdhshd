package com.jarvis.ai

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.Settings

// Phase A: app kholna, YouTube search, alarm, timer.
// Har action ke baad Android se confirm hone par hi "ho gaya" bolna.
object Automation {

    data class Result(val handled: Boolean, val reply: String)

    private val appAliases = mapOf(
        "youtube" to "com.google.android.youtube",
        "chrome" to "com.android.chrome",
        "instagram" to "com.instagram.android",
        "whatsapp" to "com.whatsapp",
        "maps" to "com.google.android.apps.maps",
        "camera" to "com.sec.android.app.camera",
        "gallery" to "com.sec.android.gallery3d",
        "settings" to "com.android.settings",
        "spotify" to "com.spotify.music"
    )

    // Command ko samajhkar action lo. handled=false ho to Gemini ko bhejo.
    fun handle(ctx: Context, raw: String): Result {
        val text = raw.trim().lowercase()

        // Alarm: "7 baje alarm laga" / "alarm set 7 30"
        Regex("alarm.*?(\\d{1,2})(?::|\\s)?(\\d{2})?\\s*(baje|am|pm)?").find(text)?.let { m ->
            if (text.contains("alarm")) {
                var h = m.groupValues[1].toInt()
                val min = m.groupValues[2].ifEmpty { "0" }.toInt()
                val ampm = m.groupValues[3]
                if (ampm == "pm" && h < 12) h += 12
                if (ampm == "am" && h == 12) h = 0
                return setAlarm(ctx, h, min)
            }
        }

        // Timer: "5 minute ka timer" / "timer 10 second"
        Regex("timer.*?(\\d+)\\s*(minute|min|second|sec|ghanta|hour)").find(text)?.let { m ->
            val n = m.groupValues[1].toInt()
            val seconds = when (m.groupValues[2]) {
                "second", "sec" -> n
                "hour", "ghanta" -> n * 3600
                else -> n * 60
            }
            return setTimer(ctx, seconds)
        }

        // YouTube search: "youtube par kisi ka gaana search karo"
        Regex("youtube (par|pe|mein|me) (.+?)( search karo| search kar| dhundo|$)").find(text)?.let { m ->
            return searchYoutube(ctx, m.groupValues[2].trim())
        }

        // App kholna: "youtube kholo", "instagram open karo"
        Regex("(.+?) (kholo|khol do|open karo|open)$").find(text)?.let { m ->
            val name = m.groupValues[1].trim()
            val pkg = appAliases.entries.firstOrNull { name.contains(it.key) }?.value
            if (pkg != null) return openApp(ctx, pkg, name)
        }

        return Result(false, "")
    }

    private fun openApp(ctx: Context, pkg: String, label: String): Result {
        val intent = ctx.packageManager.getLaunchIntentForPackage(pkg)
        if (intent == null) {
            return Result(true, "$label app is phone par installed nahi lag raha.")
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(intent)
        return Result(true, "$label khol diya.")
    }

    private fun searchYoutube(ctx: Context, query: String): Result {
        val url = "https://www.youtube.com/results?search_query=" + Uri.encode(query)
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            ctx.startActivity(intent)
            Result(true, "YouTube par \"$query\" search khol diya. Jo video chahiye wo tap karo.")
        } catch (e: Exception) {
            Result(true, "YouTube khul nahi paaya.")
        }
    }

    private fun setAlarm(ctx: Context, hour: Int, minute: Int): Result {
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, minute)
            putExtra(AlarmClock.EXTRA_MESSAGE, "Jarvis alarm")
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return if (intent.resolveActivity(ctx.packageManager) != null) {
            ctx.startActivity(intent)
            Result(true, "Alarm ka screen khol diya, %02d:%02d ke liye. Save karna aapka kaam hai.".format(hour, minute))
        } else {
            Result(true, "Is phone par clock app nahi mila.")
        }
    }

    private fun setTimer(ctx: Context, seconds: Int): Result {
        val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(AlarmClock.EXTRA_LENGTH, seconds)
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return if (intent.resolveActivity(ctx.packageManager) != null) {
            ctx.startActivity(intent)
            Result(true, "Timer ka screen khol diya, $seconds second ke liye.")
        } else {
            Result(true, "Is phone par timer app nahi mila.")
        }
    }
}
