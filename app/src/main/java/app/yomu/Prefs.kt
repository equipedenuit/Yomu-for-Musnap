package app.yomu

import android.content.Context
import android.content.SharedPreferences

/** Réglages de l'app, stockés localement sur l'appareil. */
class Prefs(context: Context) {
    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences("yomu", Context.MODE_PRIVATE)

    var apiKey: String
        get() = sp.getString("api_key", "") ?: ""
        set(v) = sp.edit().putString("api_key", v.trim()).apply()

    var model: String
        get() = sp.getString("model", DEFAULT_MODEL) ?: DEFAULT_MODEL
        set(v) = sp.edit().putString("model", v.trim().ifEmpty { DEFAULT_MODEL }).apply()

    var systemPrompt: String
        get() = sp.getString("system_prompt", DEFAULT_SYSTEM_PROMPT) ?: DEFAULT_SYSTEM_PROMPT
        set(v) = sp.edit().putString("system_prompt", v.ifBlank { DEFAULT_SYSTEM_PROMPT }).apply()

    /** true = poignée à droite, false = à gauche. */
    var handleOnRight: Boolean
        get() = sp.getBoolean("handle_right", true)
        set(v) = sp.edit().putBoolean("handle_right", v).apply()

    /** Position verticale de la poignée, en pixels depuis le haut. */
    var handleY: Int
        get() = sp.getInt("handle_y", -1)
        set(v) = sp.edit().putInt("handle_y", v).apply()

    var startOnBoot: Boolean
        get() = sp.getBoolean("start_on_boot", true)
        set(v) = sp.edit().putBoolean("start_on_boot", v).apply()

    var handleEnabled: Boolean
        get() = sp.getBoolean("handle_enabled", false)
        set(v) = sp.edit().putBoolean("handle_enabled", v).apply()

    /** Taille du texte du panneau, en sp. */
    var textSize: Int
        get() = sp.getInt("text_size", 17)
        set(v) = sp.edit().putInt("text_size", v.coerceIn(12, 28)).apply()

    companion object {
        const val DEFAULT_MODEL = "anthropic/claude-haiku-5.5"

        val DEFAULT_SYSTEM_PROMPT = """
Tu es un professeur de japonais pour un francophone de niveau intermédiaire qui lit des mangas en VO. Tu réponds toujours en français, de façon concise.

Format de sortie : texte brut uniquement. Pas de markdown : pas d'astérisques, pas de #, pas de tableau, pas de romaji. Utilise des titres en MAJUSCULES et des tirets.

Pour le texte japonais fourni :

TRADUCTION
Une traduction française naturelle, qui garde le ton et le registre (familier, poli, enfantin, dialectal…).

MOT À MOT
Chaque mot ou bloc dans l'ordre, une ligne chacun, au format :
- mot（lecture en hiragana）— forme du dictionnaire — sens dans ce contexte
N'oublie aucune particule.

GRAMMAIRE
Les formes et tournures notables (contractions orales comme ちゃ／ねー, conjugaisons, terminaisons comme よ／な／ね, keigo), une ligne chacune.

NUANCE
Une phrase sur le registre et ce qu'il dit du personnage ou de la situation, seulement si c'est utile.

Si le texte semble incomplet ou si l'OCR a fait une erreur visible, signale-le et propose la lecture la plus probable. Si on te pose ensuite une question, réponds-y directement et brièvement, sans reprendre tout le format.
""".trim()
    }
}
