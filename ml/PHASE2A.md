# Phase 2A: bounded detector improvement

## Scope and findings

The detector remains Torchvision SSDLite320-MobileNetV3 Large with
`weights=None` and `weights_backbone=None`. The first run stopped after three
epochs and selected epoch 1. Inspection found no class-ID, RGB conversion,
box-coordinate, train/eval-mode, or checkpoint-selection bug. The model's
Torchvision transform resizes to 320 pixels and applies its internal
normalization. The dataset loader provides RGB floats in `[0, 1]` and boxes in
pixel `xyxy` coordinates. Training uses SGD with momentum 0.9.

The first run produced 300 detections on every validation image, the model's
configured output limit. Scores were mostly near 0.1, with substantial
same-class overlap. Its validation AP50 at score 0.05 was 0.01313. Raising
the threshold to 0.25 removed nearly every matched detection, so threshold
tuning alone did not address weak learning. The Open Images boxes are also
incomplete for some visible selected-class objects. For example, validation
image 1238 visibly contains a large foreground car that is absent from its
two car annotations. Image 1156 contains a large foreground car but has only
person annotations. The current evaluator has no verified ignore regions or
per-image exhaustive-label flags, so some apparent false positives may be
unlabeled objects. It must not be interpreted as a complete error audit.

## Change and experiment

Training now records classwise validation metrics each epoch and supports
`early_stopping_start_epoch` and `early_stopping_enabled`. The warm-up prevents
the early three-epoch stop that limited the first run. Separate `last.pt` and
validation-selected `best.pt` remain. The second run changed only training
duration and early stopping; it retained learning rate 0.001 to isolate this
change. No scheduler, augmentation, resume, or pretrained weights were added.

The tracked template is `configs/second_experiment.json`. Private data paths
were supplied in an ignored local copy. Configuration: seed 42, 1,130 approved
Open Images V7 training images, 209 validation images, batch size 2, 15 maximum
epochs, 320-pixel model input, SGD momentum 0.9, validation each epoch, AP50
selection at score 0.05 and IoU 0.5, early stopping beginning at epoch 10
with patience 3. The held-out 199-image test split was isolated from training
and checkpoint selection.

| Epoch | Train loss | Validation AP50 |
| ---: | ---: | ---: |
| 1 | 8.1541 | 0.01082 |
| 2 | 7.0169 | 0.01506 |
| 3 | 6.8300 | 0.01404 |
| 4 | 6.6144 | 0.01630 |
| 5 | 6.4667 | 0.02425 |
| 6 | 6.3233 | 0.03131 |
| 7 | 6.2559 | 0.04241 |
| 8 | 6.1331 | 0.04056 |
| 9 | 6.1814 | 0.02588 |
| 10 | 6.0227 | 0.04718 |
| 11 | 5.8873 | 0.06135 |
| 12 | 5.9459 | 0.04866 |
| 13 | 5.9066 | 0.07467 |
| 14 | 5.7062 | **0.10105** |
| 15 | 5.5642 | 0.08583 |

Epoch 14 was selected using validation only. The training and validation pass
took 897.8 seconds total. Peak CUDA allocated memory was 245.5 MiB and peak
reserved memory was 262 MiB as reported by PyTorch. These measurements exclude
other GPU consumers and are not an Android runtime benchmark.

## Evaluation

This is mean per-class interpolated AP at IoU 0.5, not COCO mAP over multiple
IoU thresholds. The same score threshold 0.05 and IoU threshold 0.5 were used
for both runs. The baseline test set had already been evaluated in Phase 1F;
the Phase 2A checkpoint was evaluated on it once after validation selection.

| Split | Baseline AP50 | Second run AP50 |
| --- | ---: | ---: |
| Validation | 0.01313 | 0.10105 |
| Held-out test | 0.01124 | 0.09421 |

| Class | Validation AP50 | TP / FP / FN | Test AP50 | TP / FP / FN |
| --- | ---: | --- | ---: | --- |
| Person | 0.02527 | 81 / 21,977 / 109 | 0.03514 | 73 / 20,965 / 111 |
| Bicycle | 0.04488 | 34 / 1,761 / 35 | 0.06007 | 32 / 1,544 / 29 |
| Motorcycle | 0.18536 | 45 / 1,431 / 21 | 0.14826 | 33 / 1,328 / 13 |
| Car | 0.24705 | 68 / 18,864 / 35 | 0.22050 | 71 / 18,314 / 32 |
| Chair | 0.00267 | 30 / 18,409 / 57 | 0.00707 | 27 / 17,313 / 74 |

At validation threshold 0.1, AP50 was 0.10088. At threshold 0.25 it fell to
0.07321; only 53 matched detections remained across all classes. At 0.5 it
fell to 0.02722. Median confidence by class at the selected checkpoint was
0.117 person, 0.109 bicycle, 0.111 motorcycle, 0.109 car, and 0.135 chair.
All 209 validation images still reached the 300-detection output limit.

Representative validation examples: image 1275 had a matched car detection
with score 0.682 and IoU 0.809. Image 1238 had a car detection with score
0.786 and IoU 0.165 against the available annotations; visual review showed
the large foreground car was unboxed. Image 1156 similarly has an unboxed
foreground car. These examples show both improvement and why raw false-positive
counts overstate errors on partially labeled images. Missing chair detections
and low chair AP50 remain substantial failures.

The new model improves the measured AP50 but remains unsuitable for active
walking guidance or confident spoken safety instructions. Android integration
remains experimental and disabled. Future work needs a more exhaustive
evaluation set and substantial detector improvement before safety use.
