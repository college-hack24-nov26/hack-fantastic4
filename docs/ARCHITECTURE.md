# Proposed architecture

```text
Camera frames + optional motion sensors
             |
             v
      Android frame adapter
             |
             v
       VisionEngine interface
          /          \
       mock       future model adapter
          \          /
             v
    observations and scene data
          /             \
         v               v
 urgent safety path   conversational path
 short warning/TTS    scene guidance/TTS and UI
          \             /
           accessible application state
```

The diagram is a design proposal, not implemented code. The Android app should isolate camera capture and orientation handling from `VisionEngine`. A mock engine enables UI and accessibility work before a model is delivered. A future adapter should translate validated model outputs into agreed application-level contracts without exposing raw tensors to screens.

## Processing paths

The urgent safety path should evaluate fresh observations promptly, apply an agreed hazard policy, and present concise speech or haptic feedback. The conversational path can form longer scene descriptions and answer user requests. Scheduling, prioritization, interruption, stale-result handling, and false-alarm policy need measurement and joint review. A description must not block a warning.

The UI layer owns camera permissions, runtime capability checks, voice input and TextToSpeech lifecycle, TalkBack semantics, settings, and simulated emergency workflows. A real SOS action must disclose connectivity and permission limits and require an intentional user action.

The ML track owns model architecture and export. No input dimensions, normalization, tensor shapes, class mapping, runtime, or quantization scheme is selected here.
