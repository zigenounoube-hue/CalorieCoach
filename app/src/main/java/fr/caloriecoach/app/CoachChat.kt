package fr.caloriecoach.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class ChatMessage(val user: Boolean, val text: String, val time: Long = System.currentTimeMillis())

object CoachChatStore {
    private const val prefs = "coach_conversation"
    fun load(context: Context): List<ChatMessage> = try {
        val json = JSONArray(context.getSharedPreferences(prefs, 0).getString("messages", "[]"))
        (0 until json.length()).map { i -> json.getJSONObject(i).let { ChatMessage(it.getBoolean("user"), it.getString("text"), it.optLong("time")) } }
    } catch (_: Exception) { emptyList() }

    fun save(context: Context, messages: List<ChatMessage>) {
        val json = JSONArray()
        messages.forEach { json.put(JSONObject().put("user", it.user).put("text", it.text).put("time", it.time)) }
        context.getSharedPreferences(prefs, 0).edit().putString("messages", json.toString()).apply()
    }

    suspend fun reply(apiKey: String, messages: List<ChatMessage>, profileContext: String): String {
        require(messages.isNotEmpty()) { "Écris d’abord ton message." }
        val history = messages.dropLast(1).takeLast(40).joinToString("\n") { m ->
            (if (m.user) "Utilisateur : " else "Coach : ") + m.text.take(900)
        }
        val instruction = "Tu es le coach personnel de fitness et de nutrition de l’utilisateur. Réponds en français, clairement, avec bienveillance et sans prétendre remplacer un professionnel de santé. " +
            "Utilise le profil, l’historique et le journal fournis pour personnaliser tes réponses. Réponds sans Markdown, en moins de 180 mots sauf si une séance détaillée est demandée. Les calories sont approximatives. Évite les régimes extrêmes et les diagnostics. " +
            "Profil et contexte actuel : $profileContext\nConversation récente :\n$history"
        return GeminiFoodEstimator.generateText(messages.last().text, instruction)
    }
}
