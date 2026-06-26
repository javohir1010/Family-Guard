package com.familyguard.app.receivers;

/**
 * Device Admin Receiver.
 *
 * Activating Device Admin allows FamilyGuard to:
 *  • Prevent the app from being uninstalled by the child
 *  • Use DevicePolicyManager.setApplicationHidden() to fully hide blocked apps
 *  • Lock the device screen on demand (emergency schedule enforcement)
 *
 * Activation requires: user manually confirms in Settings during install or
 * via prompt shown on first launch.
 *
 * The child CANNOT deactivate Device Admin without a password (set by parent).
 */
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000 \n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\b\u0007\u0018\u00002\u00020\u0001B\u0005\u00a2\u0006\u0002\u0010\u0002J\u0018\u0010\u0003\u001a\u00020\u00042\u0006\u0010\u0005\u001a\u00020\u00062\u0006\u0010\u0007\u001a\u00020\bH\u0016J\u0018\u0010\t\u001a\u00020\u00042\u0006\u0010\u0005\u001a\u00020\u00062\u0006\u0010\u0007\u001a\u00020\bH\u0016J\u0018\u0010\n\u001a\u00020\u00042\u0006\u0010\u0005\u001a\u00020\u00062\u0006\u0010\u0007\u001a\u00020\bH\u0016\u00a8\u0006\u000b"}, d2 = {"Lcom/familyguard/app/receivers/DeviceAdminReceiver;", "Landroid/app/admin/DeviceAdminReceiver;", "()V", "onDisabled", "", "context", "Landroid/content/Context;", "intent", "Landroid/content/Intent;", "onEnabled", "onPasswordFailed", "app_debug"})
public final class DeviceAdminReceiver extends android.app.admin.DeviceAdminReceiver {
    
    public DeviceAdminReceiver() {
        super();
    }
    
    @java.lang.Override()
    public void onEnabled(@org.jetbrains.annotations.NotNull()
    android.content.Context context, @org.jetbrains.annotations.NotNull()
    android.content.Intent intent) {
    }
    
    @java.lang.Override()
    public void onDisabled(@org.jetbrains.annotations.NotNull()
    android.content.Context context, @org.jetbrains.annotations.NotNull()
    android.content.Intent intent) {
    }
    
    @java.lang.Override()
    public void onPasswordFailed(@org.jetbrains.annotations.NotNull()
    android.content.Context context, @org.jetbrains.annotations.NotNull()
    android.content.Intent intent) {
    }
}