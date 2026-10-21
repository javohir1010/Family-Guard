package com.familyguard.app.services

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.telecom.TelecomManager
import android.view.accessibility.AccessibilityEvent
import com.familyguard.app.data.api.ApiService
import com.familyguard.app.data.model.AppRule
import com.familyguard.app.data.model.ScreenSchedule
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import java.util.Calendar
import javax.inject.Inject

/**
 * App monitoring and blocking via Android Accessibility Service.
 *
 * HOW IT WORKS:
 * ─────────────────────────────────────────────────────────────────
 * Level 1 (Accessibility Service):
 *   • Intercepts TYPE_WINDOW_STATE_CHANGED events
 *   • Detects which app moved to foreground
 *   • If blocked → launches BlockOverlayActivity on top
 *   • Limitation: some Chinese ROMs don't fire this event reliably
 *
 * Level 2 (UsageStatsManager fallback):
 *   • Polls foreground app every 500ms via UsageStats
 *   • More reliable across all devices and Android versions
 *   • Requires PACKAGE_USAGE_STATS permission (manual grant in settings)
 *
 * Both levels work together for maximum reliability.
 * ─────────────────────────────────────────────────────────────────
 *
 * LIMITATIONS (honest assessment for diploma):
 *   ✅ Works on stock Android (Pixel, Sony, Nokia, Motorola)
 *   ✅ Works on Samsung One UI 4+
 *   ⚠️ May be killed on Xiaomi MIUI without battery optimization exclusion
 *   ⚠️ User must enable manually in Settings → Accessibility
 *   ❌ A determined child can disable Accessibility in Settings
 *      (→ parent gets notified via permission monitoring)
 *
 * FOR STRONGER BLOCKING: combine with DevicePolicyManager
 * (setApplicationHidden) — see BlockOverlayActivity.tryHideAppWithDPM()
 */
@AndroidEntryPoint
class AppMonitorAccessibilityService : AccessibilityService() {

    @Inject lateinit var apiService: ApiService

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    @Volatile private var activeRules: List<AppRule> = emptyList()
    @Volatile private var schedules: List<ScreenSchedule> = emptyList()

    // Track today's usage per app: packageName → used seconds today
    // Replaced as a whole on refresh, so yesterday's totals never linger
    @Volatile private var todayUsageSeconds: Map<String, Long> = emptyMap()

    // Debounce: don't re-show overlay for the same app within 3 seconds
    private var lastBlockedPackage = ""
    private var lastBlockedTime = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()

        serviceInfo = AccessibilityServiceInfo().apply {
            // Listen for window focus changes (app switches)
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                         AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
            notificationTimeout = 100
        }

        serviceScope.launch {
            refreshRules()
            refreshTodayUsage()
        }

        // Periodic rule + schedule refresh every 5 min (parent changes apply quickly)
        serviceScope.launch {
            while (isActive) {
                delay(5 * 60 * 1000L)
                refreshRules()
            }
        }

        // Periodic usage refresh every minute (limits are checked against it)
        serviceScope.launch {
            while (isActive) {
                delay(60 * 1000L)
                refreshTodayUsage()
            }
        }

        // Level 2: UsageStats polling fallback (every 500ms)
        // More reliable than accessibility events on some devices
        serviceScope.launch {
            while (isActive) {
                delay(500)
                val foreground = getForegroundAppViaUsageStats()
                if (foreground != null && foreground != packageName) {
                    checkAndBlock(foreground)
                }
            }
        }
    }

    // Level 1: Accessibility event (instant, ~0ms latency)
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName) return // Don't block ourselves

        serviceScope.launch {
            checkAndBlock(pkg)
        }
    }

    private suspend fun checkAndBlock(packageName: String) {
        val now = System.currentTimeMillis()

        // Screen schedule: everything except essentials is locked during the window
        val schedule = currentSchedule()
        if (schedule != null && !isAlwaysAllowed(packageName)) {
            if (packageName == lastBlockedPackage && now - lastBlockedTime < 3000) return
            lastBlockedPackage = packageName
            lastBlockedTime = now
            val reason = "Сейчас «${schedule.name}»: телефон на паузе до ${schedule.endTime.take(5)}"
            showBlockOverlay(packageName, appLabel(packageName), limitMinutes = -1, reason = reason)
            return
        }

        val rule = activeRules.find { it.packageName == packageName && it.isActive }
            ?: return

        when (rule.ruleType) {
            "block" -> {
                // Debounce: same app within 3 seconds → skip
                if (packageName == lastBlockedPackage && now - lastBlockedTime < 3000) return
                lastBlockedPackage = packageName
                lastBlockedTime = now
                showBlockOverlay(packageName, rule.appName ?: packageName, limitMinutes = -1)
            }
            "limit" -> {
                val limitMinutes = rule.dailyLimitMinutes ?: return
                val usedSeconds = todayUsageSeconds[packageName] ?: 0L
                val usedMinutes = usedSeconds / 60

                if (usedMinutes >= limitMinutes) {
                    if (packageName == lastBlockedPackage && now - lastBlockedTime < 3000) return
                    lastBlockedPackage = packageName
                    lastBlockedTime = now
                    showBlockOverlay(packageName, rule.appName ?: packageName, limitMinutes)
                }
            }
        }
    }

    private fun showBlockOverlay(
        packageName: String,
        appName: String,
        limitMinutes: Int,
        reason: String? = null
    ) {
        val intent = BlockOverlayActivity.createIntent(this, packageName, appName, limitMinutes, reason)
        startActivity(intent)
    }

    /**
     * Active schedule covering the current local time, or null.
     * Days use the backend bitmask (Mon=1 … Sun=64). A window whose end is
     * before its start runs past midnight and belongs to the day it started.
     */
    private fun currentSchedule(): ScreenSchedule? {
        val cal = Calendar.getInstance()
        val nowMin = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val todayBit = 1 shl ((cal.get(Calendar.DAY_OF_WEEK) + 5) % 7) // Mon=0 … Sun=6
        val yesterdayBit = if (todayBit == 1) 64 else todayBit shr 1

        return schedules.firstOrNull { s ->
            val start = minutesOf(s.startTime) ?: return@firstOrNull false
            val end = minutesOf(s.endTime) ?: return@firstOrNull false
            if (!s.isActive) return@firstOrNull false
            if (start < end) {
                (s.daysBitmask and todayBit) != 0 && nowMin in start until end
            } else {
                ((s.daysBitmask and todayBit) != 0 && nowMin >= start) ||
                    ((s.daysBitmask and yesterdayBit) != 0 && nowMin < end)
            }
        }
    }

    private fun minutesOf(time: String): Int? {
        val parts = time.split(":")
        val h = parts.getOrNull(0)?.toIntOrNull() ?: return null
        val m = parts.getOrNull(1)?.toIntOrNull() ?: return null
        return h * 60 + m
    }

    /** Apps that stay usable during a schedule: SOS (this app), calls, home screen, keyboard. */
    private fun isAlwaysAllowed(pkg: String): Boolean {
        if (pkg == packageName || pkg in ALWAYS_ALLOWED) return true
        val tm = getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
        if (pkg == tm?.defaultDialerPackage) return true
        val ime = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
        if (ime != null && pkg == ime.substringBefore('/')) return true
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return packageManager.queryIntentActivities(home, 0)
            .any { it.activityInfo.packageName == pkg }
    }

    private fun appLabel(pkg: String): String = try {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    } catch (e: Exception) {
        pkg
    }

    /**
     * Level 2: Get current foreground app via UsageStatsManager.
     * More reliable on some devices where accessibility events are unreliable.
     * Requires PACKAGE_USAGE_STATS permission.
     */
    private fun getForegroundAppViaUsageStats(): String? {
        return try {
            val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val now = System.currentTimeMillis()
            val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 5000, now)
            stats?.maxByOrNull { it.lastTimeUsed }?.packageName
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun refreshRules() {
        try {
            activeRules = apiService.getActiveRules()
        } catch (e: Exception) {
            // Keep existing rules on network error
        }
        try {
            schedules = apiService.getScreenSchedule()
        } catch (e: Exception) {
            // Keep existing schedules on network error
        }
    }

    /**
     * Refresh today's usage stats from UsageStatsManager.
     * Used for LIMIT-type rules to check if daily quota is exceeded.
     */
    private fun refreshTodayUsage() {
        try {
            val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val calendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val startOfDay = calendar.timeInMillis
            val now = System.currentTimeMillis()

            // Aggregated per package (daily buckets can overlap and overwrite each other)
            todayUsageSeconds = usm.queryAndAggregateUsageStats(startOfDay, now)
                .mapValues { (_, stat) -> stat.totalTimeInForeground / 1000 }
        } catch (e: Exception) {
            // UsageStats not available (permission not granted)
        }
    }

    companion object {
        private val ALWAYS_ALLOWED = setOf(
            "com.android.systemui",
            "com.android.phone",
            "com.android.server.telecom",
            "com.android.emergency",
        )
    }

    override fun onInterrupt() {
        serviceScope.cancel()
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}
