package com.example.susapp

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.util.Locale

class ActiveCallActivity : AppCompatActivity() {

    private lateinit var timerText: TextView
    private var secondsElapsed = 0
    private var isCallActive = false
    private val handler = Handler(Looper.getMainLooper())

    private val timerRunnable = object : Runnable {
        override fun run() {
            if (isCallActive) {
                secondsElapsed++
                updateTimerText()
                handler.postDelayed(this, 1000)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_active_call)
        
        // Force screen to stay on
        window.addFlags(
            android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            android.view.WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
            android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
        )

        timerText = findViewById(R.id.timerText)
        val btnEndCall = findViewById<FloatingActionButton>(R.id.btnEndCall)

        btnEndCall.setOnClickListener {
            endCall()
        }

        // Start Call Timer
        isCallActive = true
        handler.post(timerRunnable)
    }

    private fun updateTimerText() {
        val minutes = secondsElapsed / 60
        val seconds = secondsElapsed % 60
        timerText.text = String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }

    private fun endCall() {
        isCallActive = false
        handler.removeCallbacks(timerRunnable)
        timerText.text = "Call Ended"
        
        // Minor delay before closing to simulate real phone
        handler.postDelayed({
            finish()
        }, 1500)
    }

    override fun onDestroy() {
        super.onDestroy()
        isCallActive = false
        handler.removeCallbacks(timerRunnable)
    }
}
