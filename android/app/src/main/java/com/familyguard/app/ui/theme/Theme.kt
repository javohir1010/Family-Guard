package com.familyguard.app.ui.theme

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

// Палитра варианта A «Спокойствие» (макеты в треде «Дизайн приложения и темы»)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF2F6FEB),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE8EEF8),
    onPrimaryContainer = Color(0xFF2558C9),
    secondary = Color(0xFF2558C9),
    onSecondary = Color.White,
    background = Color(0xFFF4F6FA),
    onBackground = Color(0xFF0F172A),
    surface = Color.White,
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFE8EEF8),
    onSurfaceVariant = Color(0xFF556070),
    outline = Color(0xFFCBD3DF),
    outlineVariant = Color(0xFFDDE3EC),
    error = Color(0xFFD93A3F),
    onError = Color.White,
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF6E9BFF),
    onPrimary = Color(0xFF0B1020),
    primaryContainer = Color(0xFF1E2740),
    onPrimaryContainer = Color(0xFF8EB2FF),
    secondary = Color(0xFF8EB2FF),
    onSecondary = Color(0xFF0B1020),
    background = Color(0xFF0B1020),
    onBackground = Color(0xFFE8ECF5),
    surface = Color(0xFF151B2E),
    onSurface = Color(0xFFE8ECF5),
    surfaceVariant = Color(0xFF1E2740),
    onSurfaceVariant = Color(0xFF9AA3B5),
    outline = Color(0xFF3A4662),
    outlineVariant = Color(0xFF263049),
    error = Color(0xFFFF5D62),
    onError = Color(0xFF1A0506),
)

/** Цвета, которых нет в Material ColorScheme: SOS и статусы. */
@Immutable
data class FgColors(
    val sos: Color,
    val onSos: Color,
    val sosSoft: Color,
    val ok: Color,
    val okSoft: Color,
    val warn: Color,
    val warnSoft: Color,
)

private val LightFg = FgColors(
    sos = Color(0xFFD93A3F), onSos = Color.White, sosSoft = Color(0xFFFBE3E4),
    ok = Color(0xFF17794A), okSoft = Color(0xFFDDF3E7),
    warn = Color(0xFFA15C00), warnSoft = Color(0xFFFDF0DC),
)

private val DarkFg = FgColors(
    sos = Color(0xFFFF5D62), onSos = Color(0xFF1A0506), sosSoft = Color(0xFF3A1A20),
    ok = Color(0xFF4FD08B), okSoft = Color(0xFF12301F),
    warn = Color(0xFFF5B451), warnSoft = Color(0xFF3A2A10),
)

val LocalFgColors = staticCompositionLocalOf { LightFg }

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Выбор темы пользователя; хранится локально на устройстве. */
object ThemePrefs {
    private const val PREFS = "fg_ui"
    private const val KEY = "theme_mode"
    private var prefs: SharedPreferences? = null

    var mode by mutableStateOf(ThemeMode.SYSTEM)
        private set

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        mode = runCatching { ThemeMode.valueOf(p.getString(KEY, null) ?: "") }
            .getOrDefault(ThemeMode.SYSTEM)
    }

    fun set(value: ThemeMode) {
        mode = value
        prefs?.edit()?.putString(KEY, value.name)?.apply()
    }
}

private val FgShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
)

@Composable
fun FamilyGuardTheme(content: @Composable () -> Unit) {
    val dark = when (ThemePrefs.mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val scheme = if (dark) DarkScheme else LightScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            @Suppress("DEPRECATION")
            window.statusBarColor = scheme.background.toArgb()
            @Suppress("DEPRECATION")
            window.navigationBarColor = scheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }

    CompositionLocalProvider(LocalFgColors provides if (dark) DarkFg else LightFg) {
        MaterialTheme(colorScheme = scheme, shapes = FgShapes, content = content)
    }
}
