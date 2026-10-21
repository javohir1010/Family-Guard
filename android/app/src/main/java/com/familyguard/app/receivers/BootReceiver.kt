package com.familyguard.app.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import com.familyguard.app.services.DnsVpnService
import com.familyguard.app.services.LocationForegroundService
import com.familyguard.app.ui.screens.isDnsFilterRequested

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
                // VPN consent survives reboot: prepare() == null means it is still granted
                if (isDnsFilterRequested(context) && VpnService.prepare(context) == null) {
                    DnsVpnService.startVpn(context)
                }
            }
        }
    }
}
