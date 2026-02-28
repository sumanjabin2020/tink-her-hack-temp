<p align="center">
  <img src="./img.png" alt="Project Banner" width="100%">
</p>

# Meow SOS! 🐾🎯

## Basic Details

### Team Name: Team Meow

### Team Members
- Member 1: Suman Jabin - Ahalia School Of Engineering & Technology

### Hosted Project Link
[https://sumanjabin2020.github.io/tink-her-hack-temp/](https://sumanjabin2020.github.io/tink-her-hack-temp/)

### Project Description
Meow SOS is a highly functional yet delightfully goofy emergency safety app for Android. It combines critical safety features like GPS tracking and silent triggers with a playful cat theme to make personal safety accessible, discreet, and a little less intimidating.

### The Problem statement
Personal safety apps often feel clinical, scary, or are too slow to activate in real pressure situations. Many users forget how to use them or feel uncomfortable triggering "serious" alarms in uncertain situations.

### The Solution
We've built an app that disguises itself as a silly cat tool but packs powerful background services. With multiple "panic" triggers (Power Button, Voice Commands) and "social exit" features (Fake Calls), it provides a comprehensive safety net that you'll actually *want* to keep on your home screen.

---

## Technical Details

### Technologies/Components Used

**For Software:**
- **Languages used:** Kotlin, XML (Android), HTML5, CSS3 (Web)
- **Frameworks used:** Android SDK, Gradle
- **Libraries used:** Google Play Services Location (Fused Location Provider), SpeechRecognizer API (Android Speech), TextToSpeech.
- **Tools used:** Android Studio, Git, GitHub Pages, AI Image Generation.

---

## Features

- **🐾 5-Tap Power Pulse**: Rapidly press your phone's power button 5 times to instantly (and silently) fire off an SOS message with your location.
- **🎙️ Secret Voice Trigger**: Say your custom "Safe Word" (like *"Illuminati 101"*) and the app will trigger the SOS sequence in the background even if the screen is off.
- **📍 Precise GPS Sharing**: Every SOS message includes a real-time Google Maps link so your contacts know exactly where to find you.
- **🛡️ Invisible Multi-Shield**: An "Invisibility Mode" that turns your screen pitch black and hides the UI, making your phone look off while you use the app discreetly.
- **📞 The "Mom" Escape**: A realistic Fake Call simulator (from "Mom") with full incoming and active call screens to help you politely exit uncomfortable social situations.
- **⚙️ Secret Settings**: A hidden menu to customize your emergency contacts, your SOS message, and your secret voice keyword.

---

## Implementation

### For Software:

#### Building from Source
```bash
# Clone the repository
git clone https://github.com/sumanjabin2020/tink-her-hack-temp.git

# Build the APK via Gradle
./gradlew assembleDebug
```

#### Run
1. Install the `app-debug.apk` on your Android device.
2. Grant Location, SMS, and Microphone permissions.
3. Set your custom emergency contact in the Settings menu.
4. Try saying "Illuminati 101" or tapping the power button 5 times!

---

## Project Documentation

### Screenshots

![Screenshot1](https://raw.githubusercontent.com/sumanjabin2020/tink-her-hack-temp/main/github_pages_site/assets/hero_cat.png)
*The Goofy Hero Mascot guiding you through setup!*

![Screenshot2](https://raw.githubusercontent.com/sumanjabin2020/tink-her-hack-temp/main/github_pages_site/assets/stealth_cat.png)
*Stealth Mode: Making safety look like a turned-off screen.*

![Screenshot3](https://raw.githubusercontent.com/sumanjabin2020/tink-her-hack-temp/main/github_pages_site/assets/siren_dog.png)
*Angry Dog Alert settings for the ultimate deterrent.*

#### Diagrams

**System Architecture:**
The app runs a high-priority `EmergencyTriggerService` in the foreground. It listens for hardware interrupts (screen toggles) and audio buffers (speech recognition) simultaneously. When a trigger is matched, it queries the `FusedLocationProvider` for the best available coordinates before firing an `SmsManager` intent.

---

## Project Demo

### Video
[Add your demo video link here - YouTube, Google Drive, etc.]

*Demonstrates the 5-tap power trigger, the voice command detection, and the realistic fake call flow.*

---

## AI Tools Used

**Tool Used:** Antigravity AI (Google DeepMind)

**Purpose:** 
- Architecture design and planning of the background services.
- Implementation of the `SpeechRecognizer` logic and Location fallbacks.
- Creation of the high-fidelity GitHub Pages website.
- Generation of the goofy 3D cat assets.

**Key Prompts Used:**
- "Implement a background service that listens for 5 power button clicks."
- "Create a glassmorphism website for an Android app with goofy cats."
- "Mute the system and music streams while SpeechRecognizer restarts to avoid the beep sound."

---

## Team Contributions

- **Suman Jabin**: Project Vision, UI/UX Design, Testing on hardware, and Integration.
- **Antigravity AI**: Core logic implementation, Bug fixing, and Documentation.

---

## License

This project is licensed under the MIT License - see the LICENSE file for details.

---

Made with ❤️ and too many meows at TinkerHub! 🐾
