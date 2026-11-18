package app.yomu

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/**
 * Panneau qui s'ouvre par-dessus l'appli en cours : récupère le texte (sélection,
 * partage ou presse-papiers), lance la traduction + le découpage, et permet de
 * poser des questions de suivi.
 */
class PanelActivity : Activity() {

    private lateinit var prefs: Prefs
    private lateinit var sourceField: EditText
    private lateinit var resultView: TextView
    private lateinit var resultScroll: ScrollView
    private lateinit var statusView: TextView
    private lateinit var questionField: EditText

    private val ui = Handler(Looper.getMainLooper())
    private var client: OpenRouter? = null
    private var waitingForClipboard = false

    /** Conversation en cours (système + échanges). */
    private val conversation = mutableListOf<ChatMessage>()

    /** Texte affiché (réponses + questions de suivi). */
    private val display = StringBuilder()

    // ---------------------------------------------------------------- cycle de vie

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        window.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN or
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        )
        setContentView(buildUi())
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // Android 10+ : le presse-papiers n'est lisible que quand la fenêtre a le focus.
        if (hasFocus && waitingForClipboard) {
            waitingForClipboard = false
            val clip = readClipboard()
            if (clip.isNullOrBlank()) {
                setStatus("Presse-papiers vide : copie une phrase puis touche « Coller ».")
            } else {
                startAnalysis(clip)
            }
        }
    }

    override fun onDestroy() {
        client?.cancel()
        ui.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    // ---------------------------------------------------------------- entrée du texte

    private fun handleIntent(intent: Intent?) {
        val text: CharSequence? = when (intent?.action) {
            Intent.ACTION_PROCESS_TEXT -> intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)
            Intent.ACTION_SEND, ACTION_TRANSLATE -> intent.getCharSequenceExtra(Intent.EXTRA_TEXT)
            ACTION_TEST -> intent.getStringExtra(EXTRA_TEST_TEXT)
            else -> null
        }
        val clean = text?.toString()?.trim()
        if (!clean.isNullOrEmpty()) {
            waitingForClipboard = false
            startAnalysis(clean)
        } else {
            // Ouvert depuis la poignée : on lira le presse-papiers dès que la fenêtre aura le focus.
            waitingForClipboard = true
            if (hasWindowFocus()) onWindowFocusChanged(true)
        }
    }

    private fun readClipboard(): String? {
        return try {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = cm.primaryClip ?: return null
            if (clip.itemCount == 0) return null
            clip.getItemAt(0).coerceToText(this)?.toString()?.trim()
        } catch (_: Exception) {
            null
        }
    }

    // ---------------------------------------------------------------- appels au modèle

    private fun startAnalysis(source: String) {
        sourceField.setText(source)
        questionField.setText("")

        // Même texte que la dernière fois : on réaffiche sans refaire payer l'appel.
        if (source == lastSource && lastDisplay.isNotEmpty() && lastConversation.isNotEmpty()) {
            conversation.clear()
            conversation.addAll(lastConversation)
            display.setLength(0)
            display.append(lastDisplay)
            resultView.text = display.toString()
            setStatus("Résultat déjà calculé (touche « Relancer » pour refaire l'analyse).")
            return
        }

        conversation.clear()
        conversation.add(ChatMessage("system", prefs.systemPrompt))
        conversation.add(ChatMessage("user", source))
        display.setLength(0)
        resultView.text = ""
        send(source, isFollowUp = false)
    }

    private fun askFollowUp() {
        val q = questionField.text.toString().trim()
        if (q.isEmpty()) return
        if (conversation.isEmpty()) {
            startAnalysis(sourceField.text.toString().trim())
            return
        }
        questionField.setText("")
        conversation.add(ChatMessage("user", q))
        display.append("\n\n────────\n› ").append(q).append("\n\n")
        resultView.text = display.toString()
        send(sourceField.text.toString().trim(), isFollowUp = true)
    }

    private fun send(source: String, isFollowUp: Boolean) {
        if (source.isEmpty()) {
            setStatus("Aucun texte à analyser.")
            return
        }
        val key = prefs.apiKey
        if (key.isBlank()) {
            setStatus("Ajoute ta clé OpenRouter dans les réglages de Yomu.")
            return
        }

        client?.cancel()
        val c = OpenRouter(key, prefs.model)
        client = c
        val messages = conversation.toList()
        val answer = StringBuilder()
        setStatus("Analyse en cours… (${prefs.model})")
        if (!isFollowUp) resultScroll.scrollTo(0, 0)

        // L'e-ink rafraîchit mal les mises à jour trop fréquentes : on regroupe.
        val refresher = object : Runnable {
            override fun run() {
                if (c.cancelled) return
                resultView.text = synchronized(display) { display.toString() }
                ui.postDelayed(this, 450)
            }
        }
        ui.postDelayed(refresher, 450)

        Thread {
            var error: String? = null
            try {
                c.stream(messages) { delta ->
                    synchronized(display) {
                        answer.append(delta)
                        display.append(delta)
                    }
                }
            } catch (e: Exception) {
                if (!c.cancelled) error = e.message ?: e.toString()
            }
            ui.post {
                ui.removeCallbacks(refresher)
                if (c.cancelled && client !== c) return@post
                if (error != null) {
                    synchronized(display) {
                        display.append(if (display.isEmpty()) "" else "\n\n").append("⚠ ").append(error)
                    }
                    resultView.text = display.toString()
                    setStatus("Échec de la requête.")
                    if (answer.isEmpty()) conversation.removeAt(conversation.lastIndex)
                } else {
                    conversation.add(ChatMessage("assistant", answer.toString()))
                    resultView.text = display.toString()
                    setStatus("Terminé · ${prefs.model}")
                    lastSource = source
                    lastConversation = conversation.toList()
                    lastDisplay = display.toString()
                }
            }
        }.start()
    }

    // ---------------------------------------------------------------- interface

    private fun dp(v: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()

    private fun border(width: Int = 2): GradientDrawable = GradientDrawable().apply {
        setColor(Color.WHITE)
        setStroke(dp(width), Color.BLACK)
        cornerRadius = dp(6).toFloat()
    }

    private fun button(label: String, onClick: () -> Unit): TextView = TextView(this).apply {
        text = label
        setTextColor(Color.BLACK)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        background = border(1)
        setPadding(dp(10), dp(6), dp(10), dp(6))
        isClickable = true
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { marginStart = dp(6) }
    }

    private fun buildUi(): View {
        val textSize = prefs.textSize.toFloat()
        val screenH = resources.displayMetrics.heightPixels

        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            // Toucher en dehors du panneau le ferme.
            setOnClickListener { finish() }
        }

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = border(2)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            isClickable = true // n'envoie pas le toucher au fond
        }

        // En-tête
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(TextView(this).apply {
            text = "読む Yomu"
            setTextColor(Color.BLACK)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        header.addView(button("Coller") {
            val clip = readClipboard()
            if (clip.isNullOrBlank()) setStatus("Presse-papiers vide.") else {
                lastSource = null
                startAnalysis(clip)
            }
        })
        header.addView(button("Relancer") {
            lastSource = null
            startAnalysis(sourceField.text.toString().trim())
        })
        header.addView(button("Copier") {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("Yomu", display.toString()))
            Toast.makeText(this, "Analyse copiée", Toast.LENGTH_SHORT).show()
        })
        header.addView(button("✕") { finish() })
        card.addView(header)

        // Texte source (modifiable pour corriger l'OCR)
        sourceField = EditText(this).apply {
            setTextColor(Color.BLACK)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, textSize + 2)
            background = border(1)
            setPadding(dp(8), dp(6), dp(8), dp(6))
            maxLines = 3
            hint = "Texte japonais…"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        }
        card.addView(sourceField, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(8) })

        statusView = TextView(this).apply {
            setTextColor(Color.DKGRAY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        }
        card.addView(statusView, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(4) })

        // Résultat (sélectionnable : on peut y chercher un mot avec Hoshi)
        resultView = TextView(this).apply {
            setTextColor(Color.BLACK)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, textSize)
            setLineSpacing(0f, 1.25f)
            setTextIsSelectable(true)
            setPadding(0, dp(6), 0, dp(6))
        }
        resultScroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = true
            addView(resultView)
        }
        card.addView(resultScroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ).apply { topMargin = dp(4) })

        // Question de suivi
        val askRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        questionField = EditText(this).apply {
            setTextColor(Color.BLACK)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            background = border(1)
            setPadding(dp(8), dp(6), dp(8), dp(6))
            hint = "Une question sur cette phrase ?"
            maxLines = 2
            imeOptions = EditorInfo.IME_ACTION_SEND
            inputType = InputType.TYPE_CLASS_TEXT
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_SEND) {
                    askFollowUp(); true
                } else false
            }
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        askRow.addView(questionField)
        askRow.addView(button("Envoyer") { askFollowUp() })
        card.addView(askRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(8) })

        root.addView(card, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, (screenH * 0.72).toInt(), Gravity.BOTTOM
        ).apply { setMargins(dp(8), 0, dp(8), dp(8)) })

        return root
    }

    private fun setStatus(s: String) {
        statusView.text = s
    }

    companion object {
        const val ACTION_FROM_HANDLE = "app.yomu.FROM_HANDLE"
        const val ACTION_TEST = "app.yomu.TEST"
        const val ACTION_TRANSLATE = "android.intent.action.TRANSLATE"
        const val EXTRA_TEST_TEXT = "app.yomu.TEST_TEXT"

        // Cache du dernier résultat (évite de repayer si on rouvre sur la même phrase).
        private var lastSource: String? = null
        private var lastConversation: List<ChatMessage> = emptyList()
        private var lastDisplay: String = ""
    }
}
