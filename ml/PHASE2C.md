# Phase 2C: targeted sampling and Android export feasibility

## Accuracy intervention and controlled run

Phase 2B selected epoch 24 with validation AP50 0.13228. Person and chair
AP50 were 0.02777 and 0.03698. The 1,130-image training split has 450 person
images, 229 chair images, and 186 images with a person box smaller than 2% of
the image area. The single Phase 2C intervention was deterministic,
class-aware **training-only** sampling. Each training image has base weight 1,
plus 1 if it has a chair box and plus 1 if it has a small person box. The
sampler draws 1,130 images with replacement per epoch using seed 42.
Validation/test images and labels were unchanged. No external weights or
bounding-box augmentation were introduced.

The first seeded epoch drew 352 chair-image instances and 275 small-person
instances, versus 229 and 186 under one uniform pass. It drew only 684 unique
training images because sampling is with replacement. This reduced diversity
is a plausible contributor to the poor result; it is an inference, not a
measured causal explanation.

The private run continued from Phase 2B's epoch-24 checkpoint with model and
SGD optimizer state. The older checkpoint does not contain RNG state, so this
was not an exact resume. Architecture, 320-pixel input, batch size 2,
learning rate 0.001, momentum 0.9, and approved dataset were unchanged.
Configuration template: `configs/phase2c_focus_sampling.json`. Early stopping
was active from epoch 28 with patience 3; maximum was epoch 34.

| Epoch | Loss | Validation AP50 |
| ---: | ---: | ---: |
| 25 | 4.4774 | 0.11053 |
| 26 | 4.4533 | 0.10238 |
| 27 | 4.1772 | **0.12264** |
| 28 | 4.1678 | 0.10479 |
| 29 | 4.0817 | 0.10929 |
| 30 | 3.9412 | 0.10317 |

Early stopping ended the run at epoch 30. The best new epoch, 27, was below
the Phase 2B validation AP50 0.13228, so the Phase 2B checkpoint remains the
preferred `best.pt`. The Phase 2C run directory's `best.pt` is a byte-identical
copy of that checkpoint; its `last.pt` is epoch 30. The six additional epochs
took 342.3 seconds. PyTorch peak CUDA allocation was 245.6 MiB and peak
reservation 262 MiB.

| Class | Phase 2B AP50 | Best new epoch-27 AP50 | Change |
| --- | ---: | ---: | --- |
| Person | 0.02777 | 0.02285 | Worse |
| Bicycle | 0.05655 | 0.06121 | Better |
| Motorcycle | 0.26343 | 0.21962 | Worse |
| Car | 0.27666 | 0.29264 | Better |
| Chair | 0.03698 | 0.01692 | Worse |

At the fixed score threshold 0.05 and IoU 0.5, the best new epoch had 294
matched detections, 61,490 counted false positives, and 221 misses on
validation: micro precision 0.476% and recall 57.09%. Phase 2B had 0.464%
and 56.31%, but better rank-based AP50. Open Images' incomplete boxes limit
interpretation of false positives. The previously used held-out test set was
**not evaluated** for Phase 2C because validation did not select a new model.
Its last reported Phase 2B AP50 remains 0.11496.

## Export feasibility

The preferred Phase 2B epoch-24 checkpoint exported to fixed-shape ONNX
opset 17 using `vision_ml.export_onnx`. The export is 9,261,366 bytes and
passed `onnx.checker`. Input is one `float32` RGB tensor in NCHW shape
`[1,3,320,320]`, with channel values in `[0,1]`. Torchvision includes its
internal resize and per-channel mean/std of 0.5 in the graph. Outputs are
`boxes` float32 pixel `xyxy` in the 320×320 input frame, `scores` float32
class confidence, and `labels` int64 with IDs 1–5. Torchvision's NMS and
300-detection limit are inside the exported graph; optional Phase 2B output
filtering is separate. The exported input shape is fixed; arbitrary camera
frames must be resized before inference and boxes mapped back to the original
frame with the exact inverse transform.

To repeat the private export from `ml/`, install `requirements-export.txt` and
run `python -B -m vision_ml.export_onnx --checkpoint runs/phase2b/continuation/best.pt --output runs/phase2c/export/model.onnx --verify`.

On an identical seeded synthetic input, desktop ONNX Runtime CPU and PyTorch
both returned 300 detections. Labels were identical, maximum score error was
1.19e-7, and maximum box-coordinate error was 6.10e-5 pixels. This verifies
desktop numerical parity for one input, not Android compatibility or speed.
The ONNX file and verification log remain under ignored `runs/phase2c/export/`.

[ONNX Runtime's mobile guidance](https://onnxruntime.ai/docs/tutorials/mobile/)
documents Android Java/C++ packages with CPU inference. Its
[model usability checker](https://onnxruntime.ai/docs/tutorials/mobile/helpers/model-usability-checker.html)
found 34 NNAPI partitions covering 301 of 420 nodes and did **not** recommend
NNAPI for this graph. Unsupported NNAPI operators include
`NonMaxSuppression`, `If`, `TopK`, `NonZero`, and `GatherND`; dynamic-shaped
postprocessing is another concern. ONNX Runtime CPU executed the full graph
on Windows, making ONNX Runtime Mobile CPU the first Android feasibility route.
Actual Android loading, latency, memory, camera preprocessing, box mapping,
and optional output filtering remain untested. The export uses PyTorch's
legacy ONNX exporter for this detection graph; this is a compatibility risk
to revisit, not a reason to rewrite the detector during this sprint.

## Decision

The sampling intervention did not improve selected validation performance.
Keep the Phase 2B checkpoint as the preferred offline model candidate. Its
accuracy remains inadequate for active mobility guidance. The next action is
an Android **offline CPU loading and timing spike** with the exported ONNX
model, while separately obtaining more representative and exhaustively
annotated person/chair validation data. No Android deployment or additional
training is part of Phase 2C.
