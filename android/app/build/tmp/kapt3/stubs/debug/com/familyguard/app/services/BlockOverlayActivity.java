package com.familyguard.app.services;

/**
 * Full-screen overlay shown when a child tries to open a blocked app.
 *
 * Two levels of blocking:
 *
 * Level 1 (Accessibility Service — always available):
 *  → Show this overlay activity on top of the blocked app
 *  → Navigate home when back is pressed
 *  → Re-trigger if child tries to return to the app
 *
 * Level 2 (Device Policy Manager — if Device Admin is active):
 *  → setApplicationHidden() makes the app completely invisible
 *  → Cannot be launched at all — no overlay needed
 *  → More reliable but requires explicit user grant on first install
 */
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000,\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0002\b\u0002\b\u0007\u0018\u0000 \u000e2\u00020\u0001:\u0001\u000eB\u0005\u00a2\u0006\u0002\u0010\u0002J\b\u0010\u0003\u001a\u00020\u0004H\u0002J\u0012\u0010\u0005\u001a\u00020\u00042\b\u0010\u0006\u001a\u0004\u0018\u00010\u0007H\u0014J\u0010\u0010\b\u001a\u00020\u00042\u0006\u0010\t\u001a\u00020\nH\u0014J\u000e\u0010\u000b\u001a\u00020\u00042\u0006\u0010\f\u001a\u00020\r\u00a8\u0006\u000f"}, d2 = {"Lcom/familyguard/app/services/BlockOverlayActivity;", "Landroidx/activity/ComponentActivity;", "()V", "goHome", "", "onCreate", "savedInstanceState", "Landroid/os/Bundle;", "onNewIntent", "intent", "Landroid/content/Intent;", "tryHideAppWithDPM", "packageName", "", "Companion", "app_debug"})
public final class BlockOverlayActivity extends androidx.activity.ComponentActivity {
    @org.jetbrains.annotations.NotNull()
    public static final java.lang.String EXTRA_PACKAGE = "package_name";
    @org.jetbrains.annotations.NotNull()
    public static final java.lang.String EXTRA_APP_NAME = "app_name";
    @org.jetbrains.annotations.NotNull()
    public static final java.lang.String EXTRA_LIMIT_MINUTES = "limit_minutes";
    @org.jetbrains.annotations.NotNull()
    public static final com.familyguard.app.services.BlockOverlayActivity.Companion Companion = null;
    
    public BlockOverlayActivity() {
        super(0);
    }
    
    @java.lang.Override()
    protected void onCreate(@org.jetbrains.annotations.Nullable()
    android.os.Bundle savedInstanceState) {
    }
    
    @java.lang.Override()
    protected void onNewIntent(@org.jetbrains.annotations.NotNull()
    android.content.Intent intent) {
    }
    
    /**
     * Navigate to home screen instead of returning to the blocked app.
     */
    private final void goHome() {
    }
    
    /**
     * Attempt Level 2 blocking via DevicePolicyManager.
     * If Device Admin is active, hides the app completely from launcher.
     * Falls back to Level 1 (overlay) if DPM is not available.
     */
    public final void tryHideAppWithDPM(@org.jetbrains.annotations.NotNull()
    java.lang.String packageName) {
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000(\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\b\n\u0000\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J(\u0010\u0007\u001a\u00020\b2\u0006\u0010\t\u001a\u00020\n2\u0006\u0010\u000b\u001a\u00020\u00042\u0006\u0010\f\u001a\u00020\u00042\b\b\u0002\u0010\r\u001a\u00020\u000eR\u000e\u0010\u0003\u001a\u00020\u0004X\u0086T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0005\u001a\u00020\u0004X\u0086T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0006\u001a\u00020\u0004X\u0086T\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u000f"}, d2 = {"Lcom/familyguard/app/services/BlockOverlayActivity$Companion;", "", "()V", "EXTRA_APP_NAME", "", "EXTRA_LIMIT_MINUTES", "EXTRA_PACKAGE", "createIntent", "Landroid/content/Intent;", "context", "Landroid/content/Context;", "packageName", "appName", "limitMinutes", "", "app_debug"})
    public static final class Companion {
        
        private Companion() {
            super();
        }
        
        @org.jetbrains.annotations.NotNull()
        public final android.content.Intent createIntent(@org.jetbrains.annotations.NotNull()
        android.content.Context context, @org.jetbrains.annotations.NotNull()
        java.lang.String packageName, @org.jetbrains.annotations.NotNull()
        java.lang.String appName, int limitMinutes) {
            return null;
        }
    }
}