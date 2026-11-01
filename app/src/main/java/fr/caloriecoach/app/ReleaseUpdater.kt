package fr.caloriecoach.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class AppRelease(
    val version: String,
    val notes: String,
    val releasePage: String,
    val apkUrl: String?
)

object ReleaseUpdater {
    suspend fun latest(repository: String): AppRelease = withContext(Dispatchers.IO) {
        val normalized = repository.trim().removePrefix("https://github.com/").trimEnd('/')
        require(Regex("^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$").matches(normalized)) {
            "Saisis le dépôt GitHub au format propriétaire/projet."
        }
        val connection = URL("https://api.github.com/repos/$normalized/releases/latest")
            .openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 12_000
            connection.readTimeout = 18_000
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("User-Agent", "CalorieCoach-Android")
            val status = connection.responseCode
            if (status !in 200..299) {
                val detail = connection.errorStream?.bufferedReader()?.use { it.readText() }
                throw IllegalStateException(
                    if (status == 404) "Aucune publication GitHub trouvée. Vérifie le dépôt et publie une version."
                    else "GitHub n’a pas répondu correctement (HTTP $status). ${detail.orEmpty()}".take(260)
                )
            }
            val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            val assets = json.optJSONArray("assets")
            val apk = (0 until (assets?.length() ?: 0))
                .mapNotNull { assets?.optJSONObject(it) }
                .firstOrNull { it.optString("name").endsWith(".apk", ignoreCase = true) }
                ?.optString("browser_download_url")
                ?.takeIf { it.startsWith("https://") }
            AppRelease(
                version = json.optString("tag_name", "Version inconnue"),
                notes = json.optString("body").ifBlank { "Aucune note de version fournie." },
                releasePage = json.optString("html_url"),
                apkUrl = apk
            )
        } finally {
            connection.disconnect()
        }
    }

    fun isNewer(remote: String, local: String): Boolean {
        fun parts(version: String) = Regex("\\d+").findAll(version).mapNotNull { it.value.toIntOrNull() }.toList()
        val remoteParts = parts(remote)
        val localParts = parts(local)
        if (remoteParts.isEmpty() || localParts.isEmpty()) return remote.trim() != local.trim()
        for (index in 0 until maxOf(remoteParts.size, localParts.size)) {
            val a = remoteParts.getOrElse(index) { 0 }
            val b = localParts.getOrElse(index) { 0 }
            if (a != b) return a > b
        }
        return false
    }
}
