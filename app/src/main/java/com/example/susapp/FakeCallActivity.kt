package com.example.susapp

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.floatingactionbutton.FloatingActionButton

class FakeCallActivity : AppCompatActivity() {

    private var vibrator: Vibrator? = null
    private var isRinging = false
    
    // Using MediaPlayer for ringtone provides better control than Ringtone
    private var ringtonePlayer: android.media.MediaPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_fake_call)

        // Force screen to stay on
        window.addFlags(
            android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            android.view.WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
            android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )

        val btnAccept = findViewById<FloatingActionButton>(R.id.btnAccept)
        val btnDecline = findViewById<FloatingActionButton>(R.id.btnDecline)

        startRinging()

        btnAccept.setOnClickListener {
            stopRinging()
            // Launch the active call screen
            startActivity(Intent(this, ActiveCallActivity::class.java))
            finish()
        }

        btnDecline.setOnClickListener {
            stopRinging()
            finish()
        }
    }

    private fun startRinging() {
        if (isRinging) return
        isRinging = true

        // 1. Vibrate continuously
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibrator = vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        val pattern = longArrayOf(0, 1000, 1000) // wait 0, vibrate 1s, sleep 1s
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0)) // 0 = repeat
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(pattern, 0)
        }

        // 2. Play Default Ringtone loudly
        try {
            val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ringtonePlayer = android.media.MediaPlayer().apply {
                setDataSource(applicationContext, ringtoneUri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                isLooping = true
                prepare()
                start()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun stopRinging() {
        if (!isRinging) return
        isRinging = false
        
        vibrator?.cancel()
        
        ringtonePlayer?.let {
            if (it.isPlaying) {
                it.stop()
            }
            it.release()
        }
        ringtonePlayer = null
    }

    override fun onDestroy() {
        super.onDestroy()
        stopRinging()
    }
}
