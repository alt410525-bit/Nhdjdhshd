package com.jarvis.ai

import android.Manifest
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
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

    private val accent = "#00E5FF"
    private val panel = "#0A1A2A"
    private val bg = "#05080F"

    private lateinit var chatContainer: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var input: EditText
    private lateinit var sendBtn: Button
    private lateinit var micBtn: Button
    private lateinit var modeBtn: Button
    private lateinit var speakToggle: CheckBox
    private lateinit var statusText: TextView

    private lateinit var voiceInput: VoiceInput
    private lateinit var voiceOutput: VoiceOutput
    private var busy = false
    private var codingMode = false

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
                "Namaste! Main ${Prefs.getPersona(this)} hoon, aapki AI assistant. Main ek AI hoon, insaan nahi. Jo bhi aap batayenge, main phone par yaad rakhungi (sensitive baat ke liye pehle poochungi).\n\nMic dabakar bolo, ya type karo. Coding ke liye Mode ko Code par rakho."
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

    private fun styledButton(label: String, onClick: () -> Unit): Button =
        Button(this).apply {
            text = label
            setTextColor(Color.parseColor(accent))
            setBackgroundColor(Color.parseColor(panel))
            setOnClickListener { onClick() }
        }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor(bg))
        }

        // Top bar
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor(panel))
            setPadding(32, 40, 16, 24)
        }
        val title = TextView(this).apply {
            text = Prefs.getPersona(this@MainActivity).uppercase()
            textSize = 20f
            setTextColor(Color.parseColor(accent))
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        top.addView(title)
        top.addView(styledButton("Memory") { showMemory() })
        top.addView(styledButton("Settings") { showSettings() })
        root.addView(top)

        statusText = TextView(this).apply {
            textSize = 12f
            setPadding(32, 8, 32, 8)
            setTextColor(Color.parseColor(accent))
        }
        root.addView(statusText)

        // Input row ab upar hai
        val inputRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(16, 8, 16, 8)
        }
        micBtn = styledButton("Mic") { onMicTapped() }
        input = EditText(this).apply {
            hint = "${Prefs.getPersona(this@MainActivity)} se baat karo..."
            setHintTextColor(Color.GRAY)
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        sendBtn = styledButton("Send") { onSend() }
        inputRow.addView(micBtn)
        inputRow.addView(input)
        inputRow.addView(sendBtn)
        root.addView(inputRow)

        // Mode aur speak toggle
        val optionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(16, 0, 16, 8)
        }
        modeBtn = styledButton("Mode: Chat") {
            codingMode = !codingMode
            modeBtn.text = if (codingMode) "Mode: Code" else "Mode: Chat"
            statusText.text = if (codingMode) "Coding mode on" else "Chat mode on"
        }
        speakToggle = CheckBox(this).apply {
            text = "Jawab bolkar sunao"
            isChecked = true
            setTextColor(Color.parseColor(accent))
        }
        optionRow.addView(modeBtn)
        optionRow.addView(speakToggle)
        root.addView(optionRow)

        // Chat area neeche
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

        setContentView(root)
    }

    private fun textView(text: String, color: String, mono: Boolean = false): TextView =
        TextView(this).apply {
            this.text = text
            textSize = 16f
            setTextColor(Color.parseColor(color))
            if (mono) typeface = Typeface.MONOSPACE
        }

    private fun codeView(seg: CodeBlocks.Segment): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#000000"))
            setPadding(20, 12, 20, 12)
            addView(TextView(this@MainActivity).apply {
                text = if (seg.lang.isEmpty()) "code" else seg.lang
                textSize = 11f
                setTextColor(Color.parseColor(accent))
            })
            addView(TextView(this@MainActivity).apply {
                text = seg.text
                textSize = 13f
                typeface = Typeface.MONOSPACE
                setTextColor(Color.parseColor("#B2EBF2"))
            })
            addView(Button(this@MainActivity).apply {
                text = "Copy"
                setTextColor(Color.parseColor(accent))
                setOnClickListener { CodeBlocks.copy(this@MainActivity, seg.text) }
            })
        }

    private fun addBubble(role: String, text: String) {
        val isUser = role == "user"
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor(if (isUser) "#0E3A4F" else "#0D1B2A"))
            setPadding(28, 18, 28, 18)
            layoutParams = LinearLayout.LayoutParams(
                if (isUser) LinearLayout.LayoutParams.WRAP_CONTENT else LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 8, 0, 8)
                gravity = if (isUser) Gravity.END else Gravity.START
            }
        }
        if (isUser) {
            box.addView(textView(text, "#E0F7FA"))
        } else {
            for (seg in CodeBlocks.split(text)) {
                if (seg.isCode) box.addView(codeView(seg))
                else box.addView(textView(seg.text, "#D6F4FF"))
            }
        }
        chatContainer.addView(box)
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
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
                    voiceOutput.speak(CodeBlocks.spokenText(reply)) {
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

        val base = """
            Tum $name ho, ek warm, intelligent aur helpful AI assistant.
            Tum hamesha yeh saaf rakhte ho ki tum ek AI ho, insaan nahi.
            User jo bhasha mein baat kare (Hindi, Hinglish ya English), usi mein jawab do.
            Chhote, natural, saaf jawab do. Zarurat ho to hi lamba likho.
            Medical, legal ya financial sawaalon par general jaankari do aur professional se milne ki salah do.
        """.trimIndent()

        val coding = if (!codingMode) "" else """

            CODING MODE ON:
            - Code hamesha markdown code block mein do, language ke naam ke saath (jaise ```kotlin).
            - Agar multiple files hain to har file se pehle uska poora path likho.
            - Beginner ki bhasha mein short explanation do.
            - Tum code compile ya run nahi kar sakti, isliye agar user ne error diya ho to uska sabab aur fix batao, aur ye bhi bolo ki fix test karna user ka kaam hai.
            - Kabhi bhi ye mat kaho ki code test ho chuka hai.
        """.trimIndent()

        return base + coding + memoryBlock
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
                addBubble("model", "Sab kuch bhool gaya. Naye sire se shuru karte hain.")
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
