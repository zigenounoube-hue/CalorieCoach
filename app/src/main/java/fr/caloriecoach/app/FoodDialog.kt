package fr.caloriecoach.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable fun FoodDialog(apiKey: String, onDismiss: () -> Unit, onSave: (String, Int) -> Unit) {
    var description by remember { mutableStateOf("") }
    var calories by remember { mutableStateOf("") }
    var estimatedName by remember { mutableStateOf<String?>(null) }
    var adjusted by remember { mutableStateOf(false) }
    var photo by remember { mutableStateOf<Uri?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = MaterialTheme.colorScheme
    fun resetEstimate() { calories=""; estimatedName=null; error=null; adjusted=false }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { selected ->
        if(selected != null) { photo=selected; resetEstimate() }
    }
    val kcal = calories.toIntOrNull()?.takeIf { it in 0..20_000 }
    val canSave = !busy && kcal != null && (description.isNotBlank() || estimatedName != null)

    AlertDialog(
        onDismissRequest={if(!busy) onDismiss()},
        title={Text("Ajouter un repas")},
        text={
            Column(Modifier.heightIn(max=460.dp).verticalScroll(rememberScrollState()), verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Text("Une photo ou quelques mots suffisent pour demander une estimation.", color=colors.onSurfaceVariant, fontSize=12.sp)
                OutlinedTextField(
                    value=description, onValueChange={description=it; resetEstimate()}, enabled=!busy,
                    label={Text("Décris ton repas")}, placeholder={Text("Ex. 150 g de riz cuit, 2 œufs et une cuillère d’huile")},
                    minLines=2, maxLines=5, modifier=Modifier.fillMaxWidth().testTag("meal_description")
                )
                Text("Ajoute les quantités, boissons et sauces si tu les connais. Sans quantité, l’IA suppose une portion habituelle.", color=colors.onSurfaceVariant, fontSize=11.sp)
                OutlinedButton(enabled=!busy, onClick={picker.launch("image/*")}, modifier=Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.CameraAlt, null, modifier=Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                    Text(if(photo!=null) "Remplacer la photo" else "Ajouter une photo (facultatif)")
                }
                if(photo!=null) TextButton(enabled=!busy, onClick={photo=null; resetEstimate()}) { Text("Retirer la photo") }
                Button(
                    enabled=!busy && (description.isNotBlank() || photo!=null), modifier=Modifier.fillMaxWidth().testTag("meal_estimate"),
                    onClick={
                        val selectedPhoto=photo
                        val mealDescription=description.trim()
                        busy=true; resetEstimate()
                        scope.launch {
                            try {
                                val result = if(selectedPhoto==null) {
                                    GeminiFoodEstimator.estimateMealDescription(mealDescription)
                                } else {
                                    val bytes=withContext(Dispatchers.IO) {
                                        val original=context.contentResolver.openInputStream(selectedPhoto)?.use { BitmapFactory.decodeStream(it) }
                                            ?: throw IllegalStateException("La photo n’a pas pu être lue.")
                                        try {
                                            val scale=minOf(1f,1024f/maxOf(original.width,original.height))
                                            val resized=Bitmap.createScaledBitmap(original,(original.width*scale).toInt().coerceAtLeast(1),(original.height*scale).toInt().coerceAtLeast(1),true)
                                            try { ByteArrayOutputStream().use { output -> resized.compress(Bitmap.CompressFormat.JPEG,82,output); output.toByteArray() } }
                                            finally { if(resized!==original) resized.recycle() }
                                        } finally { original.recycle() }
                                    }
                                    GeminiFoodEstimator.estimate(apiKey,bytes,mealDescription)
                                }
                                estimatedName=result.first
                                calories=result.second.toString()
                            } catch (exception: Exception) {
                                error=exception.message ?: "L’estimation a échoué. Réessaie ou saisis les calories."
                            } finally { busy=false }
                        }
                    }
                ) {
                    if(busy) { CircularProgressIndicator(Modifier.size(16.dp), strokeWidth=2.dp, color=colors.onPrimary); Spacer(Modifier.width(8.dp)) }
                    Text(if(busy) "Estimation en cours…" else "Estimer les calories")
                }
                estimatedName?.let { Text("$it · ${if(adjusted) "valeur ajustée" else "estimation IA"}", color=colors.primary, fontSize=12.sp) }
                OutlinedTextField(
                    value=calories, onValueChange={calories=it.filter(Char::isDigit).take(6); adjusted=true},
                    enabled=!busy, label={Text("Calories")}, suffix={Text("kcal")}, singleLine=true,
                    keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),
                    supportingText={Text("Estime avec l’IA ou saisis une valeur connue. Tu peux la corriger.")},
                    isError=calories.isNotBlank() && kcal==null,
                    modifier=Modifier.fillMaxWidth().testTag("meal_calories")
                )
                if(calories.isNotBlank() && kcal==null) Text("Saisis une valeur entre 0 et 20 000 kcal.", color=colors.error, fontSize=12.sp)
                error?.let { Text(it, color=colors.error, fontSize=12.sp) }
                Text("Les calories restent approximatives : vérifie les portions avant d’enregistrer.", color=colors.onSurfaceVariant, fontSize=11.sp)
            }
        },
        confirmButton={Button(enabled=canSave, modifier=Modifier.testTag("meal_save"), onClick={
            // No implicit calorie value: only a validated estimate or an explicit manual value is saved.
            if(canSave) onSave(estimatedName ?: description.trim(), requireNotNull(kcal))
        }) { Text("Enregistrer") }},
        dismissButton={TextButton(enabled=!busy, onClick=onDismiss) { Text("Annuler") }}
    )
}
