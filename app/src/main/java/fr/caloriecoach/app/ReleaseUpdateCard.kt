package fr.caloriecoach.app

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

data class ReleaseNote(val version: String, val title: String, val changes: List<String>)

object AppChangelog {
    val entries = listOf(
        ReleaseNote("1.2.0", "À chacun son ambiance", listOf(
            "Cinq thèmes au choix : Minuit, Forêt, Océan, Crépuscule et Aube. Le choix reste mémorisé.",
            "Un repas peut être estimé par l’IA à partir d’une description seule, sans photo.",
            "Suppression des 450 kcal automatiques : les calories doivent être estimées ou saisies avant l’enregistrement.",
            "La source GitHub reste visible mais ne peut plus être modifiée par erreur.",
            "Les notes de version remplacent la simulation de mise à jour. L’encart technique de l’assistant a été retiré."
        )),
        ReleaseNote("1.1.3", "Un coach qui répond", listOf(
            "Accès IA corrigé et vérifié sur un téléphone Android.",
            "Réponses plus rapides, modèle de secours et nouveaux messages visibles automatiquement dans le chat.",
            "Génération des programmes à la journée et à la semaine corrigée."
        )),
        ReleaseNote("1.1.2", "Préparation des mises à jour", listOf("Identification de l’application pour les services IA et premières publications de test."))
    )
}

@Composable fun ReleaseUpdateCard() {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var failed by remember { mutableStateOf(false) }
    var foundRelease by remember { mutableStateOf<AppRelease?>(null) }
    var showRelease by remember { mutableStateOf(false) }
    var showNotes by remember { mutableStateOf(false) }

    Card(colors=CardDefaults.cardColors(containerColor=colors.surface), shape=RoundedCornerShape(24.dp), border=BorderStroke(1.dp, colors.outline)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text("Mises à jour", fontWeight=FontWeight.Bold)
            Text("Version ${BuildConfig.VERSION_NAME} · Retrouve les nouveautés et les prochaines versions.", color=colors.onSurfaceVariant, fontSize=12.sp)
            Surface(color=colors.surfaceVariant, shape=RoundedCornerShape(14.dp), modifier=Modifier.fillMaxWidth().testTag("update_repository")) {
                Row(Modifier.padding(14.dp), verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("Source des mises à jour · GitHub", color=colors.onSurfaceVariant, fontSize=11.sp)
                        SelectionContainer { Text(ReleaseUpdater.REPOSITORY, color=colors.onSurface, fontSize=12.sp) }
                    }
                    Icon(Icons.Default.Lock, "Source protégée", tint=colors.onSurfaceVariant, modifier=Modifier.size(18.dp))
                }
            }
            Button(enabled=!busy, modifier=Modifier.fillMaxWidth(), onClick={
                busy=true; failed=false; status="Vérification en cours…"
                scope.launch {
                    try {
                        val found = ReleaseUpdater.latest()
                        foundRelease=found
                        if (ReleaseUpdater.isNewer(found.version, BuildConfig.VERSION_NAME)) {
                            status="Nouvelle version disponible : ${found.version}"
                            showRelease=true
                        } else status="Tu utilises la dernière version disponible."
                    } catch (error: Exception) { failed=true; status=error.localizedMessage ?: "Vérification impossible. Réessaie plus tard." }
                    finally { busy=false }
                }
            }) { Text(if(busy) "Vérification…" else "Vérifier les mises à jour") }
            OutlinedButton(onClick={showNotes=true}, modifier=Modifier.fillMaxWidth()) { Text("Notes de version") }
            if(status.isNotBlank()) Text(status, color=if(failed) colors.error else colors.onSurfaceVariant, fontSize=12.sp)
            Text("Android te demande de confirmer l’installation d’une nouvelle version.", color=colors.onSurfaceVariant, fontSize=11.sp)
        }
    }
    if(showNotes) AlertDialog(
        onDismissRequest={showNotes=false}, title={Text("Notes de version")},
        text={
            Column(Modifier.heightIn(max=420.dp).verticalScroll(rememberScrollState()), verticalArrangement=Arrangement.spacedBy(16.dp)) {
                AppChangelog.entries.forEach { note ->
                    Column(verticalArrangement=Arrangement.spacedBy(7.dp)) {
                        Text("${note.version} · ${note.title}", fontWeight=FontWeight.Bold, color=colors.primary)
                        note.changes.forEach { Text("• $it", color=colors.onSurface, fontSize=13.sp, lineHeight=19.sp) }
                    }
                    HorizontalDivider()
                }
            }
        }, confirmButton={TextButton(onClick={showNotes=false}) { Text("Fermer") }}
    )
    if(showRelease) {
        val found=foundRelease
        AlertDialog(onDismissRequest={showRelease=false}, title={Text("Mise à jour ${found?.version.orEmpty()}")},
            text={Text(found?.notes.orEmpty(), modifier=Modifier.heightIn(max=360.dp).verticalScroll(rememberScrollState()))},
            confirmButton={TextButton(onClick={
                showRelease=false
                val url=found?.apkUrl ?: found?.releasePage
                if(!url.isNullOrBlank()) runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            }) { Text(if(found?.apkUrl!=null) "Télécharger l’APK" else "Ouvrir la publication") }},
            dismissButton={TextButton(onClick={showRelease=false}) { Text("Plus tard") }}
        )
    }
}
