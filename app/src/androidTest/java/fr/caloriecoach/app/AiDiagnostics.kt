package fr.caloriecoach.app

import android.app.Instrumentation
import android.os.Bundle
import android.os.SystemClock
import com.google.firebase.appcheck.FirebaseAppCheck
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

/** Explicit, on-device integration probe. Never reads the user's profile or chat. */
class AiDiagnostics : Instrumentation() {
    private var suite = false
    private var features = false
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); suite = arguments?.getString("suite") == "true"; features=arguments?.getString("features")=="true"; start() }
    override fun onStart() {
        com.google.firebase.FirebaseApp.initializeApp(targetContext)
        FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
            com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory.getInstance()
        )
        if(features) {
            val report=FeatureDiagnostics(this).run()
            finish(if(report.contains("FAILURES=0")) -1 else 1,Bundle().apply {putString("stream",report)})
            return
        }
        val report = StringBuilder()
        var failures = 0
        fun record(value: String) {
            report.appendLine(value)
            android.util.Log.i("CoachAiDiagnostic", value)
        }
        fun failure(error: Throwable): String = generateSequence(error) { it.cause }
            .take(8).joinToString(" -> ") { it.javaClass.simpleName + ": " + it.message.orEmpty() }
            .replace(Regex("AIza[\\w-]+"), "[redacted]")
            .replace(Regex("[a-fA-F0-9]{8}(?:-[a-fA-F0-9]{4}){3}-[a-fA-F0-9]{12}"), "[redacted]")
            .take(1600)
        runBlocking {
            val started = SystemClock.elapsedRealtime()
            try {
                record("APPCHECK_START")
                withTimeout(30_000) { FirebaseAppCheck.getInstance().getAppCheckToken(true).await() }
                record("APPCHECK_OK ${SystemClock.elapsedRealtime() - started}ms")
            } catch (error: Exception) { failures++; record("APPCHECK_FAILED " + failure(error)) }
            try {
                record("AI_START")
                val answer = withTimeout(85_000) { GeminiFoodEstimator.generateText("Réponds uniquement OK.", null, 64) }
                record("AI_OK ${SystemClock.elapsedRealtime() - started}ms answer=" + answer.take(100))
            } catch (error: Exception) { failures++; record("AI_FAILED " + failure(error)) }
            if (suite) {
                suspend fun probe(name: String, block: suspend () -> String) {
                    val time = SystemClock.elapsedRealtime()
                    try { record(name + "_OK " + (withTimeout(70_000) { block() }).take(180) + " elapsed=" + (SystemClock.elapsedRealtime()-time) + "ms") }
                    catch (error: Exception) { failures++; record(name + "_FAILED " + failure(error)) }
                }
                probe("CHAT") {
                    CoachChatStore.reply("", listOf(ChatMessage(true,"Mon activité préférée est la marche."), ChatMessage(false,"Très bien."), ChatMessage(true,"Quelle activité ai-je indiquée ? Réponds en une phrase.")), "Profil de test fictif, adulte.")
                        .also { check(it.contains("marche",true)) }
                }
                probe("WORKOUT") {
                    GeminiFoodEstimator.estimateWorkout("", "Marche rapide pendant 30 minutes", 70).also { check(it.second in 50..500) }.toString()
                }
                probe("PLAN_DAY") {
                    GeminiFoodEstimator.generatePlan("", "30", 70, "Maintien", 1, "Débutant", "Sans matériel", true)
                        .also { check(it.length > 100 && !it.contains("**")) }
                }
                probe("PLAN_WEEK") {
                    GeminiFoodEstimator.generatePlan("", "30", 70, "Maintien", 3, "Débutant", "Sans matériel", false)
                        .also { check(it.length > 300 && !it.contains("**")) }
                }
                probe("MEAL_IMAGE") {
                    // Synthetic fixture; verifies multimodal transport and JSON parsing, not calorie accuracy.
                    val bitmap = android.graphics.Bitmap.createBitmap(256,256,android.graphics.Bitmap.Config.ARGB_8888)
                    val canvas = android.graphics.Canvas(bitmap)
                    canvas.drawColor(android.graphics.Color.WHITE)
                    val paint = android.graphics.Paint().apply { color=android.graphics.Color.YELLOW; strokeWidth=30f; style=android.graphics.Paint.Style.STROKE }
                    canvas.drawArc(android.graphics.RectF(50f,40f,200f,200f),30f,140f,false,paint)
                    val out = java.io.ByteArrayOutputStream()
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG,85,out)
                    GeminiFoodEstimator.estimate("",out.toByteArray(),"Image de test illustrée : une banane moyenne, environ 120 g de chair, sans autre aliment.")
                        .also { check(it.second in 50..250) }.toString()
                }
                val descriptions=listOf("Une pomme crue de 150 g, sans autre aliment", "250 ml d’eau plate, sans sucre ni sirop", "200 g de riz cuit, 150 g de poulet grillé et 15 g d’huile d’olive")
                val ranges=listOf(50..140,0..5,450..900)
                descriptions.forEachIndexed { index, description ->
                    probe("MEAL_TEXT_$index") {
                        GeminiFoodEstimator.estimateMealDescription(description).also { check(it.second in ranges[index]) { "Estimation hors plage pour le cas $index : ${it.second}" } }.toString()
                    }
                }
                probe("MEAL_TEXT_EMPTY") {
                    check(runCatching { GeminiFoodEstimator.estimateMealDescription("   ") }.exceptionOrNull() is IllegalArgumentException)
                    "Entrée vide refusée sans appel réseau"
                }
            }
        }
        record("FAILURES=$failures")
        finish(if (failures == 0) -1 else 1, Bundle().apply { putString("stream", report.toString()) })
    }
}
