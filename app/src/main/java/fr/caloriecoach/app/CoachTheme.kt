package fr.caloriecoach.app

import android.content.Context
import android.app.Activity
import android.view.View
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class CoachTheme(val id: String, val title: String, val description: String) {
    MIDNIGHT("minuit", "Minuit", "Bleu nuit, sauge et lavande"),
    FOREST("foret", "Forêt", "Vert profond et reflets dorés"),
    OCEAN("ocean", "Océan", "Bleu abyssal et écume turquoise"),
    DUSK("crepuscule", "Crépuscule", "Prune, lilas et rose poudré"),
    DAWN("aube", "Aube", "Un thème clair, ivoire et terracotta");

    fun colors(): ColorScheme = when (this) {
        MIDNIGHT -> night(0xFF0B1018, 0xFF141D29, 0xFF1B2735, 0xFF94C9B2, 0xFFABBAE8, 0xFFE7F0F2, 0xFFA5B3C0, 0xFF3C4C5D)
        FOREST -> night(0xFF0D1510, 0xFF17231B, 0xFF233229, 0xFFAFCE9B, 0xFFE2C692, 0xFFEAF1E7, 0xFFA9B9A9, 0xFF425446)
        OCEAN -> night(0xFF07151E, 0xFF102431, 0xFF1B3443, 0xFF8FD7DE, 0xFFABBFF5, 0xFFE5F3F6, 0xFFA2B8C5, 0xFF3C586B)
        DUSK -> night(0xFF18111F, 0xFF271D31, 0xFF372940, 0xFFD0B2ED, 0xFFF0B7C3, 0xFFF5ECF7, 0xFFBFAEC9, 0xFF5E496A)
        DAWN -> lightColorScheme(
            primary=Color(0xFF8A472D), onPrimary=Color.White,
            primaryContainer=Color(0xFFF5D9C8), onPrimaryContainer=Color(0xFF48210F),
            secondary=Color(0xFF52694D), onSecondary=Color.White,
            secondaryContainer=Color(0xFFDFE9D9), onSecondaryContainer=Color(0xFF243B20),
            background=Color(0xFFFAF7F1), onBackground=Color(0xFF29251F),
            surface=Color(0xFFFFFCF6), onSurface=Color(0xFF29251F),
            surfaceVariant=Color(0xFFF0E9DF), onSurfaceVariant=Color(0xFF655D53),
            surfaceDim=Color(0xFFE9E0D4), surfaceBright=Color(0xFFFFFCF6),
            surfaceContainerLowest=Color(0xFFFFFFFF), surfaceContainerLow=Color(0xFFFFFCF6),
            surfaceContainer=Color(0xFFF7F0E7), surfaceContainerHigh=Color(0xFFF0E9DF), surfaceContainerHighest=Color(0xFFE9E0D4),
            outline=Color(0xFF998B7C), outlineVariant=Color(0xFFD6CBBE)
        )
    }

    companion object {
        fun fromId(id: String?) = entries.firstOrNull { it.id == id } ?: MIDNIGHT
        private fun night(bg: Long, panel: Long, raised: Long, accent: Long, second: Long, text: Long, muted: Long, border: Long) = darkColorScheme(
            primary=Color(accent), onPrimary=Color(0xFF142019),
            primaryContainer=Color(raised), onPrimaryContainer=Color(accent),
            secondary=Color(second), onSecondary=Color(0xFF201B25),
            secondaryContainer=Color(raised), onSecondaryContainer=Color(second),
            background=Color(bg), onBackground=Color(text),
            surface=Color(panel), onSurface=Color(text),
            surfaceVariant=Color(raised), onSurfaceVariant=Color(muted),
            surfaceDim=Color(bg), surfaceBright=Color(raised),
            surfaceContainerLowest=Color(bg), surfaceContainerLow=Color(panel),
            surfaceContainer=Color(panel), surfaceContainerHigh=Color(raised), surfaceContainerHighest=Color(raised),
            outline=Color(border), outlineVariant=Color(border)
        )
    }
}

object CoachThemeStore {
    fun load(context: Context) = CoachTheme.fromId(context.getSharedPreferences("coach_appearance", 0).getString("theme", null))
    fun save(context: Context, theme: CoachTheme) { context.getSharedPreferences("coach_appearance", 0).edit().putString("theme", theme.id).apply() }
}

private data class ThemeSelection(val theme: CoachTheme, val select: (CoachTheme) -> Unit)
private val LocalThemeSelection = staticCompositionLocalOf { ThemeSelection(CoachTheme.MIDNIGHT, {}) }

@Composable fun CoachThemeRoot(content: @Composable () -> Unit) {
    val context = LocalContext.current
    var choice by remember { mutableStateOf(CoachThemeStore.load(context)) }
    val target = choice.colors()
    SideEffect {
        (context as? Activity)?.window?.let { window ->
            window.statusBarColor=target.background.toArgb()
            window.navigationBarColor=target.background.toArgb()
            val lightFlags=View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            window.decorView.systemUiVisibility=if(choice==CoachTheme.DAWN) window.decorView.systemUiVisibility or lightFlags else window.decorView.systemUiVisibility and lightFlags.inv()
        }
    }
    @Composable fun transition(color: Color): Color = animateColorAsState(color, tween(240), label="theme-color").value
    val scheme = target.copy(
        primary=transition(target.primary), onPrimary=transition(target.onPrimary),
        secondary=transition(target.secondary), background=transition(target.background),
        onBackground=transition(target.onBackground), surface=transition(target.surface),
        onSurface=transition(target.onSurface), surfaceVariant=transition(target.surfaceVariant),
        onSurfaceVariant=transition(target.onSurfaceVariant), outline=transition(target.outline)
    )
    CompositionLocalProvider(LocalThemeSelection provides ThemeSelection(choice) { next ->
        choice = next
        CoachThemeStore.save(context, next)
    }) { MaterialTheme(colorScheme=scheme, content=content) }
}

@Composable fun ThemeSettingsCard() {
    val selection = LocalThemeSelection.current
    val colors = MaterialTheme.colorScheme
    Card(shape=RoundedCornerShape(24.dp), colors=CardDefaults.cardColors(containerColor=colors.surface), border=BorderStroke(1.dp, colors.outline)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Text("Apparence", fontWeight=FontWeight.Bold, color=colors.onSurface)
            Text("Choisis ton ambiance. Elle s’applique à toute l’app et reste mémorisée sur cet appareil.", color=colors.onSurfaceVariant, fontSize=12.sp)
            Column(Modifier.selectableGroup(), verticalArrangement=Arrangement.spacedBy(8.dp)) {
                CoachTheme.entries.forEach { theme ->
                    val selected = selection.theme == theme
                    val preview = theme.colors()
                    Surface(
                        selected=selected, onClick={selection.select(theme)},
                        shape=RoundedCornerShape(16.dp),
                        color=if(selected) colors.surfaceVariant else colors.surface,
                        border=BorderStroke(if(selected) 2.dp else 1.dp, if(selected) colors.primary else colors.outline),
                        modifier=Modifier.fillMaxWidth().testTag("theme_${theme.id}").semantics { role=Role.RadioButton }
                    ) {
                        Row(Modifier.padding(horizontal=10.dp, vertical=12.dp), verticalAlignment=Alignment.CenterVertically) {
                            Box(Modifier.size(38.dp).background(preview.background, RoundedCornerShape(12.dp)), contentAlignment=Alignment.Center) {
                                Row(horizontalArrangement=Arrangement.spacedBy(3.dp)) {
                                    Box(Modifier.size(10.dp).background(preview.primary, CircleShape))
                                    Box(Modifier.size(10.dp).background(preview.secondary, CircleShape))
                                }
                            }
                            Column(Modifier.weight(1f).padding(start=12.dp)) {
                                Text(theme.title, color=colors.onSurface, fontWeight=FontWeight.SemiBold, fontSize=14.sp)
                                Text(theme.description, color=colors.onSurfaceVariant, fontSize=11.sp)
                            }
                            RadioButton(selected=selected, onClick=null)
                        }
                    }
                }
            }
        }
    }
}
