package fr.caloriecoach.app

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.os.SystemClock
import android.graphics.Rect
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.accessibilityservice.AccessibilityServiceInfo
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import java.io.File

/** Uses real accessibility controls. Does not save test meals to the user's journal. */
class FeatureDiagnostics(private val inst: Instrumentation) {
    private val report=StringBuilder()
    private var failures=0
    private var activity: Activity?=null
    private fun pause() { SystemClock.sleep(550) }
    private fun all(): List<AccessibilityNodeInfo> {
        val result=mutableListOf<AccessibilityNodeInfo>()
        fun walk(node: AccessibilityNodeInfo) { result+=node; for(i in 0 until node.childCount) node.getChild(i)?.let(::walk) }
        inst.uiAutomation.rootInActiveWindow?.let(::walk)
        return result
    }
    private fun nodes(text:String) = all().filter { it.isVisibleToUser && (it.text?.toString()?.lineSequence()?.any { line -> line==text }==true || it.contentDescription?.toString()==text) }
    private fun clickable(node:AccessibilityNodeInfo):AccessibilityNodeInfo? {
        var current:AccessibilityNodeInfo?=node
        repeat(8) { if(current?.isClickable==true) return current; current=current?.parent }
        return null
    }
    private fun click(text:String) {
        var node:AccessibilityNodeInfo?=null
        var textNode:AccessibilityNodeInfo?=null
        for(attempt in 0 until 10) {
            val candidates=nodes(text)
            node=candidates.firstNotNullOfOrNull(::clickable)
            textNode=candidates.firstOrNull()
            if(node!=null || textNode!=null) break
            pause()
        }
        if(node!=null) {
            check(node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) { "Clic refusé : $text" }
        } else {
            val bounds=Rect().also { (textNode ?: error("Contrôle introuvable : $text")).getBoundsInScreen(it) }
            check(!bounds.isEmpty)
            val time=SystemClock.uptimeMillis()
            inst.sendPointerSync(MotionEvent.obtain(time,time,MotionEvent.ACTION_DOWN,bounds.exactCenterX(),bounds.exactCenterY(),0))
            inst.sendPointerSync(MotionEvent.obtain(time,time+60,MotionEvent.ACTION_UP,bounds.exactCenterX(),bounds.exactCenterY(),0))
        }
        pause()
    }
    private fun scrollTo(text:String) {
        if(nodes(text).isNotEmpty()) return
        repeat(12) {
            if(nodes(text).isNotEmpty()) return
            val scroll=all().firstOrNull { it.isScrollable && it.isVisibleToUser } ?: return@repeat
            if(!scroll.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)) return@repeat
            pause()
        }
        repeat(14) {
            if(nodes(text).isNotEmpty()) return
            val scroll=all().firstOrNull { it.isScrollable && it.isVisibleToUser } ?: error("Zone de défilement absente")
            scroll.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD); pause()
        }
        error("Texte non atteint : $text")
    }
    private fun edit(label:String,value:String) {
        var field=nodes(label).firstOrNull()
        repeat(4) { if(field?.isEditable!=true) field=field?.parent }
        val node=field?.takeIf { it.isEditable } ?: all().firstOrNull { it.isEditable && (it.text?.toString()==label || it.hintText?.toString()==label || it.findAccessibilityNodeInfosByText(label).isNotEmpty()) }
            ?: error("Champ absent : $label")
        check(node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,Bundle().apply {putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,value)}))
        pause()
    }
    private fun capture(name:String) {
        val bitmap=inst.uiAutomation.takeScreenshot() ?: error("Capture impossible")
        val folder=File(inst.targetContext.getExternalFilesDir(null) ?: inst.targetContext.filesDir,"validation").apply { mkdirs() }
        try { File(folder,"$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) } }
        finally { bitmap.recycle() }
    }
    private fun launch() {
        activity=inst.startActivitySync(Intent(inst.targetContext,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        repeat(10) { if(nodes("Réglages").isEmpty()) pause() }
    }
    private fun probe(name:String,block:()->Unit) {
        android.util.Log.i("CoachFeatureTest", "${name}_START")
        try { block(); report.appendLine("${name}_OK"); android.util.Log.i("CoachFeatureTest","${name}_OK") }
        catch(error:Exception) { failures++; report.appendLine("${name}_FAILED ${error.message}"); android.util.Log.e("CoachFeatureTest","${name}_FAILED ${error.message}") }
    }
    private fun contrast(a:Color,b:Color):Double {
        val x=a.luminance().toDouble(); val y=b.luminance().toDouble()
        return (maxOf(x,y)+.05)/(minOf(x,y)+.05)
    }
    fun run():String {
        inst.uiAutomation.serviceInfo=inst.uiAutomation.serviceInfo.apply {
            flags=flags or AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        }
        val original=CoachThemeStore.load(inst.targetContext)
        try {
            launch()
            probe("THEME_CONTRAST") {
                CoachTheme.entries.forEach { theme ->
                    val c=theme.colors()
                    check(contrast(c.onSurface,c.surface)>=4.5) { "${theme.id}: texte" }
                    check(contrast(c.onSurfaceVariant,c.surfaceVariant)>=4.5) { "${theme.id}: texte secondaire" }
                    check(contrast(c.onPrimary,c.primary)>=4.5) { "${theme.id}: bouton" }
                }
            }
            CoachTheme.entries.forEach { theme ->
                probe("THEME_${theme.id.uppercase()}") {
                    click("Réglages"); scrollTo(theme.title); click(theme.title)
                    check(CoachThemeStore.load(inst.targetContext)==theme)
                    click("Aujourd'hui"); capture("theme-${theme.id}")
                }
            }
            probe("THEME_RESTART") {
                inst.runOnMainSync { activity?.finish() }; pause(); launch()
                check(CoachThemeStore.load(inst.targetContext)==CoachTheme.DAWN)
                capture("theme-aube-restart")
            }
            probe("SETTINGS_UPDATE") {
                click("Réglages"); scrollTo("Notes de version")
                val source=nodes(ReleaseUpdater.REPOSITORY).firstOrNull() ?: error("Source GitHub absente")
                check(!source.isEditable)
                check(all().none { it.isEditable && it.text?.contains("CalorieCoach")==true })
                check(nodes("Simuler une mise à jour").isEmpty())
                check(nodes("Assistant Gemini").isEmpty())
                capture("settings-updates")
                click("Notes de version")
                check(nodes("1.2.0 · À chacun son ambiance").isNotEmpty())
                capture("release-notes"); click("Fermer")
            }
            probe("MEAL_NO_DEFAULT") {
                click("Aujourd'hui"); scrollTo("Ajouter un repas"); click("Ajouter un repas")
                check(nodes("Enregistrer").firstNotNullOfOrNull(::clickable)?.isEnabled==false)
                edit("Décris ton repas","Une pomme de 150 g")
                check(nodes("Estimer les calories").firstNotNullOfOrNull(::clickable)?.isEnabled==true)
                check(nodes("Enregistrer").firstNotNullOfOrNull(::clickable)?.isEnabled==false)
                capture("meal-description-ready")
                scrollTo("Estimer les calories"); click("Estimer les calories")
                for(attempt in 0 until 120) {
                    if(nodes("Enregistrer").firstNotNullOfOrNull(::clickable)?.isEnabled==true) break
                    pause()
                }
                check(nodes("Enregistrer").firstNotNullOfOrNull(::clickable)?.isEnabled==true) { "L’estimation de la description n’a pas abouti" }
                scrollTo("Calories"); capture("meal-description-estimated")
                check(all().filter { it.isEditable }.mapNotNull { it.text?.toString()?.toIntOrNull() }.any { it in 50..140 }) { "Calories de la pomme incorrectes dans l’interface" }
                // Manual correction is allowed, but no fake journal entry is saved.
                scrollTo("Calories"); edit("Calories","80")
                check(nodes("Enregistrer").firstNotNullOfOrNull(::clickable)?.isEnabled==true)
                scrollTo("Décris ton repas")
                edit("Décris ton repas","Deux pommes de 150 g")
                check(nodes("Enregistrer").firstNotNullOfOrNull(::clickable)?.isEnabled==false)
                click("Annuler")
            }
        } finally {
            CoachThemeStore.save(inst.targetContext,original)
            inst.runOnMainSync { activity?.finish() }
        }
        report.appendLine("FAILURES=$failures")
        return report.toString()
    }
}
