package com.sipdoor.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Cihaz açıldığında SIP servisini otomatik başlatır.
 * Böylece uygulama açık olmasa bile çağrı alınabilir.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON"
        ) {
            val prefs = SipPreferences(context)
            if (prefs.isConfigured()) {
                context.startForegroundService(
                    Intent(context, LinphoneService::class.java)
                )
            }
        }
    }
}
