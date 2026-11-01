package fr.caloriecoach.app

import android.content.Context
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import org.json.JSONArray

object CloudSync {
    private fun database() = FirebaseFirestore.getInstance()
    private fun user(uid: String) = database().collection("users").document(uid)

    suspend fun restoreIfPresent(context: Context, uid: String): Boolean {
        val root = user(uid)
        val profile = root.collection("private").document("profile").get().await()
        if (!profile.exists()) return false

        val profilePrefs = context.getSharedPreferences("coach_profile", Context.MODE_PRIVATE)
        profilePrefs.edit().apply {
            profile.data.orEmpty().forEach { (key, value) ->
                when (value) {
                    is String -> putString(key, value)
                    is Boolean -> putBoolean(key, value)
                    is Number -> putInt(key, value.toInt())
                }
            }
        }.apply()

        val entriesPrefs = context.getSharedPreferences("coach_entries", Context.MODE_PRIVATE)
        root.collection("entries").get().await().documents.forEach { doc ->
            doc.getString("json")?.let { entriesPrefs.edit().putString("entries_${doc.id}", it).apply() }
        }
        val chat = root.collection("private").document("conversation").get().await()
        chat.getString("json")?.let { context.getSharedPreferences("coach_conversation", Context.MODE_PRIVATE).edit().putString("messages", it).apply() }
        return true
    }

    suspend fun upload(context: Context, uid: String) {
        val root = user(uid)
        val profilePrefs = context.getSharedPreferences("coach_profile", Context.MODE_PRIVATE)
        val profile = hashMapOf<String, Any>()
        profilePrefs.all.forEach { (key, value) ->
            if (value is String || value is Boolean || value is Int || value is Long || value is Float || value is Double) profile[key] = value
        }
        profile["updatedAt"] = FieldValue.serverTimestamp()
        root.collection("private").document("profile").set(profile).await()

        val entriesPrefs = context.getSharedPreferences("coach_entries", Context.MODE_PRIVATE)
        entriesPrefs.all.forEach { (key, value) ->
            if (key.startsWith("entries_") && value is String) {
                val date = key.removePrefix("entries_")
                root.collection("entries").document(date).set(mapOf("json" to value)).await()
            }
        }
        val messages = context.getSharedPreferences("coach_conversation", Context.MODE_PRIVATE).getString("messages", "[]") ?: "[]"
        // Keep the remote conversation comfortably below Firestore's per-document size limit.
        val trimmed = try {
            val all = JSONArray(messages)
            val recent = JSONArray()
            for (i in (all.length() - 80).coerceAtLeast(0) until all.length()) recent.put(all.get(i))
            recent.toString()
        } catch (_: Exception) { "[]" }
        root.collection("private").document("conversation").set(mapOf("json" to trimmed, "updatedAt" to FieldValue.serverTimestamp())).await()
    }
}
