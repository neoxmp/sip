package com.sipdoor.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Bildirim üzerindeki "Yanıtla" ve "Reddet" butonlarını yakalar.
 * Ekran kapalıyken bildirimi yanıtlamak için kullanılır.
 */
class CallNotificationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val service = SipDoorApplication.instance.linphoneService ?: return

        when (intent.action) {
            LinphoneService.ACTION_ANSWER -> {
                service.answerCall(withVideo = false)
                // Aktif çağrı ekranını aç
                context.startActivity(
                    Intent(context, OngoingCallActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                )
            }
            LinphoneService.ACTION_DECLINE -> {
                service.declineCall()
            }
        }
    }
}
