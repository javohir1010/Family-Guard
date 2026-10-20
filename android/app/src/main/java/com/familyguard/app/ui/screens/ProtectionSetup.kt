package com.familyguard.app.ui.screens

import android.Manifest
import android.app.Activity
import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.Process
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.familyguard.app.services.AppMonitorAccessibilityService
import com.familyguard.app.services.DnsVpnService
import com.familyguard.app.services.LocationForegroundService
import com.familyguard.app.services.UsageStatsSyncWorker

private const val PREFS = "fg_setup"
private const val KEY_VPN_ENABLED = "vpn_enabled"

data class ProtectionStatus(
    val location: Boolean,
    val usageAccess: Boolean,
    val accessibility: Boolean,
    val dnsFilter: Boolean,
) {
    val done get() = listOf(location, usageAccess, accessibility, dnsFilter).count { it }
}

fun readProtectionStatus(context: Context) = ProtectionStatus(
    location = LocationForegroundService.hasLocationPermission(context),
    usageAccess = hasUsageAccess(context),
    accessibility = isAccessibilityServiceEnabled(context),
    dnsFilter = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getBoolean(KEY_VPN_ENABLED, false) && VpnService.prepare(context) == null,
)

private fun hasUsageAccess(context: Context): Boolean {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
    } else {
        @Suppress("DEPRECATION")
        appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
    }
    return mode == AppOpsManager.MODE_ALLOWED
}

private fun isAccessibilityServiceEnabled(context: Context): Boolean {
    val enabled = Settings.Secure.getString(
        context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: return false
    val ours = ComponentName(context, AppMonitorAccessibilityService::class.java)
    return enabled.split(':').any { ComponentName.unflattenFromString(it) == ours }
}

/**
 * Asks for location (+ notifications on Android 13+) once, then starts the
 * location service and the usage sync. Also restarts the DNS filter if the
 * user enabled it before. Returns the current status, refreshed on resume.
 */
@Composable
fun rememberProtectionSetup(): MutableState<ProtectionStatus> {
    val context = LocalContext.current
    val status = remember { mutableStateOf(readProtectionStatus(context)) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        LocationForegroundService.startService(context)
        status.value = readProtectionStatus(context)
    }

    LaunchedEffect(Unit) {
        UsageStatsSyncWorker.schedule(context)
        if (status.value.dnsFilter) DnsVpnService.startVpn(context)
        val permissions = buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (status.value.location) {
            LocationForegroundService.startService(context)
        } else {
            permissionLauncher.launch(permissions.toTypedArray())
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                status.value = readProtectionStatus(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return status
}

/** Dialog listing the permissions protection needs, each with a button to grant it. */
@Composable
fun ProtectionSetupDialog(status: ProtectionStatus, onChanged: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current

    val vpnLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) enableDnsFilter(context)
        onChanged()
    }
    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        if (granted.values.any { it }) {
            LocationForegroundService.startService(context)
        } else {
            // Denied twice: Android won't show the dialog again, open app settings
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
            )
        }
        onChanged()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Готово") } },
        title = { Text("Настройка защиты") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SetupRow("Геолокация", "Родитель видит устройство на карте", status.location) {
                    locationLauncher.launch(
                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                    )
                }
                SetupRow("Статистика приложений", "Найдите FamilyGuard и включите доступ", status.usageAccess) {
                    context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                }
                SetupRow("Блокировка приложений", "Спец. возможности → FamilyGuard → Вкл.", status.accessibility) {
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
                SetupRow("DNS-фильтр сайтов", "Подтвердите подключение VPN", status.dnsFilter) {
                    val intent = VpnService.prepare(context)
                    if (intent != null) vpnLauncher.launch(intent) else {
                        enableDnsFilter(context)
                        onChanged()
                    }
                }
            }
        }
    )
}

private fun enableDnsFilter(context: Context) {
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .edit().putBoolean(KEY_VPN_ENABLED, true).apply()
    DnsVpnService.startVpn(context)
}

@Composable
private fun SetupRow(title: String, hint: String, done: Boolean, onEnable: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp)
            if (!done) Text(hint, fontSize = 12.sp, color = Color.Gray)
        }
        if (done) {
            Text("✅", fontSize = 18.sp)
        } else {
            TextButton(onClick = onEnable) { Text("Включить") }
        }
    }
}
