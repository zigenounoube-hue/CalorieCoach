package fr.caloriecoach.app

import android.graphics.BitmapFactory
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.RequestOptions
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext
import org.json.JSONObject

object GeminiFoodEstimator {
    const val MODEL = "gemini-3.5-flash-lite"
    private const val FALLBACK_MODEL = "gemini-3.8-flash"
    private const val MAX_ATTEMPTS = 2
    private const val REQUEST_TIMEOUT_MS = 30_000L

    private fun model(systemInstruction: String? = null, maxOutputTokens: Int = 1200, modelName: String = MODEL) = Firebase.ai(
        backend = GenerativeBackend.googleAI()
    ).generativeModel(
        modelName = modelName,
        generationConfig = generationConfig { this.maxOutputTokens = maxOutputTokens },
        systemInstruction = systemInstruction?.let { content { text(it) } },
        requestOptions = RequestOptions(timeoutInMillis = REQUEST_TIMEOUT_MS)
    )

    private fun cleanFormatting(text: String): String = text.lines().joinToString("\n") { line ->
        line.replace(Regex("^\\s{0,3}#{1,6}\\s*"), "")
            .replace(Regex("^\\s*[-*]\\s+"), "• ")
            .replace(Regex("\\*\\*(.+?)\\*\\*"), "$1")
            .replace("`", "")
    }

    suspend fun generateText(prompt: String, systemInstruction: String? = null): String =
        generateText(prompt, systemInstruction, 1200)

    suspend fun generateText(prompt: String, systemInstruction: String?, maxOutputTokens: Int): String = withContext(Dispatchers.IO) {
        retryTransiently { modelName ->
            model(systemInstruction, maxOutputTokens, modelName).generateContent(prompt).text?.trim()
                ?.let(::cleanFormatting)
                ?.takeIf { it.isNotBlank() } ?: throw IllegalStateException("Gemini n’a pas renvoyé de réponse.")
        }
    }

    private suspend fun <T> retryTransiently(block: suspend (String) -> T): T {
        var lastError: Exception? = null
        repeat(MAX_ATTEMPTS) { attempt ->
            try {
                return withTimeout(REQUEST_TIMEOUT_MS) { block(if (attempt == 0) MODEL else FALLBACK_MODEL) }
            } catch (error: TimeoutCancellationException) {
                lastError = error
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                val detail = generateSequence<Throwable>(error) { it.cause }.joinToString(" ") { it.message.orEmpty() }.lowercase()
                val permanent = when {
                    "app check" in detail || "appcheck" in detail -> "Accès IA refusé par Firebase App Check. La configuration de cette installation doit être vérifiée."
                    "404" in detail || "not found" in detail -> "Le modèle IA est introuvable ou indisponible pour ce projet."
                    "403" in detail || "permission denied" in detail || "api key" in detail -> "Accès IA refusé : vérifie la configuration Firebase du projet."
                    "429" in detail || "quota" in detail -> "Le quota IA est atteint. Réessaie plus tard."
                    else -> null
                }
                if (permanent != null) throw IllegalStateException(permanent, error)
                lastError = error
                val temporary = listOf("high demand", "overload", "503", "500", "502", "504", "unavailable", "timeout", "timed out", "network", "connection", "unexpected").any { it in detail }
                if (!temporary) throw IllegalStateException("La demande IA a échoué. Réessaie avec un message plus court.", error)
            }
            if (attempt < MAX_ATTEMPTS - 1) delay(700L)
        }
        throw IllegalStateException(
            "Le service IA est temporairement indisponible ou trop lent. Deux modèles ont été essayés ; réessaie dans un moment.",
            lastError
        )
    }

    private fun parseEstimate(text: String, defaultName: String): Pair<String, Int> {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        val jsonText = if (start >= 0 && end > start) text.substring(start, end + 1) else text
        val result = JSONObject(jsonText)
        return result.optString("name", defaultName) to result.optInt("kcal", 0).coerceIn(0, 5000)
    }

    suspend fun estimate(apiKey: String, jpeg: ByteArray, description: String = ""): Pair<String, Int> = withContext(Dispatchers.IO) {
        val bitmap = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)
            ?: throw IllegalArgumentException("La photo n’a pas pu être lue.")
        val context = if (description.isBlank()) "" else "Contexte donné par l’utilisateur : $description. "
        val prompt = "Observe cette photo de repas. ${context}Estime les calories de la portion visible. Réponds uniquement en JSON valide avec deux champs : name (nom court en français) et kcal (entier). Si la portion est incertaine, donne une estimation raisonnable, pas une mesure exacte."
        retryTransiently { modelName ->
            val response = model(maxOutputTokens = 256, modelName = modelName).generateContent(content {
                image(bitmap)
                text(prompt)
            })
            parseEstimate(response.text ?: "", "Repas estimé")
        }
    }

    suspend fun estimateWorkout(apiKey: String, description: String, weightKg: Int): Pair<String, Int> {
        val prompt = "Estime les calories dépensées par une personne de ${weightKg} kg pour cette séance décrite : $description. Réponds uniquement en JSON valide avec deux champs : name (nom court en français) et kcal (entier). Fais une estimation prudente."
        return parseEstimate(generateText(prompt, null, 256), "Séance")
    }

    suspend fun generatePlan(apiKey: String, age: String, weightKg: Int, goal: String, days: Int, level: String, equipment: String, oneDay: Boolean = false): String {
        val profile = if (age.isBlank()) "" else "Âge : $age ans. "
        val modePrompt = if (oneDay) "Crée une séance unique pour aujourd’hui, avec échauffement, exercices, séries ou durée et récupération. " else "Crée un programme hebdomadaire de fitness avec un jour de repos. "
        val prompt = modePrompt + profile + "Poids : ${weightKg} kg. Objectif : $goal. Niveau : $level. Matériel disponible : $equipment. " +
            (if (oneDay) "Durée visée : environ 30 à 45 minutes. " else "Nombre de séances : $days par semaine. ") +
            "Réponds en français avec un titre et le détail des exercices. Chaque séance doit être réaliste et progressive. Signale de réduire ou arrêter en cas de douleur. Ne donne pas de conseils médicaux."
        val instruction = "Réponds en français, de façon structurée et concise. Limite le programme hebdomadaire à 3 séances détaillées et la séance unique à 6 exercices maximum. Pas de Markdown : titres simples et listes courtes."
        return generateText(prompt, instruction, if (oneDay) 2200 else 3600)
    }
}


