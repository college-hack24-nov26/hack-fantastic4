# Project context

Vision aims to provide offline-first visual assistance for people with visual impairments on Android. The intended experience is voice-first, with accessible touch controls and readable, scrollable content. It should help users interpret nearby scenes while making uncertainty and system limits clear. It is an aid, not a substitute for mobility skills, a certified safety device, or emergency services.

## Responsibilities

The UI track owns the Kotlin Android app, Jetpack Compose screens, TalkBack support, TextToSpeech, voice interaction, camera and motion-sensor integration, local settings, emergency UI, and the Android-side inference boundary. The ML track owns datasets, annotation, training, evaluation, architecture, optimization, and export. Both tracks jointly review integration contracts and model delivery requirements.

The `ui` and `ml` branches are independent development tracks. `main` is stable integration. Shared files in `contracts/`, `docs/`, and `models/` require coordination.

## Product principles

- Keep core observation and guidance usable without network access when a compatible on-device model is available.
- Prefer speech and accessible controls; provide TalkBack labels, logical focus order, sufficient contrast, and alternatives to voice input.
- Keep urgent warnings short and separate from conversational descriptions so a long response cannot delay a time-sensitive message.
- Do not state physical distance or certain safety from unvalidated visual inference.
- Treat camera, microphone, ultrawide lens, and motion sensors as capabilities to detect at runtime, not guaranteed hardware.
- Ask for necessary permissions in context and keep personal recordings and imagery private by default.
- Provide an emergency flow that states its actual capabilities. Calls or messages may require cellular service, network access, permissions, and user action; offline operation cannot guarantee contact with responders.

No Android application, inference engine, or emergency communication is implemented in this foundation.
