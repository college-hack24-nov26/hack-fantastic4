# Draft integration contracts

These four JSON Schemas are **proposals at version `0.1.0`**. They have not been approved by the ML track. They describe application-level interchange data, not raw model tensors. Consumers should validate `schemaVersion` and treat any incompatible change as a versioned contract change requiring joint review.

## Proposed conventions

- `timestampMs` is an integer count of milliseconds since the Unix epoch in UTC. It records the device's best available observation time, which can be wrong when its clock is wrong or offline. The source and handling of camera monotonic timestamps remain open.
- Optional `confidence` is a finite number from `0` to `1`, where higher means more confident. Absence means unavailable, not zero. Calibration and threshold policy remain open.
- Detection `boundingBox` coordinates are proposed as normalized values from `0` to `1`, with origin at the top left of the upright displayed frame. `left < right` and `top < bottom` are semantic rules that basic JSON Schema cannot express here. Rotation, mirroring, crop, and lens distortion handling need agreement.
- `label` and hazard `kind` are human-readable strings for this draft. No class IDs, label taxonomy, severity scale, physical distance, or safety conclusion is implied.
- Unknown fields are rejected by these draft schemas so additions can be reviewed and versioned. Future extensions require coordination.

## Open decisions for UI and ML review

1. Input image dimensions, color format, orientation, camera metadata, crop, and normalization.
2. Model architecture, output tensors, runtime, quantization, and device compatibility.
3. Class IDs, stable label mapping, localization, confidence calibration, and thresholds.
4. Camera timestamp conversion, clock skew, frame identity, and stale-result policy.
5. Bounding-box behavior across rotation, front camera mirroring, and ultrawide lenses.
6. Hazard taxonomy, urgency policy, suppression, and how uncertain observations are phrased.
7. Artifact packaging, supported hardware, performance targets, and validation evidence.

See [integration guidance](../docs/INTEGRATION.md) before implementing a real adapter.
