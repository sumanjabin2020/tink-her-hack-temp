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
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import org.vosk.android.StorageService
import java.io.IOException
import org.json.JSONObject
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
    private var speechService: SpeechService? = null
    private var voskModel: Model? = null
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
                    
                    // Bring app to foreground
                    if (context != null) {
                        val launchIntent = Intent(context, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                            putExtra("SOS_TRIGGERED", true)
                        }
                        context.startActivity(launchIntent)
                    }
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
        
        initializeVosk()
        
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
            speechService?.stop()
            speechService?.shutdown()
            speechService = null
            voskModel?.close()
            voskModel = null
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
            .setContentText("Listening for power button presses and voice keywords.")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
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
    
    private fun initializeVosk() {
        val sharedPrefs = getSharedPreferences("SOS_PREFS", MODE_PRIVATE)
        val isVoiceEnabled = sharedPrefs.getBoolean("voice_enabled", false)
        if (!isVoiceEnabled) return

        StorageService.unpack(this, "model", "model",
            { model: Model ->
                this.voskModel = model
                startListening()
            },
            { exception: IOException -> Log.e(TAG, "Failed to unpack the model: " + exception.message) }
        )
    }

    private fun startListening() {
        if (voskModel == null) return

        val sharedPrefs = getSharedPreferences("SOS_PREFS", MODE_PRIVATE)
        val isVoiceEnabled = sharedPrefs.getBoolean("voice_enabled", false)
        
        if (!isVoiceEnabled) {
            mainHandler.postDelayed({ startListening() }, 5000)
            return
        }

        if (speechService != null) {
            speechService?.stop()
            speechService?.shutdown()
            speechService = null
        }

        val secretKeyword = sharedPrefs.getString("voice_keyword", "")?.lowercase()?.trim()

        try {
            val recognizer = Recognizer(voskModel, 16000.0f)
            speechService = SpeechService(recognizer, 16000.0f)
            
            val listener = object : RecognitionListener {
                override fun onPartialResult(hypothesis: String) {
                    checkKeyword(hypothesis, secretKeyword)
                }

                override fun onResult(hypothesis: String) {
                    checkKeyword(hypothesis, secretKeyword)
                }

                override fun onFinalResult(hypothesis: String) {
                    checkKeyword(hypothesis, secretKeyword)
                    restartListening()
                }

                override fun onError(e: Exception) {
                    Log.e(TAG, "SpeechService error", e)
                    restartListening()
                }

                override fun onTimeout() {
                    restartListening()
                }
            }
            
            speechService?.startListening(listener)
            isListening = true
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create SpeechService", e)
            restartListening()
        }
    }

    private fun checkKeyword(hypothesis: String?, secretKeyword: String?) {
        if (secretKeyword.isNullOrEmpty() || hypothesis.isNullOrEmpty()) return
        
        try {
            // hypothesis is a JSON string from Vosk like {"text": "something"}
            val jsonObject = JSONObject(hypothesis)
            if (!jsonObject.has("text")) return
            
            val recognizedText = jsonObject.getString("text").lowercase()
            
            if (recognizedText.contains(secretKeyword)) {
                Log.d(TAG, "Voice keyword detected via Vosk! Triggering SOS...")
                
                // Run background logic
                triggerSOSInBackground()
                
                // Stop listening immediately to prevent loops
                speechService?.stop()
                speechService?.shutdown()
                speechService = null
                isListening = false
                
                // Bring app to foreground - MUST be on main thread
                mainHandler.post {
                    val intent = Intent(this@EmergencyTriggerService, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        putExtra("SOS_TRIGGERED", true)
                    }
                    startActivity(intent)
                }
                
                // Wait a bit before restarting listening to avoid multiple triggers
                mainHandler.postDelayed({ startListening() }, 10000)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing Vosk JSON: $hypothesis", e)
        }
    }

    private fun restartListening() {
        isListening = false
        mainHandler.postDelayed({
            startListening()
        }, 1500)
    }
    
    private fun sendSms(numbers: List<String>, message: String) {
        val smsManager = SmsManager.getDefault()
        var sentCount = 0
        for (number in numbers) {
            try {
                smsManager.sendTextMessage(number, null, message, null, null)
                sentCount++
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send SMS to $number")
            }
        }
        
        if (sentCount > 0) {
            Log.d(TAG, "Successfully sent $sentCount SOS messages")
            mainHandler.post {
                android.widget.Toast.makeText(this, "Meow SOS successfully sent to contacts!", android.widget.Toast.LENGTH_LONG).show()
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                vibrator.vibrate(VibrationEffect.createOneShot(1000, VibrationEffect.DEFAULT_AMPLITUDE))
            }
        }
    }
}
