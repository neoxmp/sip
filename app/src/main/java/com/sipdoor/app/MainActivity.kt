package com.sipdoor.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.sipdoor.app.databinding.ActivityMainBinding
import org.linphone.core.RegistrationState

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: SipPreferences
    private val service get() = SipDoorApplication.instance.linphoneService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = SipPreferences(this)
        requestRequiredPermissions()
        requestBatteryOptimizationExemption()
        loadSettings()
        setupListeners()
    }

    override fun onResume() {
        super.onResume()
        service?.onRegistrationChanged = { state ->
            runOnUiThread { updateRegistrationStatus(state) }
        }
        service?.getRegistrationState()?.let { updateRegistrationStatus(it) }
    }

    override fun onPause() {
        super.onPause()
        service?.onRegistrationChanged = null
    }

    private fun loadSettings() {
        with(binding) {
            etUsername.setText(prefs.username)
            etPassword.setText(prefs.password)
            etDomain.setText(prefs.domain)
            etPort.setText(prefs.port.toString())
            etDtmfCode.setText(prefs.doorDtmfCode)
            etAutoAnswer.setText(prefs.autoAnswerDelay.toString())
            val transports = arrayOf("UDP", "TCP", "TLS")
            val idx = transports.indexOf(prefs.transport.uppercase()).coerceAtLeast(0)
            spinnerTransport.setSelection(idx)
        }
    }

    private fun setupListeners() {
        binding.btnSave.setOnClickListener { saveSettings() }
        binding.btnRegister.setOnClickListener {
            if (prefs.isConfigured()) {
                service?.registerAccount()
                Toast.makeText(this, "Kayıt başlatılıyor...", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Önce ayarları kaydedin!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun saveSettings() {
        with(binding) {
            val username = etUsername.text.toString().trim()
            val password = etPassword.text.toString().trim()
            val domain = etDomain.text.toString().trim()

            if (username.isEmpty() || password.isEmpty() || domain.isEmpty()) {
                Toast.makeText(this@MainActivity, "Kullanıcı adı, şifre ve domain zorunludur!", Toast.LENGTH_LONG).show()
                return
            }

            prefs.username = username
            prefs.password = password
            prefs.domain = domain
            prefs.port = etPort.text.toString().trim().toIntOrNull() ?: 5060
            prefs.transport = spinnerTransport.selectedItem.toString()
            prefs.doorDtmfCode = etDtmfCode.text.toString().trim().ifEmpty { "#" }
            prefs.autoAnswerDelay = etAutoAnswer.text.toString().trim().toIntOrNull() ?: 0

            Toast.makeText(this@MainActivity, "Ayarlar kaydedildi!", Toast.LENGTH_SHORT).show()
            service?.registerAccount()
        }
    }

    private fun updateRegistrationStatus(state: RegistrationState) {
        val (text, color) = when (state) {
            RegistrationState.Ok -> "✅ Bağlı" to getColor(android.R.color.holo_green_dark)
            RegistrationState.Progress -> "⏳ Bağlanıyor..." to getColor(android.R.color.holo_orange_dark)
            RegistrationState.Failed -> "❌ Bağlantı Hatası" to getColor(android.R.color.holo_red_dark)
            RegistrationState.Cleared -> "⭕ Kayıt Silindi" to getColor(android.R.color.darker_gray)
            else -> "❓ Bilinmiyor" to getColor(android.R.color.darker_gray)
        }
        binding.tvStatus.text = text
        binding.tvStatus.setTextColor(color)
    }

    /** Batarya optimizasyonundan muaf tut — arka planda çalışmak için zorunlu */
    private fun requestBatteryOptimizationExemption() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                try {
                    startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:$packageName")
                    })
                } catch (e: Exception) {
                    // Bazı cihazlar bu intent'i desteklemiyor, manuel yönlendirme yap
                    Toast.makeText(
                        this,
                        "Ayarlar > Uygulamalar > SipDoor > Pil > Kısıtlama Yok seçin",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun requestRequiredPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA
        ).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), REQ_PERMISSIONS)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_PERMISSIONS) {
            val denied = permissions.zip(grantResults.toList())
                .filter { it.second != PackageManager.PERMISSION_GRANTED }
                .map { it.first }
            if (denied.isNotEmpty()) {
                Toast.makeText(this, "Bazı izinler reddedildi!", Toast.LENGTH_LONG).show()
            }
        }
    }

    companion object {
        private const val REQ_PERMISSIONS = 100
    }
}
