# 👻 Ghost AI

Ghost AI is an interactive, **AI-powered character** built with **Android + Jetpack Compose + shaders**.  
At its core, Ghost AI combines **conversational AI**, **natural voice synthesis**, and **dynamic visuals** to create a spooky but playful ghost companion.

- 🧠 **Conversational AI:** The ghost uses the OpenAI API to hold natural back-and-forth conversations. Memory mechanisms allow it to stay context-aware within a session, so interactions feel more continuous and personal. Ghost reacts visually with eyes and mouth depending on the LLM emotional response.  
- 🎙️ **AI Voice:** ElevenLabs TTS brings the ghost to life with a high-quality, expressive synthetic voice. The ghost doesn’t just talk—it *performs*.  Mutliple voice options to choose from through the settings menu (long press on the screen). There is also an option to use OpenAI TTS Voice service if you don't want to pay for ElevenLabs. OpenAI TTS is a bit slower with less expressive voices.
- 🕯️ **Visual FX with Shaders:** The ghost’s body, mist, eyes, and glow are powered by **AGSL shaders**, animated in real-time for floating, blinking, and glowing effects that react to conversation states.  
- 🔄 **STT ↔ AI ↔ TTS Loop:** A smooth pipeline connects speech recognition, AI responses, and voice output, so the ghost can listen, think, and talk naturally—without awkward pauses.  

The result is an immersive, animated AI character that can listen, respond, and spook audiences in real time—perfect for Halloween setups, parties, or just experimenting with interactive AI characters.

---

## 📸 Screenshots

| Main Ghost Screen | Settings |
|-------------------|----------|
|<img width="1080" height="2400" alt="Screenshot_20250930_185157" src="https://github.com/user-attachments/assets/f7b2901b-23f1-4282-b966-d1067d42b210" />|<img width="1080" height="2400" alt="Screenshot_20250930_185308" src="https://github.com/user-attachments/assets/0f7fca8c-ba9c-42eb-a522-1f330f6a4ad4" />|

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
- **UI:** Jetpack Compose (Material 3), AGSL Shaders  
- **Media:** ExoPlayer + ElevenLabs TTS  
- **Networking:** Ktor with kotlinx.serialization   

---

## 🎃 Roadmap

See [Enhancements Checklist](ghost_ai_enhancements_checklist.md) for upcoming features.  

---

