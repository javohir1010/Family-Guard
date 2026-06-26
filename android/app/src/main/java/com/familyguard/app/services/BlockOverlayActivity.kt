package com.familyguard.app.services

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.familyguard.app.receivers.DeviceAdminReceiver
import com.familyguard.app.ui.theme.FamilyGuardTheme

/**
 * Full-screen overlay shown when a child tries to open a blocked app.
 *
 * Two levels of blocking:
 *
 * Level 1 (Accessibility Service — always available):
 *   → Show this overlay activity on top of the blocked app
 *   → Navigate home when back is pressed
 *   → Re-trigger if child tries to return to the app
 *
 * Level 2 (Device Policy Manager — if Device Admin is active):
 *   → setApplicationHidden() makes the app completely invisible
 *   → Cannot be launched at all — no overlay needed
 *   → More reliable but requires explicit user grant on first install
 */
class BlockOverlayActivity : ComponentActivity() {

    companion object {
        const val EXTRA_PACKAGE = "package_name"
        const val EXTRA_APP_NAME = "app_name"
        const val EXTRA_LIMIT_MINUTES = "limit_minutes"

        fun createIntent(
            context: Context,
            packageName: String,
            appName: String,
            limitMinutes: Int = -1
        ): Intent = Intent(context, BlockOverlayActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_PACKAGE, packageName)
            putExtra(EXTRA_APP_NAME, appName)
            putExtra(EXTRA_LIMIT_MINUTES, limitMinutes)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Make the window fill the entire screen including status bar
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val packageName  = intent.getStringExtra(EXTRA_PACKAGE) ?: "Приложение"
        val appName      = intent.getStringExtra(EXTRA_APP_NAME) ?: packageName
        val limitMinutes = intent.getIntExtra(EXTRA_LIMIT_MINUTES, -1)

        // Override back button — go HOME, not to the blocked app
        onBackPressedDispatcher.addCallback(this) {
            goHome()
        }

        setContent {
            FamilyGuardTheme {
                BlockOverlayScreen(
                    appName = appName,
                    limitMinutes = limitMinutes,
                    onGoHome = { goHome() }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // App was re-launched while overlay is visible → stay on overlay
    }

    /**
     * Navigate to home screen instead of returning to the blocked app.
     */
    private fun goHome() {
        startActivity(
            Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        )
        finish()
    }

    /**
     * Attempt Level 2 blocking via DevicePolicyManager.
     * If Device Admin is active, hides the app completely from launcher.
     * Falls back to Level 1 (overlay) if DPM is not available.
     */
    fun tryHideAppWithDPM(packageName: String) {
        try {
            val dpm = getSystemService(DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val adminComponent = ComponentName(this, DeviceAdminReceiver::class.java)
            if (dpm.isAdminActive(adminComponent)) {
                // setApplicationHidden is available on API 21+ with Device Admin
                dpm.setApplicationHidden(adminComponent, packageName, true)
            }
        } catch (e: Exception) {
            // Device Admin not active — overlay approach still works
        }
    }
}

@Composable
private fun BlockOverlayScreen(
    appName: String,
    limitMinutes: Int,
    onGoHome: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D1B2A)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp)
        ) {
            // Big block icon
            Text(text = "🚫", fontSize = 72.sp)

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "$appName\nзаблокировано",
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                lineHeight = 34.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            val reason = if (limitMinutes > 0) {
                "Дневной лимит ($limitMinutes мин) исчерпан"
            } else {
                "Это приложение ограничено родителем"
            }

            Text(
                text = reason,
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Info card
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF1A2F45),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Родительский контроль",
                        color = Color.White.copy(alpha = 0.4f),
                        fontSize = 12.sp,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Обратитесь к родителю для\nвременного разрешения",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Go home button
            Button(
                onClick = onGoHome,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF1C3045)
                )
            ) {
                Text(
                    text = "← На главный экран",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
