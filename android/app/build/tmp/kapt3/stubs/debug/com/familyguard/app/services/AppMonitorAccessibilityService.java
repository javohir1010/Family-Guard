package com.familyguard.app.services;

/**
 * App monitoring and blocking via Android Accessibility Service.
 *
 * HOW IT WORKS:
 * ─────────────────────────────────────────────────────────────────
 * Level 1 (Accessibility Service):
 *  • Intercepts TYPE_WINDOW_STATE_CHANGED events
 *  • Detects which app moved to foreground
 *  • If blocked → launches BlockOverlayActivity on top
 *  • Limitation: some Chinese ROMs don't fire this event reliably
 *
 * Level 2 (UsageStatsManager fallback):
 *  • Polls foreground app every 500ms via UsageStats
 *  • More reliable across all devices and Android versions
 *  • Requires PACKAGE_USAGE_STATS permission (manual grant in settings)
 *
 * Both levels work together for maximum reliability.
 * ─────────────────────────────────────────────────────────────────
 *
 * LIMITATIONS (honest assessment for diploma):
 *  ✅ Works on stock Android (Pixel, Sony, Nokia, Motorola)
 *  ✅ Works on Samsung One UI 4+
 *  ⚠️ May be killed on Xiaomi MIUI without battery optimization exclusion
 *  ⚠️ User must enable manually in Settings → Accessibility
 *  ❌ A determined child can disable Accessibility in Settings
 *     (→ parent gets notified via permission monitoring)
 *
 * FOR STRONGER BLOCKING: combine with DevicePolicyManager
 * (setApplicationHidden) — see BlockOverlayActivity.tryHideAppWithDPM()
 */
@dagger.hilt.android.AndroidEntryPoint()
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000L\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0010\u000e\n\u0000\n\u0002\u0010\t\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010%\n\u0000\n\u0002\u0010\u0002\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0002\b\t\n\u0002\u0010\b\n\u0000\b\u0007\u0018\u00002\u00020\u0001B\u0005\u00a2\u0006\u0002\u0010\u0002J\u0016\u0010\u0014\u001a\u00020\u00152\u0006\u0010\u0016\u001a\u00020\rH\u0082@\u00a2\u0006\u0002\u0010\u0017J\n\u0010\u0018\u001a\u0004\u0018\u00010\rH\u0002J\u0012\u0010\u0019\u001a\u00020\u00152\b\u0010\u001a\u001a\u0004\u0018\u00010\u001bH\u0016J\b\u0010\u001c\u001a\u00020\u0015H\u0016J\b\u0010\u001d\u001a\u00020\u0015H\u0016J\b\u0010\u001e\u001a\u00020\u0015H\u0014J\u000e\u0010\u001f\u001a\u00020\u0015H\u0082@\u00a2\u0006\u0002\u0010 J\b\u0010!\u001a\u00020\u0015H\u0002J \u0010\"\u001a\u00020\u00152\u0006\u0010\u0016\u001a\u00020\r2\u0006\u0010#\u001a\u00020\r2\u0006\u0010$\u001a\u00020%H\u0002R\u0014\u0010\u0003\u001a\b\u0012\u0004\u0012\u00020\u00050\u0004X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u001e\u0010\u0006\u001a\u00020\u00078\u0006@\u0006X\u0087.\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\b\u0010\t\"\u0004\b\n\u0010\u000bR\u000e\u0010\f\u001a\u00020\rX\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u000e\u001a\u00020\u000fX\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0010\u001a\u00020\u0011X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u001a\u0010\u0012\u001a\u000e\u0012\u0004\u0012\u00020\r\u0012\u0004\u0012\u00020\u000f0\u0013X\u0082\u0004\u00a2\u0006\u0002\n\u0000\u00a8\u0006&"}, d2 = {"Lcom/familyguard/app/services/AppMonitorAccessibilityService;", "Landroid/accessibilityservice/AccessibilityService;", "()V", "activeRules", "", "Lcom/familyguard/app/data/model/AppRule;", "apiService", "Lcom/familyguard/app/data/api/ApiService;", "getApiService", "()Lcom/familyguard/app/data/api/ApiService;", "setApiService", "(Lcom/familyguard/app/data/api/ApiService;)V", "lastBlockedPackage", "", "lastBlockedTime", "", "serviceScope", "Lkotlinx/coroutines/CoroutineScope;", "todayUsageSeconds", "", "checkAndBlock", "", "packageName", "(Ljava/lang/String;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "getForegroundAppViaUsageStats", "onAccessibilityEvent", "event", "Landroid/view/accessibility/AccessibilityEvent;", "onDestroy", "onInterrupt", "onServiceConnected", "refreshRules", "(Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "refreshTodayUsage", "showBlockOverlay", "appName", "limitMinutes", "", "app_debug"})
public final class AppMonitorAccessibilityService extends android.accessibilityservice.AccessibilityService {
    @javax.inject.Inject()
    public com.familyguard.app.data.api.ApiService apiService;
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.CoroutineScope serviceScope = null;
    @org.jetbrains.annotations.NotNull()
    private java.util.List<com.familyguard.app.data.model.AppRule> activeRules;
    @org.jetbrains.annotations.NotNull()
    private final java.util.Map<java.lang.String, java.lang.Long> todayUsageSeconds = null;
    @org.jetbrains.annotations.NotNull()
    private java.lang.String lastBlockedPackage = "";
    private long lastBlockedTime = 0L;
    
    public AppMonitorAccessibilityService() {
        super();
    }
    
    @org.jetbrains.annotations.NotNull()
    public final com.familyguard.app.data.api.ApiService getApiService() {
        return null;
    }
    
    public final void setApiService(@org.jetbrains.annotations.NotNull()
    com.familyguard.app.data.api.ApiService p0) {
    }
    
    @java.lang.Override()
    protected void onServiceConnected() {
    }
    
    @java.lang.Override()
    public void onAccessibilityEvent(@org.jetbrains.annotations.Nullable()
    android.view.accessibility.AccessibilityEvent event) {
    }
    
    private final java.lang.Object checkAndBlock(java.lang.String packageName, kotlin.coroutines.Continuation<? super kotlin.Unit> $completion) {
        return null;
    }
    
    private final void showBlockOverlay(java.lang.String packageName, java.lang.String appName, int limitMinutes) {
    }
    
    /**
     * Level 2: Get current foreground app via UsageStatsManager.
     * More reliable on some devices where accessibility events are unreliable.
     * Requires PACKAGE_USAGE_STATS permission.
     */
    private final java.lang.String getForegroundAppViaUsageStats() {
        return null;
    }
    
    private final java.lang.Object refreshRules(kotlin.coroutines.Continuation<? super kotlin.Unit> $completion) {
        return null;
    }
    
    /**
     * Refresh today's usage stats from UsageStatsManager.
     * Used for LIMIT-type rules to check if daily quota is exceeded.
     */
    private final void refreshTodayUsage() {
    }
    
    @java.lang.Override()
    public void onInterrupt() {
    }
    
    @java.lang.Override()
    public void onDestroy() {
    }
}