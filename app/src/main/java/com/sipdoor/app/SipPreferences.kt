package com.sipdoor.app

import android.content.Context
import android.content.SharedPreferences

/**
 * SIP sunucu ayarlarını ve kapı açma DTMF kodunu yöneten yardımcı sınıf.
 */
class SipPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("sip_prefs", Context.MODE_PRIVATE)

    var username: String
        get() = prefs.getString(KEY_USERNAME, "") ?: ""
        set(value) = prefs.edit().putString(KEY_USERNAME, value).apply()

    var password: String
        get() = prefs.getString(KEY_PASSWORD, "") ?: ""
        set(value) = prefs.edit().putString(KEY_PASSWORD, value).apply()

    var domain: String
        get() = prefs.getString(KEY_DOMAIN, "") ?: ""
        set(value) = prefs.edit().putString(KEY_DOMAIN, value).apply()

    var port: Int
        get() = prefs.getInt(KEY_PORT, 5060)
        set(value) = prefs.edit().putInt(KEY_PORT, value).apply()

    var transport: String
        get() = prefs.getString(KEY_TRANSPORT, "UDP") ?: "UDP"
        set(value) = prefs.edit().putString(KEY_TRANSPORT, value).apply()

    /** Kapıyı açacak DTMF kodu, örn: "#" veya "1" */
    var doorDtmfCode: String
        get() = prefs.getString(KEY_DOOR_DTMF, "#") ?: "#"
        set(value) = prefs.edit().putString(KEY_DOOR_DTMF, value).apply()

    /** Otomatik yanıtlama süresi (saniye, 0 = kapalı) */
    var autoAnswerDelay: Int
        get() = prefs.getInt(KEY_AUTO_ANSWER, 0)
        set(value) = prefs.edit().putInt(KEY_AUTO_ANSWER, value).apply()

    /** Ayarların dolu olup olmadığını kontrol eder */
    fun isConfigured(): Boolean {
        return username.isNotEmpty() && password.isNotEmpty() && domain.isNotEmpty()
    }

    companion object {
        private const val KEY_USERNAME = "username"
        private const val KEY_PASSWORD = "password"
        private const val KEY_DOMAIN = "domain"
        private const val KEY_PORT = "port"
        private const val KEY_TRANSPORT = "transport"
        private const val KEY_DOOR_DTMF = "door_dtmf"
        private const val KEY_AUTO_ANSWER = "auto_answer"
    }
}
