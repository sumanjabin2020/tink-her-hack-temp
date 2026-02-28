package com.example.susapp

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Vibrator
import android.os.VibrationEffect
import android.speech.tts.TextToSpeech
import android.telephony.SmsManager
import android.view.View
import android.view.animation.Animation
import android.view.animation.RotateAnimation
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import java.util.Locale

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private val PERMISSION_REQUEST_CODE = 100
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    
    private lateinit var tts: TextToSpeech
    private var isTtsReady = false
    
    // UI elements
    private lateinit var settingsButton: ImageButton
    private lateinit var invisibilityButton: ImageButton
    private lateinit var sosButtonWrapper: View
    private lateinit var titleText: TextView
    private lateinit var actionRow: View
    private lateinit var resultImage: ImageView
    private lateinit var mascotImage: ImageView
    private lateinit var gestureOverlay: CircleGestureView
    private lateinit var invisibilityOverlay: View

    private var isInvisible = false

    // Goofy image cycler
    private val goofyImages = arrayOf(
        R.drawable.pleading_cat,
        R.drawable.cat_mic_cry,
        R.drawable.owl_cat,
        R.drawable.car,
        R.drawable.sniffles
    )
    private var mascotCycleIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        tts = TextToSpeech(this, this)

        bindViews()
        setupUIAndPrefs()
        setupListeners()
        checkAndRequestPermissions()
        
        if (intent?.getBooleanExtra("SOS_TRIGGERED", false) == true) {
            triggerPanicMode()
        }
    }
    
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra("SOS_TRIGGERED", false)) {
            triggerPanicMode()
        }
    }
    
    private fun startPowerButtonService() {
        val intent = Intent(this, EmergencyTriggerService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts.setLanguage(Locale.US)
            if (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED) {
                isTtsReady = true
            }
        }
    }

    private fun bindViews() {
        settingsButton = findViewById(R.id.settingsButton)
        invisibilityButton = findViewById(R.id.invisibilityButton)
        sosButtonWrapper = findViewById(R.id.sosButtonWrapper)
        titleText = findViewById(R.id.titleText)
        resultImage = findViewById(R.id.resultImage)
        mascotImage = findViewById(R.id.mascotImage)
        actionRow = findViewById(R.id.actionRow)
        gestureOverlay = findViewById(R.id.circleGestureView)
        invisibilityOverlay = findViewById(R.id.invisibilityOverlay)
    }

    private fun setupUIAndPrefs() {
        val sharedPrefs = getSharedPreferences("SOS_PREFS", MODE_PRIVATE)
        val isFirstTime = sharedPrefs.getBoolean("first_time", true)
        val savedNumbers = sharedPrefs.getString("saved_numbers", "")

        sosButtonWrapper.visibility = View.VISIBLE

        if (isFirstTime || savedNumbers.isNullOrEmpty()) {
            mascotImage.setImageResource(R.drawable.sniffles)
            Toast.makeText(this, "Please enter your emergency contacts to begin", Toast.LENGTH_LONG).show()
        } else {
            mascotImage.setImageResource(R.drawable.pleading_cat)
        }
    }

    private fun setupListeners() {
        val sharedPrefs = getSharedPreferences("SOS_PREFS", MODE_PRIVATE)

        settingsButton.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        invisibilityButton.setOnClickListener {
            isInvisible = true
            invisibilityOverlay.visibility = View.VISIBLE
            // Hide system UI temporarily
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
            
            Toast.makeText(this, "Invisibility Cloak ON. Tap screen twice to exit.", Toast.LENGTH_SHORT).show()
        }
        
        // Double tap on pitch black overlay to exit invisibility
        invisibilityOverlay.setOnClickListener(object : View.OnClickListener {
            var lastClickTime: Long = 0
            override fun onClick(v: View?) {
                val clickTime = System.currentTimeMillis()
                if (clickTime - lastClickTime < 500) {
                    // Double click
                    isInvisible = false
                    invisibilityOverlay.visibility = View.GONE
                    window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
                }
                lastClickTime = clickTime
            }
        })

        mascotImage.setOnClickListener {
            mascotCycleIndex = (mascotCycleIndex + 1) % goofyImages.size
            mascotImage.setImageResource(goofyImages[mascotCycleIndex])
            
            val msgs = arrayOf("Meow!", "Purrrr", "Hiss!", "Bruh", "Tuna?")
            Toast.makeText(this, msgs[mascotCycleIndex], Toast.LENGTH_SHORT).show()
        }

        sosButtonWrapper.setOnClickListener {
            val numbersText = sharedPrefs.getString("saved_numbers", "")
            if (numbersText.isNullOrEmpty()) {
                Toast.makeText(this, "No emergency contacts found. Please add them in Settings!", Toast.LENGTH_LONG).show()
                startActivity(Intent(this, SettingsActivity::class.java))
                return@setOnClickListener
            }

            val numbers = numbersText.split(",").map { it.trim() }
            val customMsg = sharedPrefs.getString("custom_message", "Meow! Emergency! Please help!") ?: "Emergency Response"
            sendEmergencyMessageWithLocation(numbers, customMsg)
            
            triggerPanicMode()
        }
        
        findViewById<View>(R.id.btnFakeCallLayout).setOnClickListener {
            Toast.makeText(this, "Fake call will start in 5 seconds...", Toast.LENGTH_SHORT).show()
            Handler(Looper.getMainLooper()).postDelayed({
                startActivity(Intent(this, FakeCallActivity::class.java))
            }, 5000)
        }
        
        gestureOverlay.onCircleDrawnListener = {
            revertToHomeState()
        }
    }

    private fun triggerPanicMode() {
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
        
        val rotate = RotateAnimation(-10f, 10f, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f)
        rotate.duration = 50
        rotate.repeatCount = 10
        rotate.repeatMode = Animation.REVERSE
        sosButtonWrapper.startAnimation(rotate)

        Handler(Looper.getMainLooper()).postDelayed({
            titleText.visibility = View.GONE
            sosButtonWrapper.visibility = View.GONE
            mascotImage.visibility = View.GONE
            actionRow.visibility = View.GONE
            settingsButton.visibility = View.GONE
            invisibilityButton.visibility = View.GONE
            
            resultImage.visibility = View.VISIBLE
            resultImage.setImageResource(R.drawable.tear_cat)
            
            gestureOverlay.visibility = View.VISIBLE
        }, 600)
    }
    
    private fun revertToHomeState() {
        gestureOverlay.visibility = View.GONE
        resultImage.visibility = View.GONE
        
        titleText.visibility = View.VISIBLE
        sosButtonWrapper.visibility = View.VISIBLE
        mascotImage.visibility = View.VISIBLE
        actionRow.visibility = View.VISIBLE
        settingsButton.visibility = View.VISIBLE
        invisibilityButton.visibility = View.VISIBLE
        
        mascotCycleIndex = 0
        mascotImage.setImageResource(goofyImages[0])
        Toast.makeText(this, "Crisis averted. Phew!", Toast.LENGTH_SHORT).show()
    }

    private fun checkAndRequestPermissions() {
        val permissionsNeeded = mutableListOf<String>()
        val requiredPerms = arrayOf(
            Manifest.permission.SEND_SMS,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.POST_NOTIFICATIONS,
            Manifest.permission.RECORD_AUDIO
        )
        for (perm in requiredPerms) {
            if (ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(perm)
            }
        }
        if (permissionsNeeded.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsNeeded.toTypedArray(), PERMISSION_REQUEST_CODE)
        } else {
            startPowerButtonService()
        }
    }

    private fun sendEmergencyMessageWithLocation(numbers: List<String>, baseMessage: String) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener { location ->
                    val locationLink = if (location != null) {
                        " Loc: https://maps.google.com/?q=${location.latitude},${location.longitude}"
                    } else {
                        " (Loc unavailable)"
                    }
                    sendSmsToNumbers(numbers, "$baseMessage$locationLink")
                }.addOnFailureListener {
                    sendSmsToNumbers(numbers, "$baseMessage (Loc unavailable)")
                }
        } else {
            sendSmsToNumbers(numbers, "$baseMessage (Loc unavailable)")
        }
    }

    private fun sendSmsToNumbers(numbers: List<String>, message: String) {
        val smsManager = SmsManager.getDefault()
        var sentCount = 0
        for (number in numbers) {
            try {
                smsManager.sendTextMessage(number, null, message, null, null)
                sentCount++
            } catch (e: Exception) {
                Toast.makeText(this, "Failed to send SMS to $number", Toast.LENGTH_SHORT).show()
            }
        }
        if (sentCount > 0) {
            Toast.makeText(this, "SOS message successfully sent to $sentCount contacts!", Toast.LENGTH_LONG).show()
            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            // Very long strong vibration to indicate success
            vibrator.vibrate(VibrationEffect.createOneShot(1000, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            Toast.makeText(this, "Failed to send any SOS messages.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                Toast.makeText(this, "Permissions Granted", Toast.LENGTH_SHORT).show()
                startPowerButtonService()
            } else {
                Toast.makeText(this, "Some Permissions Denied", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        if (isTtsReady) {
            tts.stop()
            tts.shutdown()
        }
    }
    
    override fun onResume() {
        super.onResume()
        val prefs = getSharedPreferences("SOS_PREFS", MODE_PRIVATE)
        val numbers = prefs.getString("saved_numbers", "")
        if (numbers.isNullOrEmpty()) {
            Toast.makeText(this, "Please set your emergency contacts first!", Toast.LENGTH_LONG).show()
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }
}