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

class LinphoneService : LifecycleService() {

    private lateinit var core: Core
    private lateinit var prefs: SipPreferences
    private var wakeLock: PowerManager.WakeLock? = null
    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getService(): LinphoneService = this@LinphoneService
    }

    var onIncomingCall: ((Call) -> Unit)? = null
    var onCallEnded: (() -> Unit)? = null
    var onRegistrationChanged: ((RegistrationState) -> Unit)? = null

    private val coreListener = object : CoreListenerStub() {
        override fun onCallStateChanged(core: Core, call: Call, state: Call.State, message: String) {
            Log.d(TAG, "Çağrı durumu: $state")
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
            core: Core, account: Account, state: RegistrationState, message: String
        ) {
            Log.d(TAG, "Kayıt durumu: $state")
            onRegistrationChanged?.invoke(state)
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefs = SipPreferences(this)
        createNotificationChannels()
        startForeground(NOTIF_ID_SERVICE, buildServiceNotification())
        initLinphoneCore()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
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

    private fun initLinphoneCore() {
        val factory = Factory.instance()
        factory.setDebugMode(true, "SipDoor")
        core = factory.createCore(null, null, this)
        core.addListener(coreListener)

        try {
            val policy = Factory.instance().createVideoActivationPolicy()
            policy.automaticallyInitiate = false
            policy.automaticallyAccept = true
            core.videoActivationPolicy = policy
        } catch (e: Exception) {
            Log.w(TAG, "Video policy ayarlanamadı: ${e.message}")
        }

        core.start()
        if (prefs.isConfigured()) registerAccount()
    }

    fun registerAccount() {
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

        val authInfo = factory.createAuthInfo(username, null, password, null, null, domain)
        core.addAuthInfo(authInfo)

        val accountParams = core.createAccountParams()
        accountParams.identityAddress = factory.createAddress("sip:$username@$domain")
        val serverAddress = factory.createAddress("sip:$domain:$port")
        serverAddress?.transport = transport
        accountParams.serverAddress = serverAddress
        accountParams.isRegisterEnabled = true
        accountParams.expires = 3600

        val account = core.createAccount(accountParams)
        core.addAccount(account)
        core.defaultAccount = account
        Log.d(TAG, "SIP kaydı: $username@$domain:$port")
    }

    fun answerCall(withVideo: Boolean = false) {
        val call = core.currentCall ?: return
        val params = core.createCallParams(call)
        params?.isVideoEnabled = withVideo
        call.acceptWithParams(params)
    }

    fun declineCall() { core.currentCall?.decline(Reason.Declined) }
    fun hangUp() { core.currentCall?.terminate() }

    fun sendDtmf(digit: Char) {
        core.currentCall?.sendDtmf(digit)
        Log.d(TAG, "DTMF: $digit")
    }

    fun openDoor() {
        prefs.doorDtmfCode.forEach { char ->
            sendDtmf(char)
            Thread.sleep(200)
        }
    }

    fun toggleMute(): Boolean {
        core.isMicEnabled = !core.isMicEnabled
        return !core.isMicEnabled
    }

    fun toggleSpeaker(): Boolean {
        val isSpeaker = core.outputAudioDevice?.type == AudioDevice.Type.Speaker
        val target = if (isSpeaker) AudioDevice.Type.Earpiece else AudioDevice.Type.Speaker
        core.audioDevices.firstOrNull { it.type == target }?.let { core.outputAudioDevice = it }
        return core.outputAudioDevice?.type == AudioDevice.Type.Speaker
    }

    fun getCurrentCall(): Call? = core.currentCall
    fun getRegistrationState(): RegistrationState? = core.defaultAccount?.state

    private fun createNotificationChannels() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        NotificationChannel(CHANNEL_SERVICE, "SIP Servisi", NotificationManager.IMPORTANCE_LOW)
            .also { nm.createNotificationChannel(it) }
        NotificationChannel(CHANNEL_INCOMING, "Gelen Çağrılar", NotificationManager.IMPORTANCE_HIGH)
            .apply { lockscreenVisibility = Notification.VISIBILITY_PUBLIC }
            .also { nm.createNotificationChannel(it) }
        NotificationChannel(CHANNEL_ONGOING, "Aktif Çağrı", NotificationManager.IMPORTANCE_LOW)
            .also { nm.createNotificationChannel(it) }
    }

    private fun buildServiceNotification(): Notification {
        val pi = PendingIntent.getActivity(this, 0,
            Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_SERVICE)
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setContentTitle("SipDoor")
            .setContentText("SIP sunucusuna bağlı")
            .setContentIntent(pi)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun showIncomingCallNotification(call: Call) {
        val callerName = call.remoteAddress?.displayName ?: call.remoteAddress?.username ?: "Bilinmeyen"
        val fullScreenPi = PendingIntent.getActivity(this, 1,
            Intent(this, IncomingCallActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
            }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val answerPi = PendingIntent.getBroadcast(this, 2,
            Intent(ACTION_ANSWER).setPackage(packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val declinePi = PendingIntent.getBroadcast(this, 3,
            Intent(ACTION_DECLINE).setPackage(packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(
            NOTIF_ID_INCOMING,
            NotificationCompat.Builder(this, CHANNEL_INCOMING)
                .setSmallIcon(android.R.drawable.ic_menu_call)
                .setContentTitle("Gelen Çağrı").setContentText(callerName)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setFullScreenIntent(fullScreenPi, true)
                .addAction(android.R.drawable.ic_menu_call, "Yanıtla", answerPi)
                .addAction(android.R.drawable.ic_delete, "Reddet", declinePi)
                .setAutoCancel(false).setOngoing(true).build()
        )
    }

    private fun showOngoingCallNotification() {
        val pi = PendingIntent.getActivity(this, 0,
            Intent(this, OngoingCallActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(
            NOTIF_ID_ONGOING,
            NotificationCompat.Builder(this, CHANNEL_ONGOING)
                .setSmallIcon(android.R.drawable.ic_menu_call)
                .setContentTitle("Aktif Çağrı").setContentText("Çağrı devam ediyor...")
                .setContentIntent(pi).setOngoing(true).build()
        )
    }

    private fun cancelIncomingCallNotification() =
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(NOTIF_ID_INCOMING)

    private fun cancelOngoingCallNotification() =
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(NOTIF_ID_ONGOING)

    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SipDoor::CallWakeLock")
            .apply { acquire(60_000L) }
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
