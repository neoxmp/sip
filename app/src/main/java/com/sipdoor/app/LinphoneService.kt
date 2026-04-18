package com.sipdoor.app

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import org.linphone.core.*

/**
 * SIP bağlantısını arka planda yöneten Foreground Service.
 * Uygulama kapalıyken bile çağrıları alabilmek için cihaz açıldığında başlar.
 */
class LinphoneService : LifecycleService() {

    private lateinit var core: Core
    private lateinit var prefs: SipPreferences
    private var wakeLock: PowerManager.WakeLock? = null

    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getService(): LinphoneService = this@LinphoneService
    }

    // Dışarıdan çağrı durumunu dinlemek için callback
    var onIncomingCall: ((Call) -> Unit)? = null
    var onCallEnded: (() -> Unit)? = null
    var onRegistrationChanged: ((RegistrationState) -> Unit)? = null

    // ─────────────────────────────────────────────
    // Linphone Core Listener
    // ─────────────────────────────────────────────
    private val coreListener = object : CoreListenerStub() {

        override fun onCallStateChanged(
            core: Core, call: Call, state: Call.State, message: String
        ) {
            Log.d(TAG, "Çağrı durumu: $state - $message")
            when (state) {
                Call.State.IncomingReceived -> {
                    acquireWakeLock()
                    showIncomingCallNotification(call)
                    onIncomingCall?.invoke(call)
                }
                Call.State.Connected -> {
                    cancelIncomingCallNotification()
                    showOngoingCallNotification()
                }
                Call.State.End, Call.State.Error, Call.State.Released -> {
                    cancelIncomingCallNotification()
                    cancelOngoingCallNotification()
                    releaseWakeLock()
                    onCallEnded?.invoke()
                }
                else -> {}
            }
        }

        override fun onAccountRegistrationStateChanged(
            core: Core, account: Account,
            state: RegistrationState, message: String
        ) {
            Log.d(TAG, "Kayıt durumu: $state - $message")
            onRegistrationChanged?.invoke(state)
        }
    }

    // ─────────────────────────────────────────────
    // Service Lifecycle
    // ─────────────────────────────────────────────
    override fun onCreate() {
        super.onCreate()
        prefs = SipPreferences(this)
        createNotificationChannels()
        startForeground(NOTIF_ID_SERVICE, buildServiceNotification())
        initLinphoneCore()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_STICKY // Sistem tarafından öldürülürse otomatik yeniden başlat
    }

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        return binder
    }

    override fun onDestroy() {
        core.removeListener(coreListener)
        core.stop()
        releaseWakeLock()
        super.onDestroy()
    }

    // ─────────────────────────────────────────────
    // Linphone Başlatma
    // ─────────────────────────────────────────────
    private fun initLinphoneCore() {
        val factory = Factory.instance()
        factory.setDebugMode(true, "SipDoor")

        core = factory.createCore(null, null, this)
        core.addListener(coreListener)

        // Video codec'leri etkinleştir (görüntülü arama için)
        for (codec in core.videoCodecs) {
            core.enablePayloadType(codec, true)
        }

        core.videoCaptureEnabled = true
        core.videoDisplayEnabled = true
        core.videoActivationPolicy = core.videoActivationPolicy.also {
            it.automaticallyInitiate = false  // Kullanıcı onayıyla başlat
            it.automaticallyAccept = true     // Karşı tarafın video teklifini kabul et
        }

        core.start()

        // Kaydedilmiş SIP hesabı varsa kaydet
        if (prefs.isConfigured()) {
            registerAccount()
        }
    }

    // ─────────────────────────────────────────────
    // Hesap Kaydı
    // ─────────────────────────────────────────────
    fun registerAccount() {
        // Eski hesapları temizle
        core.clearAccounts()
        core.clearAllAuthInfo()

        val factory = Factory.instance()
        val username = prefs.username
        val password = prefs.password
        val domain = prefs.domain
        val port = prefs.port
        val transport = when (prefs.transport.uppercase()) {
            "TCP" -> TransportType.Tcp
            "TLS" -> TransportType.Tls
            else -> TransportType.Udp
        }

        // Auth bilgisi
        val authInfo = factory.createAuthInfo(username, null, password, null, null, domain)
        core.addAuthInfo(authInfo)

        // Hesap parametreleri
        val accountParams = core.createAccountParams()
        val identity = factory.createAddress("sip:$username@$domain")
        accountParams.identityAddress = identity

        val serverAddress = factory.createAddress("sip:$domain:$port")
        serverAddress?.transport = transport
        accountParams.serverAddress = serverAddress
        accountParams.registerEnabled = true

        // Kayıt yenileme aralığı (saniye)
        accountParams.expires = 3600

        val account = core.createAccount(accountParams)
        core.addAccount(account)
        core.defaultAccount = account

        Log.d(TAG, "SIP kaydı başlatıldı: $username@$domain:$port")
    }

    fun unregisterAccount() {
        core.defaultAccount?.apply {
            val params = params.clone()
            params.registerEnabled = false
            setParams(params)
        }
    }

    // ─────────────────────────────────────────────
    // Çağrı İşlemleri
    // ─────────────────────────────────────────────

    /** Gelen çağrıyı sesli olarak yanıtla */
    fun answerCall(withVideo: Boolean = false) {
        val call = core.currentCall ?: return
        val params = core.createCallParams(call)
        params?.videoEnabled = withVideo
        call.acceptWithParams(params)
        Log.d(TAG, "Çağrı yanıtlandı (video=$withVideo)")
    }

    /** Gelen çağrıyı reddet */
    fun declineCall() {
        core.currentCall?.decline(Reason.Declined)
        Log.d(TAG, "Çağrı reddedildi")
    }

    /** Aktif çağrıyı sonlandır */
    fun hangUp() {
        core.currentCall?.terminate()
        Log.d(TAG, "Çağrı sonlandırıldı")
    }

    /** DTMF tonu gönder (kapı açma) */
    fun sendDtmf(digit: Char) {
        core.currentCall?.sendDtmf(digit)
        Log.d(TAG, "DTMF gönderildi: $digit")
    }

    /** Preferences'taki kapı kodunu gönder */
    fun openDoor() {
        val code = prefs.doorDtmfCode
        code.forEach { char ->
            sendDtmf(char)
            Thread.sleep(200) // Karakterler arası bekleme
        }
        Log.d(TAG, "Kapı açma kodu gönderildi: $code")
    }

    /** Mikrofonu sessize al / aç */
    fun toggleMute(): Boolean {
        core.micEnabled = !core.micEnabled
        return !core.micEnabled // true = muted
    }

    /** Hoparlörü aç / kapat */
    fun toggleSpeaker(): Boolean {
        val audioDevice = if (core.outputAudioDevice?.type == AudioDevice.Type.Speaker) {
            core.audioDevices.firstOrNull { it.type == AudioDevice.Type.Earpiece }
        } else {
            core.audioDevices.firstOrNull { it.type == AudioDevice.Type.Speaker }
        }
        audioDevice?.let { core.outputAudioDevice = it }
        return core.outputAudioDevice?.type == AudioDevice.Type.Speaker
    }

    /** Mevcut çağrıyı döndür */
    fun getCurrentCall(): Call? = core.currentCall

    /** Kayıt durumunu döndür */
    fun getRegistrationState(): RegistrationState? =
        core.defaultAccount?.state

    // ─────────────────────────────────────────────
    // Bildirimler
    // ─────────────────────────────────────────────
    private fun createNotificationChannels() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Servis kanalı (sessiz)
        NotificationChannel(CHANNEL_SERVICE, "SIP Servisi", NotificationManager.IMPORTANCE_LOW)
            .also { nm.createNotificationChannel(it) }

        // Gelen çağrı kanalı (yüksek öncelikli)
        NotificationChannel(CHANNEL_INCOMING, "Gelen Çağrılar", NotificationManager.IMPORTANCE_HIGH)
            .apply { lockscreenVisibility = Notification.VISIBILITY_PUBLIC }
            .also { nm.createNotificationChannel(it) }

        // Aktif çağrı kanalı
        NotificationChannel(CHANNEL_ONGOING, "Aktif Çağrı", NotificationManager.IMPORTANCE_LOW)
            .also { nm.createNotificationChannel(it) }
    }

    private fun buildServiceNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pi = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_SERVICE)
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setContentTitle("SipDoor")
            .setContentText("SIP sunucusuna bağlı")
            .setContentIntent(pi)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun showIncomingCallNotification(call: Call) {
        val callerName = call.remoteAddress?.displayName
            ?: call.remoteAddress?.username
            ?: "Bilinmeyen"

        // Tam ekran intent (kilit ekranında açılır)
        val fullScreenIntent = Intent(this, IncomingCallActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
        }
        val fullScreenPi = PendingIntent.getActivity(
            this, 1, fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Yanıtla action
        val answerPi = PendingIntent.getBroadcast(
            this, 2,
            Intent(ACTION_ANSWER).setPackage(packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Reddet action
        val declinePi = PendingIntent.getBroadcast(
            this, 3,
            Intent(ACTION_DECLINE).setPackage(packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_INCOMING)
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setContentTitle("Gelen Çağrı")
            .setContentText(callerName)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setFullScreenIntent(fullScreenPi, true)
            .addAction(android.R.drawable.ic_menu_call, "Yanıtla", answerPi)
            .addAction(android.R.drawable.ic_delete, "Reddet", declinePi)
            .setAutoCancel(false)
            .setOngoing(true)
            .build()

        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(NOTIF_ID_INCOMING, notification)
    }

    private fun showOngoingCallNotification() {
        val intent = Intent(this, OngoingCallActivity::class.java)
        val pi = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(this, CHANNEL_ONGOING)
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setContentTitle("Aktif Çağrı")
            .setContentText("Çağrı devam ediyor...")
            .setContentIntent(pi)
            .setOngoing(true)
            .build()

        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(NOTIF_ID_ONGOING, notification)
    }

    private fun cancelIncomingCallNotification() {
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .cancel(NOTIF_ID_INCOMING)
    }

    private fun cancelOngoingCallNotification() {
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .cancel(NOTIF_ID_ONGOING)
    }

    // ─────────────────────────────────────────────
    // Wake Lock (ekranı açık tut)
    // ─────────────────────────────────────────────
    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK, "SipDoor::CallWakeLock"
        ).apply { acquire(60_000L) }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    companion object {
        private const val TAG = "LinphoneService"
        const val CHANNEL_SERVICE = "channel_service"
        const val CHANNEL_INCOMING = "channel_incoming"
        const val CHANNEL_ONGOING = "channel_ongoing"
        const val NOTIF_ID_SERVICE = 1
        const val NOTIF_ID_INCOMING = 2
        const val NOTIF_ID_ONGOING = 3
        const val ACTION_ANSWER = "com.sipdoor.app.ACTION_ANSWER"
        const val ACTION_DECLINE = "com.sipdoor.app.ACTION_DECLINE"
    }
}
