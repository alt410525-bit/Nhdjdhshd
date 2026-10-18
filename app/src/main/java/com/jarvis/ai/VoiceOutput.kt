package com.jarvis.ai

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

// Text-to-speech. Hindi voice is used only if the phone has it installed.
class VoiceOutput(context: Context, private val onReady: (Boolean) -> Unit) {

    private var ready = false
    private var hindiOk = false
    private val tts: TextToSpeech = TextToSpeech(context) { status ->
        ready = status == TextToSpeech.SUCCESS
        if (ready) {
            val result = tts.setLanguage(Locale("hi", "IN"))
            hindiOk = result != TextToSpeech.LANG_MISSING_DATA &&
                    result != TextToSpeech.LANG_NOT_SUPPORTED
            if (!hindiOk) tts.setLanguage(Locale.US)
        }
        onReady(hindiOk)
    }

    fun speak(text: String, onDone: () -> Unit = {}) {
        if (!ready) { onDone(); return }
        stop()
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) { onDone() }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) { onDone() }
        })
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jarvis_utt")
    }

    fun stop() {
        tts.stop()
    }

    fun shutdown() {
        tts.stop()
        tts.shutdown()
    }
}
