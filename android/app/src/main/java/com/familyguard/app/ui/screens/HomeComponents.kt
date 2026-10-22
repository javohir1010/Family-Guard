package com.familyguard.app.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.familyguard.app.R
import com.familyguard.app.ui.theme.LocalFgColors
import com.familyguard.app.ui.theme.ThemeMode
import com.familyguard.app.ui.theme.ThemePrefs
import com.familyguard.app.viewmodel.SosUiState
import java.time.Duration
import java.time.OffsetDateTime

/** Card used on both home screens: surface, thin outline, 20dp corners. */
@Composable
fun FgCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = MaterialTheme.shapes.large
    val border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    val colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    val inner: @Composable ColumnScope.() -> Unit = {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content
        )
    }
    if (onClick != null) {
        Card(onClick = onClick, modifier = modifier.fillMaxWidth(), shape = shape, border = border, colors = colors, content = inner)
    } else {
        Card(modifier = modifier.fillMaxWidth(), shape = shape, border = border, colors = colors, content = inner)
    }
}

@Composable
fun Avatar(letter: String, size: Dp = 44.dp) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size)
            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
    ) {
        Text(
            text = letter.take(1).uppercase().ifEmpty { "?" },
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            fontWeight = FontWeight.ExtraBold,
            fontSize = (size.value * 0.4f).sp
        )
    }
}

@Composable
fun StatusChip(text: String, good: Boolean) {
    val fg = LocalFgColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .background(if (good) fg.okSoft else MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Box(
            Modifier
                .size(8.dp)
                .background(if (good) fg.ok else MaterialTheme.colorScheme.onSurfaceVariant, CircleShape)
        )
        Text(
            text = text,
            color = if (good) fg.ok else MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/** Small coloured pill, e.g. battery level. */
@Composable
fun Pill(text: String, warn: Boolean) {
    val fg = LocalFgColors.current
    Text(
        text = text,
        color = if (warn) fg.warn else fg.ok,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .background(if (warn) fg.warnSoft else fg.okSoft, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onBackground,
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 6.dp)
    )
}

/**
 * Round SOS button. Every tap is one press; the view model counts presses
 * and decides when to send.
 */
@Composable
fun SosButton(state: SosUiState, size: Dp, onPress: () -> Unit) {
    val fg = LocalFgColors.current
    val haptic = LocalHapticFeedback.current

    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 1f,
        targetValue = 1.07f,
        animationSpec = infiniteRepeatable(tween(1000, easing = EaseInOut), RepeatMode.Reverse),
        label = "pulseScale"
    )
    var pressed by remember { mutableStateOf(false) }
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.93f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "pressScale"
    )

    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(size)) {
        Box(
            Modifier
                .size(size)
                .scale(if (state.isSending) 1f else pulse)
                .background(fg.sosSoft, CircleShape)
        )
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(size * 0.78f)
                .scale(pressScale)
                .clip(CircleShape)
                .background(if (state.isSending) fg.sos.copy(alpha = 0.7f) else fg.sos)
                .semantics {
                    role = Role.Button
                    contentDescription = "Отправить сигнал SOS"
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            pressed = true
                            tryAwaitRelease()
                            pressed = false
                        },
                        onTap = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onPress()
                        }
                    )
                }
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (state.isSending) "…" else "SOS",
                    color = fg.onSos,
                    fontSize = (size.value * 0.2f).sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 3.sp
                )
                if (state.pressCount > 0) {
                    Text(
                        text = "${state.pressCount}",
                        color = fg.onSos.copy(alpha = 0.85f),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/** One line under the SOS button: what happens now, then the last result. */
@Composable
fun SosStatusText(state: SosUiState) {
    val fg = LocalFgColors.current
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = when {
                state.isSending -> "Отправляем сигнал…"
                state.pressCount >= 8 -> "Экстренный сигнал"
                state.pressCount > 0 -> "Нажмите ещё раз или подождите"
                else -> "Нажмите, чтобы отправить сигнал родителям"
            },
            color = when {
                state.pressCount >= 8 -> fg.sos
                state.pressCount > 0 -> fg.warn
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            fontSize = 14.sp,
            fontWeight = if (state.pressCount >= 8) FontWeight.Bold else FontWeight.Normal,
            textAlign = TextAlign.Center
        )
        state.lastSentTime?.let {
            Text("Последний сигнал отправлен в $it", color = fg.ok, fontSize = 13.sp)
        }
        state.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp, textAlign = TextAlign.Center)
        }
    }
}

/** Light / system / dark segmented switch. */
@Composable
fun ThemeSwitcher(modifier: Modifier = Modifier) {
    val options = listOf(
        ThemeMode.LIGHT to "Светлая",
        ThemeMode.SYSTEM to "Как в системе",
        ThemeMode.DARK to "Тёмная"
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
            .padding(4.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        options.forEach { (mode, label) ->
            val selected = ThemePrefs.mode == mode
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (selected) MaterialTheme.colorScheme.surface else Color.Transparent)
                    .selectable(selected = selected, role = Role.RadioButton) { ThemePrefs.set(mode) }
            ) {
                Text(
                    text = label,
                    fontSize = 13.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** Quiet outlined pill with an icon, used instead of the old door emoji. */
@Composable
fun LogoutButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 44.dp),
        shape = RoundedCornerShape(50),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
        contentPadding = PaddingValues(horizontal = 16.dp)
    ) {
        Icon(painterResource(R.drawable.ic_logout), contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("Выйти из аккаунта", fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

/** "5 мин назад" for an ISO timestamp from the API. */
fun timeAgo(iso: String): String {
    val then = runCatching { OffsetDateTime.parse(iso) }.getOrNull() ?: return ""
    val minutes = Duration.between(then, OffsetDateTime.now()).toMinutes()
    return when {
        minutes < 1 -> "только что"
        minutes < 60 -> "$minutes мин назад"
        minutes < 24 * 60 -> "${minutes / 60} ч назад"
        else -> "${minutes / (24 * 60)} дн назад"
    }
}
