package com.example.susapp

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Vibrator
import android.os.VibrationEffect
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.telephony.SmsManager
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

class EmergencyTriggerService : Service() {

    private val CHANNEL_ID = "PowerButtonSOSChannel"
    private val TAG = "EmergencyTriggerService"
    
    // Voice Listening Logic
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    private val mainHandler = Handler(Looper.getMainLooper())
    
    // Counting logic
    private var powerPressCount = 0
    private var lastPressTime = 0L
    private val TIMEOUT = 3000L // 3 seconds to press 5 times

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent == null) return
            
            val action = intent.action
            if (action == Intent.ACTION_SCREEN_ON || action == Intent.ACTION_SCREEN_OFF) {
                // Record press
                val currentTime = System.currentTimeMillis()
                
                if (currentTime - lastPressTime > TIMEOUT) {
                    powerPressCount = 1 // Reset if too slow
                } else {
                    powerPressCount++
                }
                
                lastPressTime = currentTime
                
                Log.d(TAG, "Screen toggle detected. Count: $powerPressCount")

                if (powerPressCount >= 5) {
                    Log.d(TAG, "SOS Triggered via Power Button!")
                    powerPressCount = 0 // Reset
                    triggerSOSInBackground()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            startForeground(
                1, 
                createNotification(), 
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION or android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                1, 
                createNotification(), 
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(1, createNotification())
        }
        
        initializeSpeechRecognizer()
        
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        registerReceiver(screenReceiver, filter)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(screenReceiver)
        
        mainHandler.post {
            speechRecognizer?.destroy()
            speechRecognizer = null
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "SOS Background Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitors power button for SOS emergency."
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Meow SOS Active")
            .setContentText("Monitoring power button presses for emergencies.")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun triggerSOSInBackground() {
        val sharedPrefs = getSharedPreferences("SOS_PREFS", MODE_PRIVATE)
        val numbersText = sharedPrefs.getString("saved_numbers", "")
        
        if (numbersText.isNullOrEmpty()) return
        
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        vibrator.vibrate(VibrationEffect.createOneShot(1000, VibrationEffect.DEFAULT_AMPLITUDE))
        
        val numbers = numbersText.split(",").map { it.trim() }
        val customMessage = sharedPrefs.getString("custom_message", "Meow! Emergency! Please help!") ?: "Emergency Response"
        
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.lastLocation.addOnSuccessListener { lastLoc ->
                if (lastLoc != null) {
                    val locationLink = " Loc: https://maps.google.com/?q=${lastLoc.latitude},${lastLoc.longitude}"
                    sendSms(numbers, "$customMessage$locationLink")
                } else {
                    // Fallback to current location if last is null
                    fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                        .addOnSuccessListener { location ->
                            val locationLink = if (location != null) {
                                " Loc: https://maps.google.com/?q=${location.latitude},${location.longitude}"
                            } else {
                                " (Loc unavailable)"
                            }
                            sendSms(numbers, "$customMessage$locationLink")
                        }.addOnFailureListener {
                            sendSms(numbers, "$customMessage (Loc unavailable)")
                        }
                }
            }.addOnFailureListener {
                val fallbackIntent = Intent(Intent.ACTION_VIEW)
                sendSms(numbers, "$customMessage (Loc unavailable)")
            }
        } else {
            sendSms(numbers, "$customMessage (Loc unavailable)")
        }
    }
    
    private fun initializeSpeechRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Log.e(TAG, "Speech recognition not available")
            return
        }

        mainHandler.post {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onError(error: Int) {
                    isListening = false
                    restartListening()
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (matches != null) {
                        val sharedPrefs = getSharedPreferences("SOS_PREFS", MODE_PRIVATE)
                        val isVoiceEnabled = sharedPrefs.getBoolean("voice_enabled", false)
                        val secretKeyword = sharedPrefs.getString("voice_keyword", "")?.lowercase()?.trim()

                        if (isVoiceEnabled && !secretKeyword.isNullOrEmpty()) {
                            for (match in matches) {
                                if (match.lowercase().contains(secretKeyword)) {
                                    Log.d(TAG, "Voice keyword detected! Triggering SOS...")
                                    triggerSOSInBackground()
                                    break
                                }
                            }
                        }
                    }
                    isListening = false
                    restartListening()
                }

                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            startListening()
        }
    }

    private fun startListening() {
        val sharedPrefs = getSharedPreferences("SOS_PREFS", MODE_PRIVATE)
        val isVoiceEnabled = sharedPrefs.getBoolean("voice_enabled", false)
        
        if (!isVoiceEnabled) {
            // Check again periodically if it's disabled
            mainHandler.postDelayed({ startListening() }, 5000)
            return
        }

        if (!isListening && speechRecognizer != null) {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            }
            try {
                // Temporarily mute system beeps so it doesn't annoy the user
                val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                audioManager.adjustStreamVolume(AudioManager.STREAM_SYSTEM, AudioManager.ADJUST_MUTE, 0)
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, 0)
                audioManager.adjustStreamVolume(AudioManager.STREAM_NOTIFICATION, AudioManager.ADJUST_MUTE, 0)
                audioManager.adjustStreamVolume(AudioManager.STREAM_RING, AudioManager.ADJUST_MUTE, 0)
                audioManager.adjustStreamVolume(AudioManager.STREAM_ALARM, AudioManager.ADJUST_MUTE, 0)
                
                speechRecognizer?.startListening(intent)
                isListening = true
                
                // Unmute shortly after
                mainHandler.postDelayed({
                    audioManager.adjustStreamVolume(AudioManager.STREAM_SYSTEM, AudioManager.ADJUST_UNMUTE, 0)
                    audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0)
                    audioManager.adjustStreamVolume(AudioManager.STREAM_NOTIFICATION, AudioManager.ADJUST_UNMUTE, 0)
                    audioManager.adjustStreamVolume(AudioManager.STREAM_RING, AudioManager.ADJUST_UNMUTE, 0)
                    audioManager.adjustStreamVolume(AudioManager.STREAM_ALARM, AudioManager.ADJUST_UNMUTE, 0)
                }, 500)
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start listening", e)
                isListening = false
                restartListening()
            }
        }
    }

    private fun restartListening() {
        mainHandler.postDelayed({
            startListening()
        }, 1500) // Increase delay slightly to reduce battery and loop aggression
    }
    
    private fun sendSms(numbers: List<String>, message: String) {
        val smsManager = SmsManager.getDefault()
        for (number in numbers) {
            try {
                smsManager.sendTextMessage(number, null, message, null, null)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send SMS to $number")
            }
        }
    }
}
