package com.jarvis.ai

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

// Text-to-speech. Hindi voice is used only if the phone has it installed.
class VoiceOutput(context: Context, private val onReady: (Boolean) -> Unit) {

    private var ready = false
    private var hindiOk = false
    private var tts: TextToSpeech? = null

    init {
        tts = TextToSpeech(context) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (ready) {
                val engine = tts
                val result = engine?.setLanguage(Locale("hi", "IN"))
                hindiOk = result != null &&
                        result != TextToSpeech.LANG_MISSING_DATA &&
                        result != TextToSpeech.LANG_NOT_SUPPORTED
                if (!hindiOk) engine?.setLanguage(Locale.US)
            }
            onReady(hindiOk)
        }
    }

    fun speak(text: String, onDone: () -> Unit = {}) {
        val engine = tts
        if (!ready || engine == null) { onDone(); return }
        stop()
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) { onDone() }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) { onDone() }
        })
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jarvis_utt")
    }

    fun stop() {
        tts?.stop()
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}
