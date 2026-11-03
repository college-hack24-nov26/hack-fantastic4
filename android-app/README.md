# Android application

This directory contains the Kotlin and Jetpack Compose Vision prototype. It has splash, onboarding, permissions explanation, voice setup, home, assistant, and live rear-camera preview screens. The camera preview has no visual detection or safety guidance. Sample observations remain explicitly simulated.

Inference remains behind `VisionEngine` with a mock implementation. The camera source and no-op safety policy are separate interfaces. CAMERA is requested when the preview starts; RECORD_AUDIO is requested when listening starts. Voice commands use Android's on-device `SpeechRecognizer` only, with no online-recognizer fallback. Output uses installed Android TextToSpeech voices reported as not requiring a network connection.

The assistant handles basic offline commands for time, date, repeat, stop speaking, speech speed, language switching, and help. It does not run a conversational language model. It does not provide visual observations or route guidance. Speech recognition depends on an installed on-device Android recognizer for the selected language, and recognition quality requires testing on the target phone.

## Build setup

Open this directory as an Android Studio project or run `gradlew.bat :app:assembleDebug` on Windows. The project uses Android Gradle Plugin 9.0.1, Gradle 9.1.0, compile SDK 36, and Java 17 bytecode. An Android SDK with platform 36 and a Java runtime supported by Gradle 9.1 are required. Set `ANDROID_HOME` or use an untracked `local.properties` to point to the SDK.

Inclusive Sans is not bundled because no font file was supplied. The app currently uses a system sans-serif fallback. The UI reference PNG files remain outside the repository in the laptop's `UI_Images` folder.

See [project context](../docs/PROJECT_CONTEXT.md) and [integration guidance](../docs/INTEGRATION.md).
