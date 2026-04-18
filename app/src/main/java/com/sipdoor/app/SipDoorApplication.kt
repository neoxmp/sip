package com.sipdoor.app

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log

/**
 * Uygulama sınıfı. LinphoneService'e global erişim sağlar.
 */
class SipDoorApplication : Application() {

    var linphoneService: LinphoneService? = null
        private set

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            linphoneService = (binder as LinphoneService.LocalBinder).getService()
            Log.d("SipDoorApp", "LinphoneService bağlandı")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            linphoneService = null
            Log.d("SipDoorApp", "LinphoneService bağlantısı kesildi")
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        startAndBindService()
    }

    private fun startAndBindService() {
        val intent = Intent(this, LinphoneService::class.java)
        startForegroundService(intent)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    companion object {
        lateinit var instance: SipDoorApplication
            private set
    }
}
