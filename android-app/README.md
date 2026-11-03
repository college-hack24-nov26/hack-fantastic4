# Android application

This directory contains the Kotlin and Jetpack Compose Vision prototype. It has splash, onboarding, permissions explanation, voice setup, home, assistant, and live rear-camera preview screens. The camera preview has no visual detection or safety guidance. Sample observations remain explicitly simulated.

Inference remains behind `VisionEngine` with a mock implementation. The camera source and no-op safety policy are separate interfaces. CAMERA is requested when the preview starts; RECORD_AUDIO is requested when listening starts. Voice commands use Android's on-device `SpeechRecognizer` only, with no online-recognizer fallback. Output uses installed Android TextToSpeech voices reported as not requiring a network connection.

The optional conversational path uses LiteRT-LM 0.18.0. No language model is bundled or downloaded by the app. To enable it, place the exact `Qwen3-0.6B.litertlm` artifact from `litert-community/Qwen3-0.6B` on the phone, open the assistant, and choose **Import Qwen3-0.6B model**. The app copies the selected file to temporary private storage, checks its SHA-256 (`c5f39b4191926786c3e6668b62af4d6eb15248dfc7a5348252a9b406f33f2108`), and installs it only if the checksum matches. Expect roughly 614 MB of model storage plus runtime cache and memory overhead. Deterministic commands and visual-safety refusals remain outside the LLM path.

On a Samsung SM-E546B running Android 16, the imported model loaded, a typed request generated a reply, and one spoken request was transcribed and routed to the model. The recognizer reported confidence `0.0` for that usable transcript, so Vision does not reject a request solely for that score. The assistant provides typed input when speech recognition fails, and diagnostic logs record recognition stages and error codes without recording audio or transcript text. Recognition reliability, Hinglish recognition and pronunciation, response latency, and longer conversations still need device evaluation. The model has no camera input; its replies must not be used for navigation, obstacle detection, or safety decisions.

## Build setup

Open this directory as an Android Studio project or run `gradlew.bat :app:assembleDebug` on Windows. The project uses Android Gradle Plugin 9.0.1, Gradle 9.1.0, Kotlin 2.4.21, compile SDK 36, and Java 17 bytecode. The AGP 9 built-in Kotlin and new DSL are temporarily disabled because LiteRT-LM 0.18.0 was compiled with Kotlin 2.4 metadata. An Android SDK with platform 36 and a Java runtime supported by Gradle 9.1 are required. Set `ANDROID_HOME` or use an untracked `local.properties` to point to the SDK.

Inclusive Sans is not bundled because no font file was supplied. The app currently uses a system sans-serif fallback. The UI reference PNG files remain outside the repository in the laptop's `UI_Images` folder.

See [project context](../docs/PROJECT_CONTEXT.md) and [integration guidance](../docs/INTEGRATION.md).
