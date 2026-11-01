package fr.caloriecoach.app

import android.graphics.BitmapFactory
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject

object GeminiFoodEstimator {
    const val MODEL = "gemini-3.8-flash"
    private const val MAX_ATTEMPTS = 3

    private fun model(systemInstruction: String? = null) = Firebase.ai(
        backend = GenerativeBackend.googleAI()
    ).generativeModel(
        modelName = MODEL,
        systemInstruction = systemInstruction?.let { content { text(it) } }
    )

    suspend fun generateText(prompt: String, systemInstruction: String? = null): String = withContext(Dispatchers.IO) {
        retryTransiently {
            model(systemInstruction).generateContent(prompt).text?.trim()
                ?.takeIf { it.isNotBlank() } ?: throw IllegalStateException("Gemini n’a pas renvoyé de réponse.")
        }
    }

    private suspend fun <T> retryTransiently(block: suspend () -> T): T {
        var lastError: Exception? = null
        repeat(MAX_ATTEMPTS) { attempt ->
            try {
                return block()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                lastError = error
                if (attempt < MAX_ATTEMPTS - 1) delay(700L * (attempt + 1))
            }
        }
        throw IllegalStateException(
            "Le service IA ne répond pas après $MAX_ATTEMPTS tentatives. Vérifie ta connexion puis réessaie. (${lastError?.localizedMessage ?: "erreur réseau"})",
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
        retryTransiently {
            val response = model().generateContent(content {
                image(bitmap)
                text(prompt)
            })
            parseEstimate(response.text ?: "", "Repas estimé")
        }
    }

    suspend fun estimateWorkout(apiKey: String, description: String, weightKg: Int): Pair<String, Int> {
        val prompt = "Estime les calories dépensées par une personne de ${weightKg} kg pour cette séance décrite : $description. Réponds uniquement en JSON valide avec deux champs : name (nom court en français) et kcal (entier). Fais une estimation prudente."
        return parseEstimate(generateText(prompt), "Séance")
    }

    suspend fun generatePlan(apiKey: String, age: String, weightKg: Int, goal: String, days: Int, level: String, equipment: String, oneDay: Boolean = false): String {
        val profile = if (age.isBlank()) "" else "Âge : $age ans. "
        val modePrompt = if (oneDay) "Crée une séance unique pour aujourd’hui, avec échauffement, exercices, séries ou durée et récupération. " else "Crée un programme hebdomadaire de fitness avec un jour de repos. "
        val prompt = modePrompt + profile + "Poids : ${weightKg} kg. Objectif : $goal. Niveau : $level. Matériel disponible : $equipment. " +
            (if (oneDay) "Durée visée : environ 30 à 45 minutes. " else "Nombre de séances : $days par semaine. ") +
            "Réponds en français avec un titre et le détail des exercices. Chaque séance doit être réaliste et progressive. Signale de réduire ou arrêter en cas de douleur. Ne donne pas de conseils médicaux."
        return generateText(prompt)
    }
}
