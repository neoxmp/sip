package com.sipdoor.app

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.sipdoor.app.databinding.ActivityOngoingCallBinding
import org.linphone.core.Call
import java.util.Locale

/**
 * Aktif çağrı ekranı.
 * - Fanvil i66'dan gelen görüntüyü gösterir
 * - "Kapıyı Aç" butonu DTMF gönderir
 * - Sessize alma ve hoparlör kontrolü
 */
class OngoingCallActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOngoingCallBinding
    private val service get() = SipDoorApplication.instance.linphoneService
    private val prefs by lazy { SipPreferences(this) }

    private var isMuted = false
    private var isSpeaker = false
    private var callDurationSeconds = 0
    private val durationHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        binding = ActivityOngoingCallBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupCallInfo()
        setupButtons()
        setupCallStateListener()
        startDurationCounter()

        // Linphone video görüntüsü için SurfaceView ata
        service?.let { svc ->
            svc.getCurrentCall()?.let { call ->
                // Uzak (karşı taraf - Fanvil kamera) görüntüsü
                call.nativeVideoWindowId = binding.videoRemote
                // Yerel (telefon kamerası) görüntüsü
                call.nativePreviewWindowId = binding.videoLocal
            }
        }
    }

    private fun setupCallInfo() {
        val call = service?.getCurrentCall()
        val callerName = call?.remoteAddress?.displayName
            ?: call?.remoteAddress?.username
            ?: "Bilinmeyen"
        binding.tvCallerName.text = callerName
        binding.tvDtmfCode.text = "Kapı Kodu: ${prefs.doorDtmfCode}"
    }

    private fun setupButtons() {
        // 🚪 KAPI AÇ - Ana buton
        binding.btnOpenDoor.setOnClickListener {
            service?.openDoor()
            showDoorOpenFeedback()
        }

        // 📵 Çağrıyı Sonlandır
        binding.btnHangUp.setOnClickListener {
            service?.hangUp()
            finish()
        }

        // 🔇 Sessiz
        binding.btnMute.setOnClickListener {
            isMuted = service?.toggleMute() ?: false
            binding.btnMute.alpha = if (isMuted) 0.5f else 1.0f
            binding.btnMute.text = if (isMuted) "🔇 Açık" else "🎤 Sessiz"
        }

        // 🔊 Hoparlör
        binding.btnSpeaker.setOnClickListener {
            isSpeaker = service?.toggleSpeaker() ?: false
            binding.btnSpeaker.alpha = if (isSpeaker) 1.0f else 0.5f
            binding.btnSpeaker.text = if (isSpeaker) "🔊 Hoparlör" else "📱 Kulak"
        }

        // # DTMF Pad - özel kod gönderme
        setupDtmfPad()
    }

    private fun setupDtmfPad() {
        val dtmfButtons = mapOf(
            binding.btn0 to '0', binding.btn1 to '1', binding.btn2 to '2',
            binding.btn3 to '3', binding.btn4 to '4', binding.btn5 to '5',
            binding.btn6 to '6', binding.btn7 to '7', binding.btn8 to '8',
            binding.btn9 to '9', binding.btnStar to '*', binding.btnHash to '#'
        )
        dtmfButtons.forEach { (btn, digit) ->
            btn.setOnClickListener {
                service?.sendDtmf(digit)
                binding.tvDtmfInput.append(digit.toString())
            }
        }
    }

    private fun showDoorOpenFeedback() {
        binding.tvDoorStatus.visibility = View.VISIBLE
        binding.tvDoorStatus.text = "🔓 Kapı Açıldı!"
        binding.tvDoorStatus.animate()
            .alpha(1f).setDuration(200)
            .withEndAction {
                Handler(Looper.getMainLooper()).postDelayed({
                    binding.tvDoorStatus.animate()
                        .alpha(0f).setDuration(500)
                        .withEndAction {
                            binding.tvDoorStatus.visibility = View.GONE
                        }.start()
                }, 2000)
            }.start()
    }

    private fun setupCallStateListener() {
        service?.onCallEnded = {
            runOnUiThread {
                finish()
            }
        }
    }

    private fun startDurationCounter() {
        val runnable = object : Runnable {
            override fun run() {
                callDurationSeconds++
                val minutes = callDurationSeconds / 60
                val seconds = callDurationSeconds % 60
                binding.tvDuration.text = String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
                durationHandler.postDelayed(this, 1000)
            }
        }
        durationHandler.postDelayed(runnable, 1000)
    }

    override fun onDestroy() {
        durationHandler.removeCallbacksAndMessages(null)
        service?.onCallEnded = null
        super.onDestroy()
    }
}
