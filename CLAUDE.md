# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project overview

Ghost AI is an Android app (Kotlin + Jetpack Compose) that renders an animated ghost character driven by AGSL shaders, holds spoken conversations via OpenAI (LLM + optional TTS) and ElevenLabs (TTS), and can react by changing Philips Hue lights based on the ghost's detected emotion. Single-module app: `:app`, package `com.fourthwardai.ghostai`, namespace/applicationId `com.fourthwardai.ghostai`.

## Build & dev commands

- Build debug APK: `./gradlew assembleDebug`
- Run unit tests: `./gradlew test` (run a single test class: `./gradlew test --tests "com.fourthwardai.ghostai.SomeTest"`)
- Run instrumented tests: `./gradlew connectedAndroidTest`
- Lint/format check (Spotless + ktlint): `./gradlew spotlessCheck`
- Auto-format: `./gradlew spotlessApply`
- Install on a connected device/emulator: `./gradlew installDebug`

Spotless runs ktlint 1.0.1 over `**/*.kt` and `**/*.gradle.kts`, with `ktlint_standard_function-naming` disabled (Compose `@Composable` functions use PascalCase).

## Required local configuration

Several `BuildConfig` fields are populated from `local.properties` (never commit real keys):

```properties
OPENAI_API_KEY=sk-xxxx...
ELEVENLABS_API_KEY=eleven-xxxx...
HUE_BRIDGE_IP=192.168.x.x
HUE_USERNAME=...
```

These surface as `BuildConfig.OPENAI_API_KEY`, `BuildConfig.ELEVEN_LABS_API_KEY`, `BuildConfig.HUE_BRIDGE_IP`, `BuildConfig.HUE_USERNAME`, wired up in `di/NetworkModule.kt`. If `OPENAI_API_KEY` is missing, the app shows a "missing key" dialog (`MissingOpenAiKeyDialog.kt`) rather than crashing — `OpenAIService.isAvailable()` / `ElevenLabsService.isAvailable()` gate feature availability throughout.

## Architecture

### Conversation pipeline (the core loop)

`GhostViewModel` (Hilt `@HiltViewModel`, `AndroidViewModel`) is the central orchestrator. It runs two coroutine loops connected by `Channel`s:

1. `userInputChannel` (`UserInput.Voice` / `UserInput.Touch`) → `observeUserInputQueue()` appends to `conversationHistory`, calls `OpenAIService.getGhostReply(...)`, and forwards the `GhostReply` to...
2. `ghostResponseChannel` → `observeGhostResponseQueue()` stops the speech recognizer, appends the reply, updates the ghost's emotion/lights, and starts streaming TTS via either `OpenAIService` or `ElevenLabsService` (selectable in Settings; both implement the shared `AIService` interface in `service/AIService.kt`).

State machine: `ConversationState` (`Idle`, `UserTalking`, `GhostTalking`, `ProcessingTouch`) drives whether the recognizer should be (re)started — see `maybeRestartListening()`/`restartRecognizerWithDelay()`. `SpeechRecognizerManager` wraps Android's `SpeechRecognizer` with lifecycle/error-recovery callbacks that route back into the view model (`onUserSpeechStart/End`, `onSpeechRecognizerError`).

All UI-relevant state is exposed as a single `StateFlow<GhostUiState>` (`model/GhostUiState.kt`), which combines conversation state, current/target `Emotion` (for shader transitions), dialog visibility, and `VoiceSettings`.

`conversationHistory` is auto-summarized once it exceeds `MAX_NUM_MESSAGES` (`summarizeConversationIfNeeded`) by asking the LLM to compress older turns into a single summary message — keeps the context window bounded for long sessions.

### Personality & prompts

The ghost's persona ("Whisper") and system prompts live in `service/Prompts.kt`. The LLM is instructed to prefix every reply with `[Emotion: <Name>]`; `OpenAIService` parses this into an `Emotion` enum (`model/Emotions.kt`: Neutral/Angry/Happy/Sad/Spooky/Funny) that drives both shader transitions and Hue light color (`updateLightWithEmotion` in `GhostViewModel`).

### Shaders / rendering

`shaders/` contains AGSL shader definitions composed together in `MainGhost.kt` / `MainSky.kt` (body, mist, eyes, mouth, moon, lighting — `GhostBody.kt`, `Eyes.kt`/`EyesDark.kt`, `Mouth.kt`, `Moon.kt`, `Lighting.kt`). `Uniforms.kt` defines the shared AGSL uniform declarations and helper functions (hashing/noise) injected into each shader string. Shaders are driven by uniforms like `iTime`, `isSpeaking`, `uStartState`/`uTargetState`/`uTransitionProgress` (for cross-fading between emotions) and `uQuality`/`uFps` (perf scaling). `GhostWithMist.kt` is the Compose entry point that wires the shader `RuntimeShader`s into a `Canvas`/`AGSL` render pipeline and animates uniform values from `GhostUiState`. `oldui/` and `bookofshadersexamples/` hold superseded/experimental shader UI — prefer the `shaders`/`GhostWithMist` path for new work.

### Services (`service/`)

- `AIService` — shared interface for TTS-capable AI backends (`isAvailable`, `getVoices`, `getDefaultVoiceId`, `startStreamingSpeech`).
- `OpenAIService` — chat completions (`getGhostReply`) plus optional OpenAI TTS streaming.
- `ElevenLabsService` — higher-quality streaming TTS (preferred when its API key is present; see `GhostUiState.default()` which defaults to ElevenLabs).
- `HueLightService` — talks to a local Philips Hue bridge over HTTP (`bridgeIp`/`username` from `BuildConfig`) to set light state/color based on emotion.
- `TtsPreferenceService` — persists the user's chosen TTS service/voice via DataStore preferences.
- `TtsCallbacks` — shared `onStart`/`onEnd`/`onError` callbacks bridging streaming TTS playback to conversation state transitions.
- `ChatMessage` / `Prompts` — message types (`UserMessage`, `AssistantMessage`, `SystemMessage`) and system prompt strings sent to the LLM.

### Networking & DI

Networking uses Ktor (`network/KtorClient.kt` builds the shared `HttpClient` with content negotiation/kotlinx.serialization; OkHttp is used directly for ElevenLabs streaming). All services/clients are provided as Hilt singletons in `di/NetworkModule.kt`, constructed with `BuildConfig` API keys/credentials.

### UI

`MainActivity` sets up edge-to-edge/immersive mode, requests `RECORD_AUDIO` permission, and hosts the Compose tree (`MainScreen.kt` → `GhostWithMist`). `settings/SettingsDialog.kt` (opened via long-press) lets the user pick TTS service/voice (`VoiceSettings`, `TtsService` enum: `OPENAI` | `ELEVENLABS`). `model/DeviceSettings.kt`/`FormFactor.kt` and `LocalWindowClassSize` (in `ui/theme`) handle phone vs. tablet layout via `WindowSizeClass`.

## Notes

- Logging uses Timber; conversation/recognizer log lines are consistently prefixed `"CGH: "` for easy filtering in logcat.
- The package was recently renamed to `com.fourthwardai.ghostai` (see git history) — watch for stale references to the old package name when touching `BuildConfig`-dependent code (see the comment in `GhostApp.kt`).
