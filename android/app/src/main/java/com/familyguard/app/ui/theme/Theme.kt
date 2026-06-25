package com.familyguard.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val FamilyGuardColors = darkColorScheme(
    primary        = Color(0xFF2DDAB6),
    onPrimary      = Color(0xFF0D1B2A),
    primaryContainer    = Color(0xFF132235),
    secondary      = Color(0xFF00C3FF),
    onSecondary    = Color(0xFF0D1B2A),
    background     = Color(0xFF0D1B2A),
    onBackground   = Color.White,
    surface        = Color(0xFF132235),
    onSurface      = Color.White,
    error          = Color(0xFFFF4D6D),
    onError        = Color.White,
)

@Composable
fun FamilyGuardTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = FamilyGuardColors,
        content = content
    )
}
