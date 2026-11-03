# Android application

This directory now contains the Phase 1A Kotlin and Jetpack Compose application foundation. It has splash, onboarding, permissions explanation, voice setup, home, and demo assistant screens. The assistant uses simulated text only; there is no camera capture or model inference.

Inference is behind `VisionEngine` with a mock implementation. A camera source and safety policy are separate interfaces. No camera or microphone permission is declared yet because those inputs are not active. The voice setup previews speech through an installed offline Android TextToSpeech voice when available. Model tensor details must wait for an agreed contract and compatible export.

## Build setup

Open this directory as an Android Studio project or run `gradlew.bat :app:assembleDebug :app:testDebugUnitTest` on Windows (`./gradlew` on macOS or Linux). The project uses Android Gradle Plugin 9.0.1, Gradle 9.1.0, compile SDK 36, and Java 17 bytecode. An Android SDK with platform 36 and a Java runtime supported by Gradle 9.1 are required. Set `ANDROID_HOME` or use an untracked `local.properties` to point to the SDK.

Inclusive Sans is not bundled because no font file was supplied. The app currently uses a system sans-serif fallback. The UI reference PNG files remain outside the repository in the laptop's `UI_Images` folder.

See [project context](../docs/PROJECT_CONTEXT.md) and [integration guidance](../docs/INTEGRATION.md).
