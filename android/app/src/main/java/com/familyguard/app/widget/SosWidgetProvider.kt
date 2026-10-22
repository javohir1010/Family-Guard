package com.familyguard.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.familyguard.app.R
import com.familyguard.app.ui.MainActivity

/**
 * Home-screen SOS widget. A tap opens FamilyGuard and counts as one SOS
 * press, so the signal goes out without looking for the app icon; more taps
 * in the app add up exactly like presses on the big button.
 */
class SosWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        val intent = Intent(context, MainActivity::class.java)
            .setAction(MainActivity.ACTION_SOS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pending = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val views = RemoteViews(context.packageName, R.layout.widget_sos).apply {
            setOnClickPendingIntent(R.id.widget_root, pending)
        }
        appWidgetIds.forEach { manager.updateAppWidget(it, views) }
    }
}
