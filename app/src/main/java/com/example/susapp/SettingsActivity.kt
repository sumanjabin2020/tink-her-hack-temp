package com.example.susapp

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Switch
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val customMessageInput = findViewById<EditText>(R.id.settingsCustomMessage)
        val contactsInput = findViewById<EditText>(R.id.settingsContacts)
        val voiceKeywordInput = findViewById<EditText>(R.id.settingsVoiceKeyword)
        val voiceSwitch = findViewById<Switch>(R.id.switchVoiceSOS)
        val saveButton = findViewById<Button>(R.id.saveSettingsButton)

        val sharedPrefs = getSharedPreferences("SOS_PREFS", MODE_PRIVATE)
        
        // Load existing
        val currentMessage = sharedPrefs.getString("custom_message", "Meow! Emergency! Please help!")
        customMessageInput.setText(currentMessage)
        
        val currentContacts = sharedPrefs.getString("saved_numbers", "")
        contactsInput.setText(currentContacts)
        
        val currentKeyword = sharedPrefs.getString("voice_keyword", "")
        voiceKeywordInput.setText(currentKeyword)
        
        val isVoiceEnabled = sharedPrefs.getBoolean("voice_enabled", false)
        voiceSwitch.isChecked = isVoiceEnabled

        saveButton.setOnClickListener {
            val newMessage = customMessageInput.text.toString()
            val newContacts = contactsInput.text.toString()
            val newKeyword = voiceKeywordInput.text.toString()
            val newVoiceEnabled = voiceSwitch.isChecked
            
            sharedPrefs.edit()
                .putString("custom_message", newMessage)
                .putString("saved_numbers", newContacts)
                .putString("voice_keyword", newKeyword)
                .putBoolean("voice_enabled", newVoiceEnabled)
                .apply()
                
            Toast.makeText(this, "Settings Saved! Meow.", Toast.LENGTH_SHORT).show()
            finish() // Close settings
        }
    }
}
