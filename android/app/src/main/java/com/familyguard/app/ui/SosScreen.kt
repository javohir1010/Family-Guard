package com.familyguard.app.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.familyguard.app.viewmodel.SosViewModel
import kotlinx.coroutines.delay

@Composable
fun SosScreen(
    viewModel: SosViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val haptic = LocalHapticFeedback.current

    // Pulse animation for the SOS button
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    // Press scale animation
    var isPressed by remember { mutableStateOf(false) }
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.93f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "pressScale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A1A)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(32.dp)
        ) {
            // Status indicator
            StatusIndicator(isConnected = uiState.isConnected)

            // App title
            Text(
                text = "FamilyGuard",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 3.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            // SOS Button
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(220.dp)
            ) {
                // Outer ring (pulse)
                if (!uiState.isSending) {
                    Box(
                        modifier = Modifier
                            .size(220.dp)
                            .scale(pulseScale)
                            .background(
                                Color(0xFFDC2626).copy(alpha = 0.15f),
                                CircleShape
                            )
                    )
                }

                // Middle ring
                Box(
                    modifier = Modifier
                        .size(190.dp)
                        .background(
                            Color(0xFFDC2626).copy(alpha = 0.25f),
                            CircleShape
                        )
                )

                // Inner button
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(160.dp)
                        .scale(pressScale)
                        .background(
                            if (uiState.isSending) Color(0xFF991B1B) else Color(0xFFDC2626),
                            CircleShape
                        )
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onPress = {
                                    isPressed = true
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    tryAwaitRelease()
                                    isPressed = false
                                },
                                onTap = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    viewModel.onSosPressed()
                                }
                            )
                        }
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (uiState.isSending) "..." else "SOS",
                            color = Color.White,
                            fontSize = 42.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 4.sp
                        )
                        if (uiState.pressCount > 0) {
                            Text(
                                text = "${uiState.pressCount}",
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Instruction
            Text(
                text = when {
                    uiState.isSending -> "Отправка сигнала..."
                    uiState.pressCount >= 8 -> "🚨 ЭКСТРЕННЫЙ СИГНАЛ"
                    uiState.pressCount > 0 -> "Нажмите ещё раз или подождите"
                    else -> "Нажмите для отправки сигнала"
                },
                color = when {
                    uiState.pressCount >= 8 -> Color(0xFFFF6B6B)
                    uiState.pressCount > 0 -> Color(0xFFFBBF24)
                    else -> Color.White.copy(alpha = 0.5f)
                },
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                fontWeight = if (uiState.pressCount >= 8) FontWeight.Bold else FontWeight.Normal
            )

            // Last sent
            uiState.lastSentTime?.let { time ->
                Text(
                    text = "Последний сигнал: $time",
                    color = Color.White.copy(alpha = 0.3f),
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
private fun StatusIndicator(isConnected: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .background(
                if (isConnected) Color(0xFF064E3B) else Color(0xFF1F2937),
                MaterialTheme.shapes.medium
            )
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Blinking dot
        val infiniteTransition = rememberInfiniteTransition(label = "blink")
        val alpha by infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 0.2f,
            animationSpec = infiniteRepeatable(
                animation = tween(800),
                repeatMode = RepeatMode.Reverse
            ),
            label = "blink"
        )

        Box(
            modifier = Modifier
                .size(8.dp)
                .background(
                    if (isConnected) Color(0xFF10B981).copy(alpha = alpha)
                    else Color(0xFF6B7280),
                    CircleShape
                )
        )
        Text(
            text = if (isConnected) "Защита активна" else "Нет соединения",
            color = if (isConnected) Color(0xFF10B981) else Color(0xFF9CA3AF),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
