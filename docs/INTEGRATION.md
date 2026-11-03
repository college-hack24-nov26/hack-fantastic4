# Model integration plan

The Android application will first use a mock `VisionEngine` behind an interface. The mock may produce known test observations for UI development, but must not be represented as real detection. The shared schemas in [`contracts/`](../contracts/README.md) are draft application-level proposals, not a model I/O specification.

## Export and review workflow

1. UI and ML tracks agree on capture preprocessing, orientation, timestamps, labels, raw outputs, and the application-level mapping. Record contract versions and compatibility decisions.
2. ML track supplies an Android-compatible model artifact, manifest, checksum, labels, preprocessing and output specifications, runtime requirements, representative test fixtures, and evaluation results. Do not assume any of these exist yet.
3. Android track reviews device support, artifact integrity, memory, latency, battery behavior, camera format conversion, and failure handling before changing the real inference adapter.
4. Validate the adapter against agreed fixtures and on representative devices. Compare outputs with the ML reference implementation and test rotation, mirroring, stale frames, and missing capabilities.
5. Integrate through a reviewed pull request. Keep the mock available for deterministic UI development and demonstrations.

## Open decisions

- Image dimensions, color format, crop, orientation, lens metadata, and normalization.
- Tensor shapes and semantics, class ID and label mapping, confidence calibration, and bounding-box transforms.
- Runtime, quantization, minimum Android version, supported devices, artifact packaging, and update strategy.
- Timebase conversion and stale-frame threshold; performance and power targets.
- Hazard taxonomy, urgent-warning policy, scene wording, privacy boundaries, and emergency behavior.

Any breaking change to a shared contract requires a coordinated version update and compatibility test. Neither branch should silently redefine a shared field. The `0.1.0` schemas remain proposals until both tracks approve them.
