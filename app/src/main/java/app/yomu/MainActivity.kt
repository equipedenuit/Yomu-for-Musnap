package app.yomu

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/** Écran de réglages : permissions, poignée, clé OpenRouter, modèle et prompt. */
class MainActivity : Activity() {

    private lateinit var prefs: Prefs
    private lateinit var overlayStatus: TextView
    private lateinit var handleButton: TextView
    private lateinit var leftButton: TextView
    private lateinit var rightButton: TextView
    private lateinit var keyField: EditText
    private lateinit var modelField: EditText
    private lateinit var sizeField: EditText
    private lateinit var promptField: EditText
    private lateinit var bootBox: CheckBox
    private lateinit var translateBox: CheckBox

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        setContentView(buildUi())
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }

    override fun onResume() {
        super.onResume()
        refreshState()
    }

    override fun onPause() {
        save(silent = true)
        super.onPause()
    }

    // ---------------------------------------------------------------- actions

    private fun save(silent: Boolean = false) {
        prefs.apiKey = keyField.text.toString()
        prefs.model = modelField.text.toString()
        prefs.systemPrompt = promptField.text.toString()
        sizeField.text.toString().toIntOrNull()?.let { prefs.textSize = it }
        prefs.startOnBoot = bootBox.isChecked
        if (!silent) Toast.makeText(this, "Réglages enregistrés", Toast.LENGTH_SHORT).show()
    }

    private fun toggleHandle() {
        if (!Settings.canDrawOverlays(this)) {
            openOverlaySettings()
            return
        }
        if (prefs.handleEnabled) {
            prefs.handleEnabled = false
            EdgeService.stop(this)
        } else {
            prefs.handleEnabled = true
            EdgeService.start(this)
        }
        refreshState()
    }

    private fun setSide(right: Boolean) {
        prefs.handleOnRight = right
        if (prefs.handleEnabled && Settings.canDrawOverlays(this)) EdgeService.start(this)
        refreshState()
    }

    private fun setTranslateAlias(enabled: Boolean) {
        packageManager.setComponentEnabledSetting(
            ComponentName(this, "app.yomu.TranslateAlias"),
            if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP
        )
    }

    private fun isTranslateAliasEnabled(): Boolean =
        packageManager.getComponentEnabledSetting(ComponentName(this, "app.yomu.TranslateAlias")) ==
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED

    private fun openOverlaySettings() {
        try {
            startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            )
        } catch (_: Exception) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
        }
    }

    private fun test() {
        save(silent = true)
        startActivity(
            Intent(this, PanelActivity::class.java)
                .setAction(PanelActivity.ACTION_TEST)
                .putExtra(PanelActivity.EXTRA_TEST_TEXT, "おんなとこどもはやらない")
        )
    }

    private fun refreshState() {
        val overlayOk = Settings.canDrawOverlays(this)
        overlayStatus.text = if (overlayOk) "✓ Affichage par-dessus les autres applis autorisé"
        else "✗ Affichage par-dessus les autres applis : à autoriser"
        handleButton.text = when {
            !overlayOk -> "Autoriser l'affichage par-dessus les applis"
            prefs.handleEnabled -> "Masquer la poignée"
            else -> "Afficher la poignée"
        }
        styleToggle(leftButton, !prefs.handleOnRight)
        styleToggle(rightButton, prefs.handleOnRight)
        translateBox.isChecked = isTranslateAliasEnabled()
    }

    // ---------------------------------------------------------------- interface

    private fun dp(v: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()

    private fun box(width: Int = 1, filled: Boolean = false): GradientDrawable = GradientDrawable().apply {
        setColor(if (filled) Color.BLACK else Color.WHITE)
        setStroke(dp(width), Color.BLACK)
        cornerRadius = dp(6).toFloat()
    }

    private fun styleToggle(v: TextView, selected: Boolean) {
        v.background = box(1, filled = selected)
        v.setTextColor(if (selected) Color.WHITE else Color.BLACK)
    }

    private fun button(label: String, onClick: () -> Unit): TextView = TextView(this).apply {
        text = label
        setTextColor(Color.BLACK)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        background = box(2)
        setPadding(dp(12), dp(10), dp(12), dp(10))
        isClickable = true
        setOnClickListener { onClick() }
    }

    private fun title(s: String): TextView = TextView(this).apply {
        text = s
        setTextColor(Color.BLACK)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
        typeface = Typeface.DEFAULT_BOLD
        setPadding(0, dp(18), 0, dp(6))
    }

    private fun note(s: String): TextView = TextView(this).apply {
        text = s
        setTextColor(Color.DKGRAY)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        setPadding(0, dp(2), 0, dp(6))
    }

    private fun field(value: String, hint: String, type: Int): EditText = EditText(this).apply {
        setText(value)
        this.hint = hint
        inputType = type
        setTextColor(Color.BLACK)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        background = box(1)
        setPadding(dp(10), dp(8), dp(10), dp(8))
    }

    private fun LinearLayout.addFull(v: View, top: Int = 6) {
        addView(v, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(top) })
    }

    private fun buildUi(): View {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(40))
        }

        col.addView(TextView(this).apply {
            text = "読む Yomu"
            setTextColor(Color.BLACK)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
            typeface = Typeface.DEFAULT_BOLD
        })
        col.addView(note(
            "Copie une phrase japonaise dans n'importe quelle appli, puis touche la poignée sur le bord " +
                "de l'écran : Yomu traduit et décompose la phrase via OpenRouter."
        ))

        // Poignée
        col.addView(title("1. Poignée"))
        overlayStatus = note("")
        col.addFull(overlayStatus, 0)
        handleButton = button("") { toggleHandle() }
        col.addFull(handleButton)
        val sideRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        leftButton = button("À gauche") { setSide(false) }
        rightButton = button("À droite") { setSide(true) }
        sideRow.addView(leftButton, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        sideRow.addView(View(this), LinearLayout.LayoutParams(dp(8), 1))
        sideRow.addView(rightButton, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        col.addFull(sideRow, 8)
        col.addView(note("Fais glisser la poignée vers le haut ou le bas pour la déplacer."))
        bootBox = CheckBox(this).apply {
            text = "Relancer la poignée au démarrage de la liseuse"
            setTextColor(Color.BLACK)
            isChecked = prefs.startOnBoot
        }
        col.addFull(bootBox)

        // OpenRouter
        col.addView(title("2. OpenRouter"))
        keyField = field(
            prefs.apiKey, "Clé API (sk-or-v1-…)",
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        )
        col.addFull(keyField)
        modelField = field(prefs.model, Prefs.DEFAULT_MODEL, InputType.TYPE_CLASS_TEXT)
        col.addFull(modelField, 8)
        col.addView(note("Identifiant du modèle sur OpenRouter, par exemple anthropic/claude-haiku-5.5."))

        // Affichage
        col.addView(title("3. Affichage"))
        sizeField = field(prefs.textSize.toString(), "17", InputType.TYPE_CLASS_NUMBER)
        col.addFull(sizeField)
        col.addView(note("Taille du texte du panneau (12 à 28)."))

        // Intégration
        col.addView(title("4. Menus de sélection"))
        col.addView(note(
            "Yomu apparaît déjà dans le menu de sélection des applis qui affichent la liste complète " +
                "(et dans « Partager »). Pour Chimahon, dont le menu n'affiche que « Translate », tu peux " +
                "aussi déclarer Yomu comme traducteur. Android choisira alors entre Yomu et Hoshi, ou te demandera."
        ))
        translateBox = CheckBox(this).apply {
            text = "Déclarer Yomu comme traducteur (bouton « Translate »)"
            setTextColor(Color.BLACK)
            setOnCheckedChangeListener { _, checked -> setTranslateAlias(checked) }
        }
        col.addFull(translateBox)

        // Prompt
        col.addView(title("5. Prompt"))
        promptField = field(
            prefs.systemPrompt, "Prompt système",
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        ).apply {
            minLines = 6
            maxLines = 14
            gravity = Gravity.TOP or Gravity.START
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        }
        col.addFull(promptField)
        col.addFull(button("Réinitialiser le prompt") {
            promptField.setText(Prefs.DEFAULT_SYSTEM_PROMPT)
        }, 8)

        // Boutons finaux
        col.addView(title("6. Enregistrer et tester"))
        col.addFull(button("Enregistrer") { save() })
        col.addFull(button("Tester sur « おんなとこどもはやらない »") { test() }, 8)

        return ScrollView(this).apply {
            setBackgroundColor(Color.WHITE)
            addView(col)
        }
    }
}
