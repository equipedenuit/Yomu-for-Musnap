package app.yomu

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/** Un message de la conversation envoyée au modèle. */
data class ChatMessage(val role: String, val content: String)

/**
 * Client minimal pour l'API OpenRouter (format compatible OpenAI), en streaming.
 * Bloquant : à appeler depuis un thread de fond.
 */
class OpenRouter(private val apiKey: String, private val model: String) {

    @Volatile
    private var connection: HttpURLConnection? = null

    @Volatile
    var cancelled = false
        private set

    fun cancel() {
        cancelled = true
        try {
            connection?.disconnect()
        } catch (_: Exception) {
        }
    }

    /**
     * Envoie la conversation et appelle [onDelta] à chaque morceau de texte reçu.
     * Lève une exception avec un message lisible en cas d'erreur.
     */
    fun stream(messages: List<ChatMessage>, onDelta: (String) -> Unit) {
        val body = JSONObject()
            .put("model", model)
            .put("stream", true)
            .put("max_tokens", 2000)
            .put("messages", JSONArray().apply {
                messages.forEach { m ->
                    put(JSONObject().put("role", m.role).put("content", m.content))
                }
            })

        val conn = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20_000
            readTimeout = 90_000
            doOutput = true
            setRequestProperty("Authorization", "Bearer $apiKey")
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "text/event-stream")
            setRequestProperty("HTTP-Referer", "https://github.com/yomu-app")
            setRequestProperty("X-Title", "Yomu")
        }
        connection = conn

        try {
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

            val code = conn.responseCode
            if (code !in 200..299) {
                val err = conn.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                throw RuntimeException(describeError(code, err))
            }

            BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { reader ->
                while (!cancelled) {
                    val line = reader.readLine() ?: break
                    if (!line.startsWith("data:")) continue // commentaires SSE (« : OPENROUTER PROCESSING »)
                    val data = line.removePrefix("data:").trim()
                    if (data == "[DONE]") break
                    if (data.isEmpty()) continue
                    val json = try {
                        JSONObject(data)
                    } catch (_: Exception) {
                        continue
                    }
                    json.optJSONObject("error")?.let { e ->
                        throw RuntimeException("Erreur OpenRouter : " + e.optString("message", e.toString()))
                    }
                    val choices = json.optJSONArray("choices") ?: continue
                    if (choices.length() == 0) continue
                    val delta = choices.getJSONObject(0).optJSONObject("delta") ?: continue
                    if (delta.isNull("content")) continue
                    val text = delta.optString("content", "")
                    if (text.isNotEmpty()) onDelta(text)
                }
            }
        } finally {
            try {
                conn.disconnect()
            } catch (_: Exception) {
            }
            connection = null
        }
    }

    private fun describeError(code: Int, raw: String): String {
        val msg = try {
            JSONObject(raw).optJSONObject("error")?.optString("message")
        } catch (_: Exception) {
            null
        }
        val hint = when (code) {
            401 -> "Clé API invalide ou absente (vérifie-la dans les réglages)."
            402 -> "Crédit OpenRouter insuffisant."
            404 -> "Modèle introuvable : vérifie son identifiant dans les réglages."
            429 -> "Trop de requêtes : réessaie dans un instant."
            else -> null
        }
        return listOfNotNull("Erreur $code", hint, msg?.takeIf { it.isNotBlank() }).joinToString("\n")
    }

    companion object {
        private const val ENDPOINT = "https://openrouter.ai/api/v1/chat/completions"
    }
}
