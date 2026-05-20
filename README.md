<div align="center">
  <img src="app/src/main/res/drawable/perfect_auditext_icon_1779293519366.png" width="128" height="128" alt="Auditext Logo">
  <h1>Auditext Studio 🎙️</h1>
  <p><strong>The Ultimate AI-Powered Voice Studio & Text-to-Speech Generation Engine for Android.</strong></p>

  <p>
    <a href="#-what-is-auditext">What is Auditext</a> •
    <a href="#-core-architecture--deep-concepts">Core Architecture</a> •
    <a href="#-key-features">Key Features</a> •
    <a href="#-deep-dive-into-core-technical-concepts">Deep Technical Concepts</a> •
    <a href="#-hindi--english-voice-optimization">Vocal Optimization</a> •
    <a href="#-byok-providers">BYOK Settings</a> •
    <a href="#-getting-started">Getting Started</a>
  </p>
</div>

---

## ⚡ What is Auditext?

**Auditext** is a professional-grade, unified Text-to-Speech (TTS) creation studio built for modern Android platforms. It bridges the gap between client-side rendering and state-of-the-art cloud AI audio models (Gemini, OpenAI, ElevenLabs, Deepgram, Cartesia, and high-performance Native offline systems). Designed around high performance, accessibility, and dynamic aesthetics, Auditext provides a smooth, human-like narration experience with a specific emphasis on Hindi and English linguistic perfection.

---

## 🏛️ Core Architecture & Deep Concepts

The framework is architected using the standard **MVVM (Model-View-ViewModel)** architectural pattern. It strictly separates concerns, handles multi-threaded and network-intensive jobs asynchronously via Kotlin Coroutines, and coordinates runtime components perfectly.

```
                  ┌─────────────────────────────────────┐
                  │          Jetpack Compose UI         │
                  │   (Reactive, Material 3, Themes)    │
                  └──────────────────┬──────────────────┘
                                     │
                                    Obs
                                    State
                                     ▼
                  ┌─────────────────────────────────────┐
                  │           StudioViewModel           │
                  │  (StateFlow, Playback Ticker, UI)     │
                  └──────────────────┬──────────────────┘
                                     │
                        ┌────────────┴────────────┐
                        │                         │
                        ▼                         ▼
            ┌───────────────────────┐   ┌──────────────────┐
            │      TtsManager       │   │    HistoryDao    │
            │ (Audio Orch / Native) │   │ (Local SQLite/Db)│
            └───────────┬───────────┘   └─────────┬────────┘
                        │                         │
        ┌───────────────┼───────────────┐         │
        ▼               ▼               ▼         ▼
  ┌───────────┐   ┌───────────┐   ┌───────────┐ ┌───┐
  │Gemini API │   │TwelveLabs │   │OpenAI API │ │   │
  │ (REST OS) │   │ (REST OS) │   │ (REST OS) │ │   │
  └───────────┘   └───────────┘   └───────────┘ └───┘
```

### 1. Unified State Flow & UDF (Unidirectional Data Flow)
The view relies entirely on `StateFlow` streams exposed by the `StudioViewModel`. Every setting modification, text key-in, audio buffer progress ticker, or active network generation trigger is funneled as a single immutable state. This avoids erratic race conditions and ensures the UI always mirrors the exact player and generation flags.

### 2. Audio Processing Pipeline & MediaPlayer Management
To prevent UI thread locks during heavy audio preparation or continuous stream caching:
* **`prepareAsync()` Isolation**: High-fidelity generated files or local cache targets are initialized through `MediaPlayer.prepareAsync()`. The system relies on callback-based `OnPreparedListener` sequences rather than blocking main thread sync wrappers.
* **Continuous Playback Tickers**: Active millisecond tracker tickers utilize targeted `kotlinx.coroutines.delay` runs on the CPU, only running while the status state evaluates to `"playing"`.
* **Resource Disposals**: Mediaplayers are automatically released when leaving active screens or disposing Compositions, securing clean heap structures and zero audio device leakage.

### 3. Bring-Your-Own-Key (BYOK) Dynamic Rest Orchestrator
With Auditext's secure key management pipeline:
* Keys are validation-gated before saving.
* Each integration (e.g., Gemini REST client) uses robust, light `OkHttpClient` requests mapped and handled on `Dispatchers.IO` threads.
* If custom keys are not configured or are deleted by the user, the studio auto-reverts to System Offline TTS rendering, preventing any unexpected crashes.

---

## 🔥 Key Features

* **🇮🇳 Perfect Dual-Language Delivery**: Specially calibrated language bindings that ensure both English and Hindi sounds natively fluent, eliminating robotic truncation, speech pauses, and non-natural tone shifts.
* **🎭 Comprehensive Multi-Provider Architecture**: Direct integration pathways for **Native (Offline)**, **Gemini AI**, **OpenAI**, **ElevenLabs**, **Cartesia**, and **Deepgram**.
* **📁 Smart Document Imports**: Direct support to load and compile `.txt` contents into the editor with accurate character count readouts.
* **🧠 Auto-Detected Emotional Nuance**: Integration with a central Gemini parser that scans draft texts for primary emotional triggers (*Joy, Fear, Determination, Professionalism, Calmness*) and adjusts tone parameters dynamically.
* **💾 Local History Registry (Room)**: Offline persistence keeping records of all generated scripts securely.
* **🎨 Fully Responsive & Custom Theme Modes**: Responsive layout grids, dynamic padding margins, and a dedicated selector for **System Default**, **Light Style (ambient #FDF4FF)**, and **Dark Style** modes.

---

## 🧠 Deep Dive into Core Technical Concepts

### 📂 File Structure Overview

* **`com.example.MainActivity`**: Setups Edge-to-Edge display support, wraps the Compose layer in `MyApplicationTheme`, and handles navigation routers.
* **`com.example.tts.TtsManager`**: Encapsulates standard native platform TextToSpeech controls. Imparts customized locale configurations (`Locale("hi", "IN")`) to enable perfectly articulated Indian accents.
* **`com.example.viewmodel.StudioViewModel`**: Orchestrates state variables like playback, speed, current text indices, download flags, DB queries, and API triggers.
* **`com.example.data.AppDatabase`**: Handles Room persistence layers. Manages local history inputs and audio files saved local to storage directories.
* **`com.example.ui.screens`**: 
  * `StudioScreen`: Visual command deck with voice chipsets, inputs, playback controls, and emotion toggles.
  * `SettingsScreen`: Multi-provider setup, API keys configurations, and dynamic downloads.
  * `HistoryScreen`: Interactive registry showing all generated tracks.
  * `WelcomeScreen`: Dynamic welcome portal with visual pulse animation loops.

### 📜 Real-time Non-blocking Ticker Mechanics
In `StudioViewModel.kt`, playback updates are maintained using pure coroutines:
```kotlin
private var tickerJob: Job? = null

fun startTicker() {
    stopTicker()
    tickerJob = viewModelScope.launch {
        while (isActive) {
            mediaPlayer?.let { mp ->
                if (mp.isPlaying) {
                     _playbackPosition.value = mp.currentPosition
                }
            }
            delay(100) // update progressive seek elements smoothly
        }
    }
}
```

---

## 🇮🇳 Hindi & English Voice Optimization

Standard offline TTS engines frequently struggle with Indian accents, causing choppy pronunciations and bad tone shifts. Auditext solves this through customized internal rules:
1. **Locale Specification**: Uses `Locale("hi", "IN")` instead of generic language codes to invoke the native high-fidelity Indian-spoken assets.
2. **Specialized Voice IDs**: Configured to match the absolute best locale-matched voices accessible on Android OS devices.
3. **Pronunciation Mapping and Spacing**: Eliminates trailing/leading pauses in multi-byte devanagari syntax so Hindi and English blend seamlessly in mixed narratives.

---

## 🛠️ BYOK Providers Setup

To initialize your own cloud voice outputs:
1. Tap the **Settings icon (⚙️)** in the top app bar.
2. Select your provider (e.g., **Gemini**, **OpenAI**, **ElevenLabs**).
3. Input your API key and tap **Validate & Save**. 
4. The studio evaluates your keys seamlessly. If key inputs are empty, it switches back to offline rendering cleanly with zero crashes.

---

## 📬 Support & Feedback

Need help with Auditext Studio, setting up custom keys, or wanting to recommend features?

*   **📧 Email Support:** For technical support or collaborations, contact **rehan515ahmad@gmail.com** anytime!
*   **🔗 Developer Connection:** Reach out via [LinkedIn Profile](https://www.linkedin.com/in/rehan-ahmad-863386382?utm_source=share_via&utm_content=profile&utm_medium=member_android).

---

## 🚀 Getting Started

### Prerequisites
* **Android Studio Ladybug** or newer.
* **JDK 17** set as standard compiler target.
* Android device running API 26 or above.

### Manual Run
1. Download code and launch in Android Studio.
2. Allow Gradle sync to complete and assemble the dependencies.
3. Run the project in the emulator or structural device profile using `Shift + F10`.

---

<div align="center">
  <i>Developed by <b>Rehan Ahmad</b> with robust Material 3 aesthetics and asynchronous state machines. Leave a ⭐ to support the repository!</i>
</div>
