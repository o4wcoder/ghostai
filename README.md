# 👻 Ghost AI

An interactive AI-powered ghost built with **Android + Jetpack Compose + shaders**.  
The ghost floats, speaks, listens, and reacts with spooky personality — perfect for Halloween setups or smart-home fun.

---

## 📸 Screenshots

_Add screenshots or screen recordings here._

Example layout:

| Main Ghost Screen | Conversation Flow | Settings |
|-------------------|-------------------|----------|
| ![Ghost Main](docs/images/screenshot_main.png) | ![Conversation](docs/images/screenshot_conversation.png) | ![Settings](docs/images/screenshot_settings.png) |

---

## 🔑 API Keys

This project uses two external services:

- **[OpenAI API](https://platform.openai.com/account/api-keys)** for conversation and text-to-speech.
- **[ElevenLabs API](https://elevenlabs.io/app/keys)** for high-quality ghost voices.

### Setup

1. Obtain keys from the links above.  
2. Add them to your `local.properties` file (never commit them!):

   ```properties
   OPENAI_API_KEY=sk-xxxx...
   ELEVENLABS_API_KEY=eleven-xxxx...
   ```

3. The app will load them through `BuildConfig` at runtime.  

> ⚠️ **Do not hardcode keys in your source code.** They should only live in `local.properties`.

---

## 🚀 Features

- Animated ghost visuals (Shaders + Compose Canvas).
- Real-time conversation loop (STT ↔ LLM ↔ TTS).
- Speech recognizer lifecycle management with error recovery.
- Personality system (spooky, playful, mischievous, etc.).
- Smart-home tie-ins (Philips Hue lights, optional).

---

## 🛠️ Tech Stack

- **Language:** Kotlin  
- **UI:** Jetpack Compose (Material 2), AGSL Shaders  
- **Media:** ExoPlayer + ElevenLabs TTS  
- **Networking:** Ktor with kotlinx.serialization  
- **Testing:** mockk + Turbine  
- **Logging:** Timber  

---

## 📦 Project Structure

```
/app
  /ui        # Ghost visuals, Compose screens
  /audio     # Speech recognizer, TTS, audio playback
  /llm       # OpenAI integration
  /vm        # GhostViewModel + state management
  /enhancements  # Feature roadmap & checklist
```

---

## 🧪 Running Locally

```bash
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

Target device: Pixel series (Android 13+ recommended).  

---

## 🎃 Roadmap

See [Enhancements Checklist](ghost_ai_enhancements_checklist.md) for upcoming features.  

---

## 📜 License

_MIT / Apache 2.0 — your choice here._
