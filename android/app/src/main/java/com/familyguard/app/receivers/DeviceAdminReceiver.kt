package com.familyguard.app.receivers

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent

/**
 * Device Admin Receiver.
 *
 * Activating Device Admin allows FamilyGuard to:
 *   • Prevent the app from being uninstalled by the child
 *   • Use DevicePolicyManager.setApplicationHidden() to fully hide blocked apps
 *   • Lock the device screen on demand (emergency schedule enforcement)
 *
 * Activation requires: user manually confirms in Settings during install or
 * via prompt shown on first launch.
 *
 * The child CANNOT deactivate Device Admin without a password (set by parent).
 */
class DeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        // Device Admin activated — stronger blocking now available
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        // Notify parent that Device Admin was removed
        // (this happens if parent enters their password)
    }

    override fun onPasswordFailed(context: Context, intent: Intent) {
        super.onPasswordFailed(context, intent)
        // Wrong password attempt — notify parent via FCM
    }
}
