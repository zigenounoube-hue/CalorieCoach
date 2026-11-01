package fr.caloriecoach.app

import android.os.Bundle
import android.os.Build
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await
import com.google.firebase.auth.FirebaseAuth
import java.io.ByteArrayOutputStream
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.TimePicker
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

private val Ink = Color(0xFFE7F0F2)
private val Green = Color(0xFF879B70)
private val OnGreen = Color(0xFF121811)
private val Accent = Color(0xFFA7B99A)
private val Paper = Color(0xFF080D11)
private val Muted = Color(0xFF8D9BA2)
private val Panel = Color(0xFF111A20)
private val PanelRaised = Color(0xFF17242A)
private val Border = Color(0xFF26363C)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme(colorScheme = darkColorScheme(primary = Green, onPrimary = Paper, secondary = Accent, background = Paper, onBackground = Ink, surface = Panel, onSurface = Ink, surfaceVariant = PanelRaised, onSurfaceVariant = Muted, outline = Border)) { CoachApp() } }
    }
}

private data class LogItem(val title: String, val subtitle: String, val kcal: Int, val food: Boolean)

private fun loadTodayEntries(context: android.content.Context): List<LogItem> = try {
    val key = "entries_" + java.time.LocalDate.now().toString()
    val json = org.json.JSONArray(context.getSharedPreferences("coach_entries", 0).getString(key, "[]"))
    (0 until json.length()).map { i -> json.getJSONObject(i).let { LogItem(it.getString("title"), it.getString("subtitle"), it.getInt("kcal"), it.getBoolean("food")) } }
} catch (_: Exception) { emptyList() }

private fun saveTodayEntries(context: android.content.Context, entries: List<LogItem>) {
    val json = org.json.JSONArray()
    entries.forEach { json.put(org.json.JSONObject().put("title", it.title).put("subtitle", it.subtitle).put("kcal", it.kcal).put("food", it.food)) }
    context.getSharedPreferences("coach_entries", 0).edit().putString("entries_" + java.time.LocalDate.now().toString(), json.toString()).apply()
}

@Composable
private fun CoachApp() {
    val appContext = LocalContext.current
    val profilePrefs = remember { appContext.getSharedPreferences("coach_profile", 0) }
    var tab by remember { mutableIntStateOf(0) }
    var showFood by remember { mutableStateOf(false) }
    var showWorkout by remember { mutableStateOf(false) }
    var showPlan by remember { mutableStateOf(false) }
    var dailyTarget by remember { mutableIntStateOf(profilePrefs.getInt("daily_target", 2000)) }
    var age by remember { mutableStateOf(profilePrefs.getString("age", "") ?: "") }
    var height by remember { mutableStateOf(profilePrefs.getString("height", "") ?: "") }
    var weight by remember { mutableStateOf(profilePrefs.getString("weight", "") ?: "") }
    var goal by remember { mutableStateOf(profilePrefs.getString("goal", "Perte de poids") ?: "Perte de poids") }
    var sex by remember { mutableStateOf(profilePrefs.getString("sex", "") ?: "") }
    var activityLevel by remember { mutableStateOf(profilePrefs.getString("activity", "") ?: "") }
    var targetOverride by remember { mutableStateOf(profilePrefs.getBoolean("target_override", false)) }
    var showTutorial by remember { mutableStateOf((!profilePrefs.getBoolean("tutorial_done", false) || !profilePrefs.getBoolean("setup_seen", false))) }
    var assistantMessages by remember { mutableStateOf(CoachChatStore.load(appContext)) }
    var assistantBusy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val calorieSuggestion = remember(age,height,weight,sex,activityLevel,goal) { CalorieCalculator.estimate(age,height,weight,sex,activityLevel,goal) }
    val effectiveTarget = if (!targetOverride && calorieSuggestion != null) calorieSuggestion.target else dailyTarget
    var geminiKey by remember { mutableStateOf("firebase-ai-logic") }
    var signedInEmail by remember { mutableStateOf(FirebaseAuth.getInstance().currentUser?.email.orEmpty()) }
    var accountBusy by remember { mutableStateOf(false) }
    var accountMessage by remember { mutableStateOf("") }
    val reminderPrefs = remember { appContext.getSharedPreferences("workout_reminders", 0) }
    var remindersEnabled by remember { mutableStateOf(reminderPrefs.getBoolean("enabled", false)) }
    var reminderDays by remember { mutableStateOf(reminderPrefs.getStringSet("days", setOf("2","4","6"))!!.mapNotNull { it.toIntOrNull() }.toSet()) }
    var reminderHour by remember { mutableIntStateOf(reminderPrefs.getInt("hour", 18)) }
    var reminderMinute by remember { mutableIntStateOf(reminderPrefs.getInt("minute", 0)) }
    var savedPlan by remember { mutableStateOf(profilePrefs.getString("saved_plan", "") ?: "") }
    var items by remember { mutableStateOf(loadTodayEntries(appContext)) }
    LaunchedEffect(signedInEmail, age, height, weight, goal, sex, activityLevel, effectiveTarget, savedPlan, items, assistantMessages) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return@LaunchedEffect
        delay(1400)
        try { CloudSync.upload(appContext, uid) } catch (_: Exception) { /* Local data remains available; the next change retries sync. */ }
    }
    val eaten = items.filter { it.food }.sumOf { it.kcal }
    val burned = items.filterNot { it.food }.sumOf { it.kcal }
    Scaffold(containerColor = Paper, bottomBar = {
        NavigationBar(containerColor = Panel, tonalElevation = 0.dp) {
            listOf("Aujourd'hui", "Journal", "Programmes", "Coach IA", "Réglages").forEachIndexed { i, label ->
                val icon = when(i) { 0 -> Icons.Default.Home; 1 -> Icons.Default.Restaurant; 2 -> Icons.Default.FitnessCenter; 3 -> Icons.Default.SmartToy; else -> Icons.Default.Settings }
                NavigationBarItem(selected = tab == i, onClick = { tab = i }, icon = { Icon(icon, null) }, label = { Text(label, fontSize = 10.sp) }, colors = NavigationBarItemDefaults.colors(selectedIconColor=Paper, indicatorColor=Green, selectedTextColor=Green, unselectedTextColor=Muted))
            }
        }
    }) { pad ->
        AnimatedContent(targetState=tab, modifier=Modifier.padding(pad).background(Brush.verticalGradient(listOf(Paper, Color(0xFF0B151A))), RoundedCornerShape(0.dp)), transitionSpec={ fadeIn(tween(220)) + slideInVertically(tween(220)){it/24} togetherWith fadeOut(tween(140)) }, label="tab-transition") { selected ->
            when(selected) {
                0 -> Dashboard(Modifier.fillMaxSize(), eaten, burned, effectiveTarget, items, onFood={showFood=true}, onWorkout={showWorkout=true}, onPlan={showPlan=true})
                1 -> Journal(Modifier.fillMaxSize(), items, onFood={showFood=true}, onWorkout={showWorkout=true})
                2 -> Plans(Modifier.fillMaxSize(), savedPlan, onPlan={showPlan=true})
                3 -> AssistantPage(Modifier.fillMaxSize(), assistantMessages, assistantBusy, onSend={ question ->
                    val userMessage=ChatMessage(true,question); assistantMessages=(assistantMessages+userMessage).takeLast(80); CoachChatStore.save(appContext,assistantMessages); assistantBusy=true
                    scope.launch { try { val profile="Âge: $age, taille: $height cm, poids: $weight kg, sexe: $sex, activité: $activityLevel, objectif: $goal, cible: $effectiveTarget kcal. Journal du jour: "+items.joinToString { it.title+" ("+it.kcal+" kcal)" }; val answer=CoachChatStore.reply(geminiKey,assistantMessages,profile); assistantMessages=(assistantMessages+ChatMessage(false,answer)).takeLast(80); CoachChatStore.save(appContext,assistantMessages) } catch(e:Exception) { assistantMessages=(assistantMessages+ChatMessage(false,e.message ?: "Réponse indisponible.")).takeLast(80); CoachChatStore.save(appContext,assistantMessages) } finally { assistantBusy=false } }
                })
                else -> SettingsPage(Modifier.fillMaxSize(), effectiveTarget, { dailyTarget=it; targetOverride=true; profilePrefs.edit().putInt("daily_target",it).putBoolean("target_override",true).apply() }, age, { age=it; profilePrefs.edit().putString("age",it).apply() }, height, { height=it; profilePrefs.edit().putString("height",it).apply() }, weight, { weight=it; profilePrefs.edit().putString("weight",it).apply() }, goal, { goal=it; profilePrefs.edit().putString("goal",it).apply() }, sex, { sex=it; profilePrefs.edit().putString("sex",it).apply() }, activityLevel, { activityLevel=it; profilePrefs.edit().putString("activity",it).apply() }, calorieSuggestion, { targetOverride=false; calorieSuggestion?.let{dailyTarget=it.target}; profilePrefs.edit().putBoolean("target_override",false).apply() }, geminiKey, { }, remindersEnabled, reminderDays, reminderHour, reminderMinute, { enabled, days, hour, minute -> remindersEnabled=enabled; reminderDays=days; reminderHour=hour; reminderMinute=minute; WorkoutReminderScheduler.schedule(appContext,enabled,days,hour,minute) }, signedInEmail, accountBusy, accountMessage, { email, password, create ->
                    accountBusy=true; accountMessage=""
                    scope.launch {
                        try {
                            val auth=FirebaseAuth.getInstance()
                            val user=if(create) auth.createUserWithEmailAndPassword(email.trim(),password).await().user else auth.signInWithEmailAndPassword(email.trim(),password).await().user
                            val uid=user?.uid ?: throw IllegalStateException("Le compte Firebase n’a pas pu être ouvert.")
                            val restored=CloudSync.restoreIfPresent(appContext,uid)
                            if(!restored) CloudSync.upload(appContext,uid)
                            age=profilePrefs.getString("age","") ?: ""; height=profilePrefs.getString("height","") ?: ""; weight=profilePrefs.getString("weight","") ?: ""; goal=profilePrefs.getString("goal","Perte de poids") ?: "Perte de poids"; sex=profilePrefs.getString("sex","") ?: ""; activityLevel=profilePrefs.getString("activity","") ?: ""; dailyTarget=profilePrefs.getInt("daily_target",2000); targetOverride=profilePrefs.getBoolean("target_override",false); savedPlan=profilePrefs.getString("saved_plan","") ?: ""; items=loadTodayEntries(appContext); assistantMessages=CoachChatStore.load(appContext)
                            signedInEmail=user.email.orEmpty()
                            accountMessage=if(restored) "Profil, journal et conversation récupérés." else "Compte créé et données synchronisées."
                        } catch(e:Exception) { accountMessage=e.localizedMessage ?: "Connexion impossible. Vérifie tes identifiants et réessaie." }
                        finally { accountBusy=false }
                    }
                }, { FirebaseAuth.getInstance().signOut(); signedInEmail=""; accountMessage="Déconnecté. Les données restent sur cet appareil." })
            }
        }
    }
    if(showTutorial) TutorialDialog(age,height,weight,goal,sex,activityLevel,onFinish={ a,h,w,g,sx,act -> age=a; height=h; weight=w; goal=g; sex=sx; activityLevel=act; profilePrefs.edit().putString("age",a).putString("height",h).putString("weight",w).putString("goal",g).putString("sex",sx).putString("activity",act).putBoolean("tutorial_done",true).putBoolean("setup_seen",true).apply(); showTutorial=false })
    if(showFood) FoodDialog(geminiKey,{showFood=false}) { name, kcal -> items = (listOf(LogItem(name,"Repas · maintenant",kcal,true)) + items).also { saveTodayEntries(appContext,it) }; showFood=false }
    if(showWorkout) WorkoutDialog(geminiKey,weight.toIntOrNull() ?: 70,{showWorkout=false}) { name, kcal -> items = (listOf(LogItem(name,"Séance · maintenant",kcal,false)) + items).also { saveTodayEntries(appContext,it) }; showWorkout=false }
    if(showPlan) PlanDialog(geminiKey, age, weight.toIntOrNull() ?: 70, goal, {showPlan=false}) { program -> savedPlan=program; profilePrefs.edit().putString("saved_plan",program).apply(); showPlan=false }
}

@Composable private fun Dashboard(modifier: Modifier, eaten: Int, burned: Int, target: Int, items: List<LogItem>, onFood:()->Unit, onWorkout:()->Unit, onPlan:()->Unit) {
    val remaining by animateIntAsState((target-eaten+burned).coerceAtLeast(0),animationSpec=tween(650),label="remaining-calories")
    val progress by animateFloatAsState((eaten.toFloat()/target).coerceIn(0f,1f),animationSpec=tween(850),label="calorie-progress")
    LazyColumn(modifier.fillMaxSize(), contentPadding=PaddingValues(start=22.dp,end=22.dp,top=24.dp,bottom=28.dp), verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceBetween, verticalAlignment=Alignment.CenterVertically) { Column { Text(java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("EEEE d MMMM", java.util.Locale.FRENCH)).uppercase(), color=Muted, fontSize=11.sp, fontWeight=FontWeight.Bold, letterSpacing=1.4.sp); Text("On avance à ton rythme.", color=Ink, fontSize=24.sp, fontWeight=FontWeight.Bold) }; Box(Modifier.size(44.dp).background(Green,CircleShape), contentAlignment=Alignment.Center){Text("👋",fontSize=21.sp)} } }
        item { Card(modifier=Modifier.shadow(14.dp,RoundedCornerShape(26.dp),ambientColor=Green.copy(alpha=.08f),spotColor=Green.copy(alpha=.12f)),colors=CardDefaults.cardColors(containerColor=PanelRaised), shape=RoundedCornerShape(26.dp),border=androidx.compose.foundation.BorderStroke(1.dp,Border)) { Column(Modifier.fillMaxWidth().padding(22.dp)) { Text("TON BILAN DU JOUR", color=Color.White.copy(.65f), fontSize=11.sp, fontWeight=FontWeight.Bold, letterSpacing=1.3.sp); Spacer(Modifier.height(18.dp)); Row(verticalAlignment=Alignment.Bottom) { Text("$remaining", color=Green, fontSize=48.sp, fontWeight=FontWeight.Bold); Spacer(Modifier.width(8.dp)); Text("kcal restantes", color=Color.White, modifier=Modifier.padding(bottom=8.dp)) }; Spacer(Modifier.height(16.dp)); LinearProgressIndicator(progress={progress}, modifier=Modifier.fillMaxWidth().height(8.dp), color=Green, trackColor=Color.White.copy(.15f)); Spacer(Modifier.height(18.dp)); Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) { Stat("MANGÉ", "$eaten", "objectif $target"); Stat("BRÛLÉ", "$burned", "activité") } } } }
        item { Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) { ActionTile("Ajouter un repas", Icons.Default.Add, Modifier.weight(1f), onFood); ActionTile("Noter une séance", Icons.Default.Bolt, Modifier.weight(1f), onWorkout) } }
        item { Card(onClick=onPlan, modifier=Modifier.shadow(10.dp,RoundedCornerShape(22.dp),ambientColor=Green.copy(alpha=.12f),spotColor=Green.copy(alpha=.18f)), colors=CardDefaults.cardColors(containerColor=Green), shape=RoundedCornerShape(22.dp)) { Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment=Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("À FAIRE AUJOURD'HUI", fontSize=10.sp, color=OnGreen.copy(.72f), fontWeight=FontWeight.Bold, letterSpacing=1.sp); Spacer(Modifier.height(5.dp)); Text("Ta prochaine séance t'attend", fontWeight=FontWeight.Bold, color=OnGreen, fontSize=16.sp); Text("Un programme adapté à ton énergie", color=OnGreen.copy(.82f), fontSize=12.sp) }; Icon(Icons.Default.DirectionsRun,null,tint=OnGreen,modifier=Modifier.size(34.dp)) } } }
        item { Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){Text("Aujourd'hui",fontSize=18.sp,fontWeight=FontWeight.Bold,color=Ink); Text("Tout voir",color=Muted,fontSize=12.sp)} }
        if(items.isEmpty()) item { AnimatedVisibility(visible=true,enter=fadeIn(tween(450))+slideInVertically(tween(450)){it/12}) { Text("Aucune entrée aujourd’hui. Ajoute ton premier repas ou ta première séance.",color=Muted,fontSize=13.sp,modifier=Modifier.padding(6.dp)) } }
        items(items.take(4).size) { i -> LogRow(items[i]) }
    }
}
@Composable private fun Stat(label:String,value:String,sub:String){Column {Text(label,color=Color.White.copy(.55f),fontSize=10.sp,fontWeight=FontWeight.Bold,letterSpacing=1.sp);Text("$value kcal",color=Color.White,fontWeight=FontWeight.Bold,fontSize=18.sp);Text(sub,color=Color.White.copy(.55f),fontSize=11.sp)}}
@Composable private fun ActionTile(text:String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier:Modifier, click:()->Unit){Card(onClick=click,modifier=modifier.height(88.dp).animateContentSize().shadow(6.dp,RoundedCornerShape(18.dp),ambientColor=Green.copy(alpha=.04f)),colors=CardDefaults.cardColors(containerColor=Panel),shape=RoundedCornerShape(18.dp),border=androidx.compose.foundation.BorderStroke(1.dp,Border)){Column(Modifier.fillMaxSize().padding(14.dp),verticalArrangement=Arrangement.SpaceBetween){Icon(icon,null,tint=Ink);Text(text,color=Ink,fontWeight=FontWeight.SemiBold,fontSize=13.sp)}}}
@Composable private fun LogRow(item:LogItem){Card(colors=CardDefaults.cardColors(containerColor=Panel),shape=RoundedCornerShape(17.dp),border=androidx.compose.foundation.BorderStroke(1.dp,Border)){Row(Modifier.fillMaxWidth().padding(14.dp),verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(42.dp).background(if(item.food)Color(0xFF3A2B20) else Color(0xFF213329),CircleShape),contentAlignment=Alignment.Center){Icon(if(item.food)Icons.Default.Restaurant else Icons.Default.DirectionsRun,null,tint=Ink)};Column(Modifier.weight(1f).padding(start=12.dp)){Text(item.title,color=Ink,fontWeight=FontWeight.SemiBold,fontSize=14.sp);Text(item.subtitle,color=Muted,fontSize=11.sp)};Text("${if(item.food)"+" else "−"}${item.kcal}",color=Ink,fontWeight=FontWeight.Bold,fontSize=14.sp)}}}

@Composable private fun Journal(modifier:Modifier,items:List<LogItem>,onFood:()->Unit,onWorkout:()->Unit){LazyColumn(modifier.fillMaxSize(),contentPadding=PaddingValues(22.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){item{Text("Ton journal",fontSize=28.sp,fontWeight=FontWeight.Bold,color=Ink);Text(java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("EEEE d MMMM", java.util.Locale.FRENCH)).replaceFirstChar{it.uppercase()} + " · suivi du jour",color=Muted)};item{Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){Button(onClick=onFood){Text("+ Repas")}; OutlinedButton(onClick=onWorkout){Text("+ Séance")}}};items(items.size){LogRow(items[it])}}}
@Composable private fun Plans(modifier:Modifier,savedPlan:String,onPlan:()->Unit){Column(modifier.fillMaxSize().padding(22.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(16.dp)){Text("Programmes",fontSize=28.sp,fontWeight=FontWeight.Bold,color=Ink);Text("Un plan évolutif adapté à ton profil.",color=Muted);Card(colors=CardDefaults.cardColors(containerColor=Panel),shape=RoundedCornerShape(22.dp),border=androidx.compose.foundation.BorderStroke(1.dp,Border)){Column(Modifier.fillMaxWidth().padding(20.dp)){Text(if(savedPlan.isBlank())"TON COACH IA" else "TON PROGRAMME",fontSize=10.sp,fontWeight=FontWeight.Bold,color=Green,letterSpacing=1.2.sp);Spacer(Modifier.height(9.dp));Text(if(savedPlan.isBlank())"Crée une routine qui te ressemble" else savedPlan,fontSize=15.sp,fontWeight=if(savedPlan.isBlank())FontWeight.Bold else FontWeight.Normal,color=Ink,lineHeight=22.sp);Spacer(Modifier.height(14.dp));Button(onClick=onPlan,modifier=Modifier.fillMaxWidth()){Text(if(savedPlan.isBlank())"Créer mon programme avec l’IA" else "Régénérer mon programme")}}};Text("Le programme est une suggestion à adapter à tes sensations et à ton niveau.",color=Muted,fontSize=12.sp)}}
@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun SettingsPage(modifier:Modifier,target:Int,onTarget:(Int)->Unit,age:String,onAge:(String)->Unit,height:String,onHeight:(String)->Unit,weight:String,onWeight:(String)->Unit,goal:String,onGoal:(String)->Unit,sex:String,onSex:(String)->Unit,activityLevel:String,onActivity:(String)->Unit,suggestion:CalorieSuggestion?,onResetTarget:()->Unit,apiKey:String,onApiKey:(String)->Unit,remindersEnabled:Boolean,reminderDays:Set<Int>,reminderHour:Int,reminderMinute:Int,onReminderUpdate:(Boolean,Set<Int>,Int,Int)->Unit,signedInEmail:String,accountBusy:Boolean,accountMessage:String,onAccountSubmit:(String,String,Boolean)->Unit,onSignOut:()->Unit){
    val context=LocalContext.current
    var showTimePicker by remember { mutableStateOf(false) }
    var accountEmail by remember { mutableStateOf("") }
    var accountPassword by remember { mutableStateOf("") }
    var permissionDenied by remember { mutableStateOf(false) }
    val permissionLauncher=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> permissionDenied=!granted; if(granted)onReminderUpdate(true,reminderDays,reminderHour,reminderMinute) }
    val dayOptions=listOf("Lun" to 2,"Mar" to 3,"Mer" to 4,"Jeu" to 5,"Ven" to 6,"Sam" to 7,"Dim" to 1)
    Column(modifier.fillMaxSize().padding(22.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        Text("Réglages",fontSize=28.sp,fontWeight=FontWeight.Bold,color=Ink)
        Card(colors=CardDefaults.cardColors(containerColor=Panel),shape=RoundedCornerShape(20.dp),border=androidx.compose.foundation.BorderStroke(1.dp,Border)){Column(Modifier.fillMaxWidth().padding(18.dp),verticalArrangement=Arrangement.spacedBy(9.dp)){Text("Compte et sauvegarde",fontWeight=FontWeight.Bold,color=Ink);if(signedInEmail.isNotBlank()){Text("Connecté en tant que $signedInEmail",color=Muted,fontSize=12.sp);Text("Ton profil, ton journal et ta conversation sont synchronisés dans ton espace privé.",color=Muted,fontSize=12.sp);OutlinedButton(onClick=onSignOut,enabled=!accountBusy){Text("Se déconnecter")}}else{Text("Crée un compte ou connecte-toi pour retrouver tes données sur un autre appareil.",color=Muted,fontSize=12.sp);OutlinedTextField(accountEmail,{accountEmail=it},label={Text("Adresse e-mail")},singleLine=true,modifier=Modifier.fillMaxWidth());OutlinedTextField(accountPassword,{accountPassword=it},label={Text("Mot de passe")},singleLine=true,visualTransformation=androidx.compose.ui.text.input.PasswordVisualTransformation(),modifier=Modifier.fillMaxWidth());Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Button(enabled=!accountBusy&&accountEmail.contains("@")&&accountPassword.length>=6,onClick={onAccountSubmit(accountEmail,accountPassword,true)}){Text(if(accountBusy)"Patiente…" else "Créer un compte")};OutlinedButton(enabled=!accountBusy&&accountEmail.contains("@")&&accountPassword.isNotBlank(),onClick={onAccountSubmit(accountEmail,accountPassword,false)}){Text("Connexion")}}};if(accountMessage.isNotBlank())Text(accountMessage,color=if(accountMessage.contains("impossible")||accountMessage.contains("erreur"))MaterialTheme.colorScheme.error else Green,fontSize=12.sp)}}
        Card(colors=CardDefaults.cardColors(containerColor=Panel),shape=RoundedCornerShape(20.dp)){Column(Modifier.padding(18.dp)){Text("Ton profil",fontWeight=FontWeight.Bold,color=Ink);Spacer(Modifier.height(8.dp));Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){OutlinedTextField(age,onAge,label={Text("Âge")},suffix={Text("ans")},modifier=Modifier.weight(1f),singleLine=true);OutlinedTextField(height,onHeight,label={Text("Taille")},suffix={Text("cm")},modifier=Modifier.weight(1f),singleLine=true)};OutlinedTextField(weight,onWeight,label={Text("Poids")},suffix={Text("kg")},modifier=Modifier.fillMaxWidth(),singleLine=true);Text("Objectif",color=Muted,fontSize=12.sp);Row(horizontalArrangement=Arrangement.spacedBy(5.dp)){listOf("Perte de poids","Maintien","Prise de muscle").forEach{FilterChip(selected=goal==it,onClick={onGoal(it)},label={Text(it,fontSize=11.sp)})}};Text("Formule de calcul",color=Muted,fontSize=12.sp);Row(horizontalArrangement=Arrangement.spacedBy(5.dp)){listOf("Femme","Homme","Moyenne").forEach{FilterChip(selected=sex==it,onClick={onSex(it)},label={Text(it,fontSize=11.sp)})}};Text("Activité habituelle hors séances",color=Muted,fontSize=12.sp);Column { listOf("Sédentaire","Léger","Modéré","Très actif").chunked(2).forEach{row->Row(horizontalArrangement=Arrangement.spacedBy(5.dp)){row.forEach{FilterChip(selected=activityLevel==it,onClick={onActivity(it)},label={Text(it,fontSize=11.sp)})}}} }}}
        Card(colors=CardDefaults.cardColors(containerColor=Panel),shape=RoundedCornerShape(20.dp)){Column(Modifier.padding(18.dp)){Text("Objectif calories quotidien",fontWeight=FontWeight.Bold,color=Ink);Text("$target kcal",fontSize=24.sp,color=Ink);Text(suggestion?.let{"Entretien estimé : ${it.maintenance} kcal · suggestion ajustable"} ?: "Complète âge, taille, poids, sexe et activité pour obtenir une estimation.",fontSize=11.sp,color=Muted);if(suggestion!=null)TextButton(onClick=onResetTarget){Text("Revenir à la suggestion")};Slider(value=target.toFloat(),onValueChange={onTarget((it/50).toInt()*50)},valueRange=1200f..3500f,steps=45,colors=SliderDefaults.colors(thumbColor=Ink,activeTrackColor=Green))}}
        Card(colors=CardDefaults.cardColors(containerColor=Panel),shape=RoundedCornerShape(20.dp),border=androidx.compose.foundation.BorderStroke(1.dp,Border)){Column(Modifier.fillMaxWidth().padding(18.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){Row(verticalAlignment=Alignment.CenterVertically){Icon(Icons.Default.NotificationsActive,null,tint=Green);Column(Modifier.weight(1f).padding(start=12.dp)){Text("Rappels de séance",fontWeight=FontWeight.Bold,color=Ink);Text("Choisis tes jours et ton heure",color=Muted,fontSize=12.sp)};Switch(checked=remindersEnabled,onCheckedChange={enabled->if(enabled&&Build.VERSION.SDK_INT>=33&&androidx.core.content.ContextCompat.checkSelfPermission(context,android.Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS) else {permissionDenied=false;onReminderUpdate(enabled,reminderDays,reminderHour,reminderMinute)}})};Row(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically){Text("Heure",color=Muted,fontSize=12.sp);OutlinedButton(onClick={showTimePicker=true}){Text(String.format(java.util.Locale.getDefault(),"%02d:%02d",reminderHour,reminderMinute),color=Ink)}};Row(horizontalArrangement=Arrangement.spacedBy(3.dp)){dayOptions.take(4).forEach{(label,day)->FilterChip(selected=day in reminderDays,onClick={val next=if(day in reminderDays)reminderDays-day else reminderDays+day;onReminderUpdate(remindersEnabled,next,reminderHour,reminderMinute)},label={Text(label,fontSize=10.sp)})}};Row(horizontalArrangement=Arrangement.spacedBy(3.dp)){dayOptions.drop(4).forEach{(label,day)->FilterChip(selected=day in reminderDays,onClick={val next=if(day in reminderDays)reminderDays-day else reminderDays+day;onReminderUpdate(remindersEnabled,next,reminderHour,reminderMinute)},label={Text(label,fontSize=10.sp)})}};if(permissionDenied)Text("Autorise les notifications Android pour recevoir tes rappels.",color=MaterialTheme.colorScheme.error,fontSize=11.sp)}}
        Card(colors=CardDefaults.cardColors(containerColor=Panel),shape=RoundedCornerShape(20.dp),border=androidx.compose.foundation.BorderStroke(1.dp,Border)){Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){Text("Assistant Gemini",fontWeight=FontWeight.Bold,color=Ink);Text("Connecté via Firebase AI Logic. Aucune clé API personnelle n’est affichée dans l’application. Les photos de repas envoyées pour estimation sont traitées par Gemini.",color=Muted,fontSize=12.sp,lineHeight=18.sp)}}
        ReleaseUpdateCard()
    }
    if(showTimePicker){val timeState=rememberTimePickerState(initialHour=reminderHour,initialMinute=reminderMinute,is24Hour=true);AlertDialog(onDismissRequest={showTimePicker=false},title={Text("Heure du rappel")},text={TimePicker(state=timeState)},confirmButton={Button(onClick={onReminderUpdate(remindersEnabled,reminderDays,timeState.hour,timeState.minute);showTimePicker=false}){Text("Valider")}},dismissButton={TextButton(onClick={showTimePicker=false}){Text("Annuler")}})}
}

@Composable private fun ReleaseUpdateCard() {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("app_updates", 0) }
    val scope = rememberCoroutineScope()
    var repository by remember { mutableStateOf(preferences.getString("repository", "zigenounoube-hue/CalorieCoach") ?: "zigenounoube-hue/CalorieCoach") }
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var release by remember { mutableStateOf<AppRelease?>(null) }
    var showRelease by remember { mutableStateOf(false) }
    val localVersion = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0" }.getOrDefault("1.0") }

    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(20.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Border)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Mises à jour", fontWeight = FontWeight.Bold, color = Ink)
            Text("V$localVersion · Consulte les nouveautés et télécharge les versions publiées sur GitHub.", color = Muted, fontSize = 12.sp)
            OutlinedTextField(repository, { repository = it }, label = { Text("Dépôt GitHub · propriétaire/projet") }, placeholder = { Text("ex. zigen/CalorieCoach") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Button(enabled = !busy && repository.isNotBlank(), onClick = {
                busy = true
                status = "Vérification en cours…"
                preferences.edit().putString("repository", repository.trim()).apply()
                scope.launch {
                    try {
                        val found = ReleaseUpdater.latest(repository)
                        release = found
                        if (ReleaseUpdater.isNewer(found.version, localVersion)) {
                            status = "Nouvelle version disponible : ${found.version}"
                            showRelease = true
                        } else status = "Tu utilises déjà la dernière version publiée (${found.version})."
                    } catch (error: Exception) {
                        status = error.localizedMessage ?: "Impossible de vérifier les mises à jour."
                    } finally { busy = false }
                }
            }, modifier = Modifier.fillMaxWidth()) { Text(if (busy) "Vérification…" else "Vérifier les mises à jour") }
            if (status.isNotBlank()) Text(status, color = if (status.startsWith("Impossible") || status.startsWith("Saisis") || status.startsWith("GitHub")) MaterialTheme.colorScheme.error else Muted, fontSize = 12.sp)
            Text("Les APK téléchargés sont installés avec l’autorisation Android habituelle.", color = Muted, fontSize = 11.sp)
        }
    }
    if (showRelease) {
        val found = release
        AlertDialog(
            onDismissRequest = { showRelease = false },
            title = { Text("Mise à jour ${found?.version.orEmpty()}") },
            text = { Text(found?.notes.orEmpty(), color = Ink, modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) },
            confirmButton = { TextButton(onClick = {
                showRelease = false
                val url = found?.apkUrl ?: found?.releasePage
                if (!url.isNullOrBlank()) runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(url))) }
            }) { Text(if (found?.apkUrl != null) "Télécharger l’APK" else "Ouvrir la publication") } },
            dismissButton = { TextButton(onClick = { showRelease = false }) { Text("Plus tard") } }
        )
    }
}

@Composable private fun FoodDialog(apiKey:String,onDismiss:()->Unit,onSave:(String,Int)->Unit){
    var name by remember{mutableStateOf("")};var kcal by remember{mutableStateOf("")};var photoUri by remember{mutableStateOf<Uri?>(null)};var busy by remember{mutableStateOf(false)};var error by remember{mutableStateOf<String?>(null)}
    val context= LocalContext.current;val scope= rememberCoroutineScope();val picker= rememberLauncherForActivityResult(ActivityResultContracts.GetContent()){photoUri=it;error=null}
    AlertDialog(onDismissRequest=onDismiss,title={Text("Ajouter un repas",fontWeight=FontWeight.Bold)},text={Column(verticalArrangement=Arrangement.spacedBy(9.dp)){
        Text("Décris ton repas ou choisis une photo. L’estimation est approximative.",color=Muted,fontSize=12.sp)
        OutlinedButton(onClick={picker.launch("image/*")},modifier=Modifier.fillMaxWidth()){Icon(Icons.Default.CameraAlt,null);Spacer(Modifier.width(8.dp));Text(if(photoUri!=null)"Photo choisie" else "Choisir une photo")}
        OutlinedTextField(value=name,onValueChange={name=it},label={Text("Description ou nom du repas")},singleLine=true)
        if(photoUri!=null) Button(enabled=!busy,onClick={
            busy=true;error=null;scope.launch { try { val input=context.contentResolver.openInputStream(photoUri!!)?.use{BitmapFactory.decodeStream(it)} ?: throw IllegalStateException("Photo illisible");val scale=minOf(1f,1024f/maxOf(input.width,input.height));val bitmap=Bitmap.createScaledBitmap(input,(input.width*scale).toInt().coerceAtLeast(1),(input.height*scale).toInt().coerceAtLeast(1),true);val out=ByteArrayOutputStream();bitmap.compress(Bitmap.CompressFormat.JPEG,82,out);val result=GeminiFoodEstimator.estimate(apiKey,out.toByteArray(),name);name=result.first;kcal=result.second.toString() }catch(e:Exception){error=e.message ?: "Analyse impossible."}finally{busy=false} }
        },modifier=Modifier.fillMaxWidth()){Text(if(busy)"Analyse en cours…" else "Analyser la photo avec Gemini")}
        if(kcal.isNotBlank())OutlinedTextField(value=kcal,onValueChange={kcal=it.filter(Char::isDigit)},label={Text("Calories estimées")},suffix={Text("kcal")},singleLine=true)
        error?.let{Text(it,color=MaterialTheme.colorScheme.error,fontSize=12.sp)}
    }},confirmButton={Button(onClick={if(name.isNotBlank())onSave(name,kcal.toIntOrNull()?:450)},enabled=name.isNotBlank()&&!busy){Text("Enregistrer")}},dismissButton={TextButton(onClick=onDismiss){Text("Annuler")}})
}
@Composable private fun WorkoutDialog(apiKey:String,initialWeight:Int,onDismiss:()->Unit,onSave:(String,Int)->Unit){var activity by remember{mutableStateOf("Course à pied")};var minutes by remember{mutableStateOf("30")};var weight by remember{mutableStateOf(initialWeight.toString())};var description by remember{mutableStateOf("")};var estimated by remember{mutableStateOf<Int?>(null)};var sessionName by remember{mutableStateOf("")};var busy by remember{mutableStateOf(false)};var error by remember{mutableStateOf<String?>(null)};val scope=rememberCoroutineScope();val types=listOf("Course à pied","Marche rapide","Vélo","Renforcement","Natation");val met=when(activity){"Course à pied"->9.0;"Marche rapide"->4.3;"Vélo"->7.5;"Natation"->7.0;else->5.0};val kcal=estimated ?: (met*(weight.toDoubleOrNull()?:70.0)*(minutes.toDoubleOrNull()?:30.0)/60).toInt();AlertDialog(onDismissRequest=onDismiss,title={Text("Noter une séance",fontWeight=FontWeight.Bold)},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){Text("Estimation indicative basée sur l'activité, la durée et le poids.",color=Muted,fontSize=12.sp);types.forEach{FilterChip(selected=activity==it,onClick={activity=it},label={Text(it)},leadingIcon=if(activity==it){{Icon(Icons.Default.Check,null)}}else null)};OutlinedTextField(value=minutes,onValueChange={minutes=it.filter(Char::isDigit)},label={Text("Durée")},suffix={Text("min")},singleLine=true);OutlinedTextField(value=weight,onValueChange={weight=it.filter(Char::isDigit)},label={Text("Poids")},suffix={Text("kg")},singleLine=true);OutlinedTextField(value=description,onValueChange={description=it;estimated=null},label={Text("Décris ta séance")},placeholder={Text("ex. 5 km en 30 minutes avec quelques côtes")},modifier=Modifier.fillMaxWidth());Button(enabled=!busy && description.isNotBlank(),onClick={busy=true;error=null;scope.launch{try{val result=GeminiFoodEstimator.estimateWorkout(apiKey,description,weight.toIntOrNull()?:70);sessionName=result.first;estimated=result.second}catch(e:Exception){error=e.message?:"Estimation impossible."}finally{busy=false}}},modifier=Modifier.fillMaxWidth()){Text(if(busy)"Analyse en cours…" else "Estimer avec Gemini")};error?.let{Text(it,color=MaterialTheme.colorScheme.error,fontSize=12.sp)};Text("Environ $kcal kcal dépensées",fontWeight=FontWeight.Bold,color=Ink)}},confirmButton={Button(onClick={onSave(if(sessionName.isNotBlank())sessionName else activity,kcal)},enabled=!busy){Text("Enregistrer")}},dismissButton={TextButton(onClick=onDismiss){Text("Annuler")}})}
@Composable private fun PlanDialog(apiKey:String,age:String,weight:Int,goal:String,onDismiss:()->Unit,onSave:(String)->Unit){
    var days by remember{mutableIntStateOf(3)};var oneDay by remember{mutableStateOf(false)};var level by remember{mutableStateOf("Débutant")};var equipment by remember{mutableStateOf("Sans matériel")};var output by remember{mutableStateOf("")};var busy by remember{mutableStateOf(false)};var error by remember{mutableStateOf<String?>(null)};val scope=rememberCoroutineScope()
    AlertDialog(onDismissRequest=onDismiss,title={Text("Ton programme sur mesure",fontWeight=FontWeight.Bold)},text={Column(Modifier.heightIn(max=480.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)){
        Text("Généré à partir de ton profil et de tes choix.",color=Muted,fontSize=12.sp);Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){listOf("Aujourd’hui","Semaine").forEach{label->FilterChip(selected=oneDay==(label=="Aujourd’hui"),onClick={oneDay=label=="Aujourd’hui"},label={Text(label)})}};if(!oneDay){Text("Rythme · $days séances / semaine",fontWeight=FontWeight.SemiBold);Slider(value=days.toFloat(),onValueChange={days=it.toInt().coerceIn(2,5)},valueRange=2f..5f,steps=2)}
        Text("Niveau",color=Muted,fontSize=12.sp);Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){listOf("Débutant","Intermédiaire").forEach{FilterChip(selected=level==it,onClick={level=it},label={Text(it)})}}
        Text("Matériel",color=Muted,fontSize=12.sp);Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){listOf("Sans matériel","Haltères").forEach{FilterChip(selected=equipment==it,onClick={equipment=it},label={Text(it)})}}
        Button(enabled=!busy,onClick={busy=true;error=null;scope.launch{try{output=GeminiFoodEstimator.generatePlan(apiKey,age,weight,goal,if(oneDay) 1 else days,level,equipment,oneDay)}catch(e:Exception){error=e.message?:"Création impossible."}finally{busy=false}}},modifier=Modifier.fillMaxWidth()){Text(if(busy)"Ton programme se prépare…" else "Générer avec Gemini")}
        error?.let{Text(it,color=MaterialTheme.colorScheme.error,fontSize=12.sp)};if(output.isNotBlank()){HorizontalDivider();Text(output,color=Ink,fontSize=13.sp,lineHeight=19.sp)}
    }},confirmButton={Button(onClick={onSave(output)},enabled=output.isNotBlank()&&!busy){Text("Enregistrer le programme")}},dismissButton={TextButton(onClick=onDismiss){Text("Fermer")}})
}

@Composable private fun AssistantPage(modifier:Modifier,messages:List<ChatMessage>,busy:Boolean,onSend:(String)->Unit){
    var draft by remember { mutableStateOf("") }
    Column(modifier.fillMaxSize().padding(horizontal=16.dp, vertical=18.dp)) {
        Text("Coach IA",fontSize=28.sp,fontWeight=FontWeight.Bold,color=Ink)
        Text("Une conversation mémorisée sur cet appareil.",fontSize=12.sp,color=Muted,modifier=Modifier.padding(bottom=12.dp))
        LazyColumn(Modifier.weight(1f).fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(10.dp),contentPadding=PaddingValues(vertical=8.dp)) {
            if(messages.isEmpty()) item { Card(colors=CardDefaults.cardColors(containerColor=Panel),shape=RoundedCornerShape(18.dp)){Text("Demande un conseil, une séance ou un ajustement à partir de ton journal.",Modifier.padding(16.dp),color=Muted)} }
            items(messages.size) { i -> val m=messages[i]; Row(Modifier.fillMaxWidth(),horizontalArrangement=if(m.user)Arrangement.End else Arrangement.Start){Card(colors=CardDefaults.cardColors(containerColor=if(m.user)PanelRaised else Panel),shape=RoundedCornerShape(18.dp),border=androidx.compose.foundation.BorderStroke(1.dp,Border),modifier=Modifier.widthIn(max=320.dp)){Text(m.text,Modifier.padding(13.dp),color=Ink,fontSize=14.sp,lineHeight=20.sp)}} }
            if(busy) item { Text("Le coach réfléchit…",color=Muted,fontSize=12.sp) }
        }
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){
            OutlinedTextField(value=draft,onValueChange={draft=it},modifier=Modifier.weight(1f),placeholder={Text("Écris au coach…")},maxLines=4)
            IconButton(enabled=draft.isNotBlank()&&!busy,onClick={val q=draft.trim();draft="";onSend(q)}){Icon(Icons.Default.Send,"Envoyer",tint=Green)}
        }
    }
}
@Composable private fun TutorialDialog(initialAge:String,initialHeight:String,initialWeight:String,initialGoal:String,initialSex:String,initialActivity:String,onFinish:(String,String,String,String,String,String)->Unit){
    var step by remember { mutableIntStateOf(0) }
    var age by remember { mutableStateOf(initialAge) }; var height by remember { mutableStateOf(initialHeight) }; var weight by remember { mutableStateOf(initialWeight) }
    var goal by remember { mutableStateOf(initialGoal) }; var sex by remember { mutableStateOf(initialSex) }; var activity by remember { mutableStateOf(initialActivity) }
    val titles=listOf("Ton nouveau coach","Faisons connaissance","Définis ton cap","Prêt à démarrer")
    val done={ onFinish(age,height,weight,goal,sex,activity) }
    Dialog(onDismissRequest={done()},properties=DialogProperties(usePlatformDefaultWidth=false)){
        Box(Modifier.fillMaxSize().padding(horizontal=12.dp,vertical=16.dp).clip(RoundedCornerShape(32.dp)).background(Brush.linearGradient(listOf(Color(0xFF18252C),Color(0xFF0A1116),Color(0xFF101A20))))){
            Column(Modifier.fillMaxSize().padding(horizontal=24.dp,vertical=22.dp),verticalArrangement=Arrangement.SpaceBetween){
                Column {
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){
                        Column { Text("CALORIE COACH",color=Accent,fontSize=11.sp,fontWeight=FontWeight.Bold,letterSpacing=2.sp); Text("Bienvenue",color=Ink,fontSize=28.sp,fontWeight=FontWeight.Bold) }
                        Box(Modifier.size(56.dp).background(Brush.radialGradient(listOf(Green.copy(.32f),Green.copy(.03f))),CircleShape),contentAlignment=Alignment.Center){Text("✦",color=Green,fontSize=28.sp)}
                    }
                    Spacer(Modifier.height(20.dp))
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)){titles.indices.forEach{Box(Modifier.weight(1f).height(4.dp).clip(CircleShape).background(if(it<=step)Green else Border))}}
                    Spacer(Modifier.height(18.dp))
                    AnimatedContent(targetState=step,transitionSpec={fadeIn(tween(240))+slideInVertically(tween(240)){it/10} togetherWith fadeOut(tween(140))},label="onboarding-step"){current->
                        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(14.dp)){
                            Text(titles[current],color=Ink,fontSize=22.sp,fontWeight=FontWeight.Bold)
                            when(current){
                                0->{Text("Un suivi clair, un coach qui retient tes échanges et des séances adaptées à ton rythme.",color=Muted,fontSize=15.sp,lineHeight=23.sp);Card(colors=CardDefaults.cardColors(containerColor=Panel.copy(alpha=.92f)),shape=RoundedCornerShape(22.dp)){Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){Text("✦  Journal simple des repas et séances",color=Ink);Text("✦  Estimations photo et activité par IA",color=Ink);Text("✦  Profil et progression à portée de main",color=Ink)}}}
                                1->{Text("Ces informations servent à estimer tes besoins quotidiens. Tu pourras les modifier à tout moment.",color=Muted,fontSize=13.sp,lineHeight=19.sp);Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){OutlinedTextField(age,{age=it.filter(Char::isDigit).take(3)},label={Text("Âge")},suffix={Text("ans")},modifier=Modifier.weight(1f),singleLine=true);OutlinedTextField(height,{height=it.filter(Char::isDigit).take(3)},label={Text("Taille")},suffix={Text("cm")},modifier=Modifier.weight(1f),singleLine=true)};OutlinedTextField(weight,{weight=it.filter{ch->ch.isDigit()||ch=='.'||ch==','}},label={Text("Poids")},suffix={Text("kg")},modifier=Modifier.fillMaxWidth(),singleLine=true)}
                                2->{Text("Choisis la formule et ton activité habituelle en dehors des séances.",color=Muted,fontSize=13.sp);Text("Formule de calcul",color=Accent,fontSize=12.sp,fontWeight=FontWeight.SemiBold);Row(horizontalArrangement=Arrangement.spacedBy(5.dp)){listOf("Femme","Homme","Moyenne").forEach{FilterChip(selected=sex==it,onClick={sex=it},label={Text(it,fontSize=11.sp)})}};Text("Activité hors sport",color=Accent,fontSize=12.sp,fontWeight=FontWeight.SemiBold);Column{listOf("Sédentaire","Léger","Modéré","Très actif").chunked(2).forEach{row->Row(horizontalArrangement=Arrangement.spacedBy(5.dp)){row.forEach{FilterChip(selected=activity==it,onClick={activity=it},label={Text(it,fontSize=11.sp)})}}}};Text("Ton objectif",color=Accent,fontSize=12.sp,fontWeight=FontWeight.SemiBold);Column{listOf("Perte de poids","Maintien","Prise de muscle").forEach{FilterChip(selected=goal==it,onClick={goal=it},label={Text(it,fontSize=12.sp)})}}}
                                else->{Text("Ton profil est enregistré sur cet appareil. La cible calorique sera suggérée dès que les données nécessaires sont complètes.",color=Muted,fontSize=14.sp,lineHeight=22.sp);Card(colors=CardDefaults.cardColors(containerColor=Panel),shape=RoundedCornerShape(22.dp)){Column(Modifier.padding(18.dp)){Text("Tu gardes le contrôle",color=Green,fontWeight=FontWeight.Bold);Text("Les calories affichées sont des estimations. Tu peux ajuster ta cible dans Réglages.",color=Muted,fontSize=13.sp,lineHeight=19.sp,modifier=Modifier.padding(top=6.dp))}}}
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp),verticalAlignment=Alignment.CenterVertically){
                    if(step>0)TextButton(onClick={step--}){Text("Retour",color=Muted)}
                    TextButton(onClick={done}){Text("Plus tard",color=Muted)}
                    Button(onClick={if(step<titles.lastIndex)step++ else done()},modifier=Modifier.weight(1f),shape=RoundedCornerShape(16.dp)){Text(if(step<titles.lastIndex)"Continuer" else "C’est parti")}
                }
            }
        }
    }
}
