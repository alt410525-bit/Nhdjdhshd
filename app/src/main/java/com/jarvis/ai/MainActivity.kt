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

    private val rememberRegex = Regex("^(yaad rakho|remember)[:,\\s]*(.+)$", RegexOption.IGNORE_CASE)

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

        val saved = MemoryStore.loadChat(this)
        if (saved.isEmpty()) {
            addBubble(
                "model",
                "Namaste! Main ${Prefs.getPersona(this)} hoon, aapki AI assistant. Main ek AI hoon, insaan nahi. Jo bhi aap batayenge, main phone par yaad rakhungi (sensitive baat ke liye pehle poochungi).\n\nMic button dabakar bolo, ya type karo."
            )
        } else {
            for ((role, text) in saved.takeLast(50)) addBubble(role, text)
        }
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
        val memoryBtn = Button(this).apply {
            text = "Memory"
            setOnClickListener { showMemory() }
        }
        val settingsBtn = Button(this).apply {
            text = "Settings"
            setOnClickListener { showSettings() }
        }
        top.addView(title)
        top.addView(memoryBtn)
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

        if (Prefs.getApiKey(this).isEmpty()) {
            addBubble("model", "Pehle Settings mein Gemini API key daal do.")
            return
        }

        input.setText("")

        if (MemoryStore.isSensitive(text)) {
            AlertDialog.Builder(this)
                .setTitle("Sensitive lag raha hai")
                .setMessage("Ye baat memory mein save karun? \"Mat karo\" dabaoge to ye sirf is baar use hogi, aur kahin save nahi hogi.")
                .setPositiveButton("Save karo") { _, _ -> process(text, persist = true) }
                .setNegativeButton("Mat karo") { _, _ -> process(text, persist = false) }
                .setOnCancelListener { process(text, persist = false) }
                .show()
        } else {
            process(text, persist = true)
        }
    }

    private fun process(text: String, persist: Boolean) {
        // "yaad rakho: ..." wali baat alag fact ke roop mein save karo
        rememberRegex.find(text)?.groupValues?.get(2)?.let { fact ->
            if (persist) {
                MemoryStore.addFact(this, fact)
                statusText.text = "Yaad rakh liya"
            }
        }

        addBubble("user", text)
        busy = true
        sendBtn.isEnabled = false
        statusText.text = "${Prefs.getPersona(this)} soch raha hai..."

        if (persist) MemoryStore.appendChat(this, "user", text)

        val history: MutableList<Pair<String, String>> =
            MemoryStore.loadChat(this).takeLast(30).toMutableList()
        if (!persist) history.add("user" to text)

        val apiKey = Prefs.getApiKey(this)
        val model = Prefs.getModel(this)
        val prompt = systemPrompt()

        lifecycleScope.launch {
            try {
                val reply = withContext(Dispatchers.IO) {
                    GeminiClient.generate(apiKey, model, prompt, history)
                }
                addBubble("model", reply)
                if (persist) MemoryStore.appendChat(this@MainActivity, "model", reply)
                statusText.text = "Connected: $model"
                if (speakToggle.isChecked) {
                    voiceOutput.speak(reply) {
                        runOnUiThread { if (!busy) statusText.text = "Connected: $model" }
                    }
                }
            } catch (e: Exception) {
                addBubble("model", "Error: ${e.message}")
                statusText.text = "Error aaya"
            } finally {
                busy = false
                sendBtn.isEnabled = true
            }
        }
    }

    private fun systemPrompt(): String {
        val name = Prefs.getPersona(this)
        val facts = MemoryStore.loadFacts(this)
        val memoryBlock = if (facts.isEmpty()) "" else
            "\n\nUser ke baare mein jo yaad hai (is info ko naturally use karo, par bar-bar mat dohrao):\n" +
                    facts.joinToString("\n") { "- ${it.text}" }

        return """
            Tum $name ho, ek warm, intelligent aur helpful AI assistant.
            Tum hamesha yeh saaf rakhte ho ki tum ek AI ho, insaan nahi.
            User jo bhasha mein baat kare (Hindi, Hinglish ya English), usi mein jawab do.
            Chhote, natural, saaf jawab do. Zarurat ho to hi lamba likho.
            Medical, legal ya financial sawaalon par general jaankari do aur professional se milne ki salah do.
        """.trimIndent() + memoryBlock
    }

    // ---------- Memory viewer ----------

    private fun showMemory() {
        val facts = MemoryStore.loadFacts(this)
        val labels: Array<String> = if (facts.isEmpty()) {
            arrayOf("Abhi koi memory nahi hai")
        } else {
            facts.map { it.text }.toTypedArray()
        }

        AlertDialog.Builder(this)
            .setTitle("Memory (${facts.size})")
            .setItems(labels) { _, which ->
                if (facts.isNotEmpty()) factOptions(facts[which])
            }
            .setPositiveButton("Naya add karo") { _, _ -> addFactDialog() }
            .setNeutralButton("Sab delete") { _, _ -> confirmClearAll() }
            .setNegativeButton("Band karo", null)
            .show()
    }

    private fun factOptions(item: MemoryItem) {
        AlertDialog.Builder(this)
            .setTitle("Memory")
            .setMessage(item.text)
            .setPositiveButton("Edit") { _, _ -> editFactDialog(item) }
            .setNegativeButton("Delete") { _, _ ->
                MemoryStore.deleteFact(this, item.id)
                statusText.text = "Memory delete ho gayi"
            }
            .setNeutralButton("Cancel", null)
            .show()
    }

    private fun editFactDialog(item: MemoryItem) {
        val et = EditText(this).apply {
            setText(item.text)
            setPadding(48, 24, 48, 24)
        }
        AlertDialog.Builder(this)
            .setTitle("Memory edit karo")
            .setView(et)
            .setPositiveButton("Save") { _, _ ->
                MemoryStore.updateFact(this, item.id, et.text.toString())
                statusText.text = "Memory update ho gayi"
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun addFactDialog() {
        val et = EditText(this).apply {
            hint = "Jaise: Mujhe chai pasand hai"
            setPadding(48, 24, 48, 24)
        }
        AlertDialog.Builder(this)
            .setTitle("Naya memory")
            .setView(et)
            .setPositiveButton("Save") { _, _ ->
                MemoryStore.addFact(this, et.text.toString())
                statusText.text = "Memory save ho gayi"
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmClearAll() {
        AlertDialog.Builder(this)
            .setTitle("Sab delete karein?")
            .setMessage("Saari memory aur chat history delete ho jayegi. Wapas nahi aayegi.")
            .setPositiveButton("Haan, delete") { _, _ ->
                MemoryStore.clearFacts(this)
                MemoryStore.clearChat(this)
                chatContainer.removeAllViews()
                addBubble("model", "Sab kuch bhool gayi. Naye sire se shuru karte hain.")
                statusText.text = "Memory clear ho gayi"
            }
            .setNegativeButton("Nahi", null)
            .show()
    }

    // ---------- Settings ----------

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
