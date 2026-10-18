package com.jarvis.ai

import android.Manifest
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private val messages = mutableListOf<ChatMessage>()
    private lateinit var chatContainer: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var input: EditText
    private lateinit var sendBtn: Button
    private lateinit var micBtn: Button
    private lateinit var speakToggle: CheckBox
    private lateinit var statusText: TextView

    private lateinit var voiceInput: VoiceInput
    private lateinit var voiceOutput: VoiceOutput
    private var busy = false

    private val micPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startListening()
        else statusText.text = "Mic permission nahi mili. Settings se allow karo."
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        voiceInput = VoiceInput(this)
        voiceOutput = VoiceOutput(this) { hi ->
            if (!hi) statusText.text = "Hindi voice nahi mili, English mein bolunga. Google TTS mein Hindi voice download karo."
        }
        addBubble(
            "assistant",
            "Namaste! Main ${Prefs.getPersona(this)} hoon, aapki AI assistant. Main ek AI hoon, insaan nahi. Hindi, Hinglish ya English mein baat kar sakte ho.\n\nMic button dabakar bolo, ya type karo."
        )
    }

    override fun onDestroy() {
        voiceInput.release()
        voiceOutput.shutdown()
        super.onDestroy()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#F5F7FA"))
        }

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#1565C0"))
            setPadding(32, 40, 16, 24)
        }
        val title = TextView(this).apply {
            text = Prefs.getPersona(this@MainActivity)
            textSize = 20f
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val settingsBtn = Button(this).apply {
            text = "Settings"
            setOnClickListener { showSettings() }
        }
        top.addView(title)
        top.addView(settingsBtn)
        root.addView(top)

        statusText = TextView(this).apply {
            textSize = 12f
            setPadding(32, 8, 32, 8)
            setTextColor(Color.DKGRAY)
        }
        root.addView(statusText)

        scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
            isFillViewport = true
        }
        chatContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 16, 24, 16)
        }
        scroll.addView(chatContainer)
        root.addView(scroll)

        speakToggle = CheckBox(this).apply {
            text = "Jawab bolkar sunao"
            isChecked = true
            setPadding(32, 0, 32, 0)
        }
        root.addView(speakToggle)

        val inputRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(16, 8, 16, 16)
        }
        micBtn = Button(this).apply {
            text = "Mic"
            setOnClickListener { onMicTapped() }
        }
        input = EditText(this).apply {
            hint = "${Prefs.getPersona(this@MainActivity)} se baat karo..."
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        sendBtn = Button(this).apply {
            text = "Send"
            setOnClickListener { onSend() }
        }
        inputRow.addView(micBtn)
        inputRow.addView(input)
        inputRow.addView(sendBtn)
        root.addView(inputRow)

        setContentView(root)
    }

    private fun onMicTapped() {
        if (busy) return
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        if (granted) startListening() else micPermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    private fun startListening() {
        if (!voiceInput.isAvailable()) {
            statusText.text = "Is phone par speech recognition nahi hai."
            return
        }
        voiceOutput.stop()
        micBtn.isEnabled = false
        statusText.text = "Sun raha hoon... bolo"

        voiceInput.start(
            onPartial = { partial -> input.setText(partial) },
            onResult = { text ->
                micBtn.isEnabled = true
                input.setText(text)
                onSend()
            },
            onError = { msg ->
                micBtn.isEnabled = true
                statusText.text = msg
            }
        )
    }

    private fun addBubble(role: String, text: String) {
        messages.add(ChatMessage(role, text))
        val tv = TextView(this).apply {
            this.text = text
            textSize = 16f
            setPadding(32, 20, 32, 20)
            setTextColor(if (role == "user") Color.WHITE else Color.BLACK)
            setBackgroundColor(
                if (role == "user") Color.parseColor("#1565C0") else Color.parseColor("#E3EAF5")
            )
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 8, 0, 8)
                gravity = if (role == "user") Gravity.END else Gravity.START
            }
        }
        chatContainer.addView(tv)
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun onSend() {
        if (busy) return
        val text = input.text.toString().trim()
        if (text.isEmpty()) return

        val apiKey = Prefs.getApiKey(this)
        if (apiKey.isEmpty()) {
            addBubble("assistant", "Pehle Settings mein Gemini API key daal do.")
            return
        }

        input.setText("")
        addBubble("user", text)
        busy = true
        sendBtn.isEnabled = false
        statusText.text = "${Prefs.getPersona(this)} soch raha hai..."

        val model = Prefs.getModel(this)
        val history = messages.takeLast(20).map {
            (if (it.role == "user") "user" else "model") to it.text
        }

        lifecycleScope.launch {
            try {
                val reply = withContext(Dispatchers.IO) {
                    GeminiClient.generate(apiKey, model, systemPrompt(), history)
                }
                addBubble("assistant", reply)
                statusText.text = "Connected: $model"
                if (speakToggle.isChecked) {
                    voiceOutput.speak(reply) {
                        runOnUiThread { if (!busy) statusText.text = "Connected: $model" }
                    }
                }
            } catch (e: Exception) {
                addBubble("assistant", "Error: ${e.message}")
                statusText.text = "Error aaya"
            } finally {
                busy = false
                sendBtn.isEnabled = true
            }
        }
    }

    private fun systemPrompt(): String {
        val name = Prefs.getPersona(this)
        return """
            Tum $name ho, ek warm, intelligent aur helpful AI assistant.
            Tum hamesha yeh saaf rakhte ho ki tum ek AI ho, insaan nahi.
            User jo bhasha mein baat kare (Hindi, Hinglish ya English), usi mein jawab do.
            Chhote, natural, saaf jawab do. Zarurat ho to hi lamba likho.
            Medical, legal ya financial sawaalon par general jaankari do aur professional se milne ki salah do.
        """.trimIndent()
    }

    private fun showSettings() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 0)
        }

        val keyInput = EditText(this).apply {
            hint = "Gemini API key"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setText(Prefs.getApiKey(this@MainActivity))
        }
        val modelInput = EditText(this).apply {
            hint = "Model name"
            setText(Prefs.getModel(this@MainActivity))
        }
        val nameInput = EditText(this).apply {
            hint = "Assistant ka naam"
            setText(Prefs.getPersona(this@MainActivity))
        }

        layout.addView(keyInput)
        layout.addView(modelInput)
        layout.addView(nameInput)

        AlertDialog.Builder(this)
            .setTitle("Settings")
            .setView(layout)
            .setPositiveButton("Save") { _, _ ->
                val key = keyInput.text.toString().trim()
                if (key.isEmpty()) Prefs.removeApiKey(this) else Prefs.saveApiKey(this, key)
                Prefs.saveModel(this, modelInput.text.toString().ifEmpty { Prefs.DEFAULT_MODEL })
                Prefs.savePersona(this, nameInput.text.toString())
                statusText.text = "Settings save ho gayi (naam badalne ke liye app restart karo)"
            }
            .setNeutralButton("Test") { _, _ ->
                testConnection(keyInput.text.toString().trim(), modelInput.text.toString().trim())
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun testConnection(key: String, model: String) {
        if (key.isEmpty()) {
            statusText.text = "Pehle API key daalo"
            return
        }
        statusText.text = "Connection test ho raha hai..."
        lifecycleScope.launch {
            try {
                val reply = withContext(Dispatchers.IO) {
                    GeminiClient.testConnection(key, model.ifEmpty { Prefs.DEFAULT_MODEL })
                }
                statusText.text = "Connection OK ✓ ($reply)"
            } catch (e: Exception) {
                statusText.text = "Test fail: ${e.message}"
            }
        }
    }
}
