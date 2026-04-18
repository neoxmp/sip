package com.sipdoor.app

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.sipdoor.app.databinding.ActivityIncomingCallBinding
import org.linphone.core.Call

/**
 * Gelen çağrı ekranı.
 * Kilit ekranı üzerinde açılır (Fanvil i66 kapı zili çaldığında görünür).
 */
class IncomingCallActivity : AppCompatActivity() {

    private lateinit var binding: ActivityIncomingCallBinding
    private val service get() = SipDoorApplication.instance.linphoneService
    private val prefs by lazy { SipPreferences(this) }
    private var autoAnswerHandler: Handler? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Kilit ekranı ve uyku modunun üzerinde göster
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )

        binding = ActivityIncomingCallBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupCallerInfo()
        setupButtons()
        setupCallStateListener()
        setupAutoAnswer()
    }

    private fun setupCallerInfo() {
        val call = service?.getCurrentCall()
        val callerName = call?.remoteAddress?.displayName
            ?: call?.remoteAddress?.username
            ?: "Bilinmeyen"
        val callerNumber = call?.remoteAddress?.asStringUriOnly() ?: ""

        binding.tvCallerName.text = callerName
        binding.tvCallerNumber.text = callerNumber
        binding.tvStatus.text = "Arıyor..."
    }

    private fun setupButtons() {
        // Sesli yanıtla
        binding.btnAnswer.setOnClickListener {
            autoAnswerHandler?.removeCallbacksAndMessages(null)
            service?.answerCall(withVideo = false)
            openOngoingCall()
        }

        // Görüntülü yanıtla
        binding.btnAnswerVideo.setOnClickListener {
            autoAnswerHandler?.removeCallbacksAndMessages(null)
            service?.answerCall(withVideo = true)
            openOngoingCall()
        }

        // Reddet
        binding.btnDecline.setOnClickListener {
            autoAnswerHandler?.removeCallbacksAndMessages(null)
            service?.declineCall()
            finish()
        }
    }

    private fun setupCallStateListener() {
        service?.onCallEnded = {
            runOnUiThread {
                finish()
            }
        }
    }

    /** Otomatik yanıtlama: ayarlarda saniye > 0 ise geri sayım başlat */
    private fun setupAutoAnswer() {
        val delay = prefs.autoAnswerDelay
        if (delay <= 0) return

        binding.tvAutoAnswer.text = "Otomatik yanıtlama: ${delay}sn"
        binding.tvAutoAnswer.visibility = android.view.View.VISIBLE

        autoAnswerHandler = Handler(Looper.getMainLooper())

        var remaining = delay
        val runnable = object : Runnable {
            override fun run() {
                remaining--
                binding.tvAutoAnswer.text = "Otomatik yanıtlama: ${remaining}sn"
                if (remaining <= 0) {
                    service?.answerCall(withVideo = false)
                    openOngoingCall()
                } else {
                    autoAnswerHandler?.postDelayed(this, 1000)
                }
            }
        }
        autoAnswerHandler?.postDelayed(runnable, 1000)
    }

    private fun openOngoingCall() {
        startActivity(
            Intent(this, OngoingCallActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        finish()
    }

    override fun onDestroy() {
        autoAnswerHandler?.removeCallbacksAndMessages(null)
        service?.onCallEnded = null
        super.onDestroy()
    }
}
