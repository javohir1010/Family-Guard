package com.familyguard.app.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.familyguard.app.services.LocationForegroundService

/**
 * Starts FamilyGuard services automatically after device reboot.
 * Requires RECEIVE_BOOT_COMPLETED permission.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                LocationForegroundService.startService(context)
                // DnsVpnService requires VPN consent — will auto-start if previously connected
            }
        }
    }
}
