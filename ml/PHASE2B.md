# Phase 2B: detector quality and bounded continuation

## Findings

Torchvision 0.29's SSDLite postprocessor applies softmax, an internal score
threshold of 0.001, at most 300 candidates **per class**, classwise NMS at
IoU 0.55, then a 300-detection **per image** cap. The 300 predictions seen
on every Phase 2A validation image came from this configured cap, not a
software defect. Phase 2B's selected model still returns 300 raw predictions
per image, and 299.1 per image at the fixed reporting threshold 0.05.
Median scores by class are 0.107 person, 0.094 bicycle, 0.097 motorcycle,
0.097 car, and 0.129 chair. An additional classwise NMS pass at IoU 0.4
reduced validation outputs to 172.1 per image and changed AP50 from 0.13228
to 0.13628 at score 0.05. This is an optional postprocessing operation;
the fixed baseline comparison retains Torchvision's original NMS behavior.

The label map is still 0 background and 1 person, 2 bicycle, 3 motorcycle,
4 car, 5 chair. The loader's RGB `[0, 1]` tensors, pixel `xyxy` boxes,
and Torchvision resize/normalization remain correct. The existing evaluator
does not know which Open Images objects were intentionally unboxed. Validation
image 1238 contains a large unboxed car; its new car prediction (score 0.965)
is counted as false against available boxes. Image 1293 is a motorcycle
close-up with a high-score car prediction (0.906), a more credible
misclassification. Raw false-positive counts therefore need visual review.

Chairs are not simply absent from training: 1,285 chair boxes appear in 229
training images, versus 87 boxes in 41 validation images. Their median
normalized box area is 1.68% in training and 5.11% in validation; train and
validation image/box distributions differ. Sampled validation overlays show
indoor chairs, partial occlusion, and some additional visible chairs without
boxes. In image 1230, a large annotated chair's closest prediction is class
4 (car) at score 0.103; its highest chair score is 0.207. In image 1142, a
chair region is classified as person and another as bicycle at low scores.
Low confidence and class confusion both contribute to chair misses.
Private overlays and prediction diagnostics are under ignored `runs/` paths.
Original annotations were not changed.

## Continuation experiment

The Phase 2A epoch-14 `best.pt` includes model and SGD optimizer state, and
there is no scheduler. Training now supports a checked continuation from that
checkpoint, restoring the optimizer including momentum. It verifies class
mapping, dataset paths, batch size, learning rate, seed, and epoch bound.
That older checkpoint lacks RNG state, so this is **not an exact replayable
resume**. The source weights have SHA256
`8099b999dde08c898af55ad83f4f381be9341eef67e2d756123840be4273ea6f`.

The tracked template is `configs/phase2b_continuation.json`; a private copy
supplied approved Open Images paths. The lineage began at selected epoch 14
and ran epochs 15–28, with a maximum of 30 total epochs. It used the same
1,130 training and 209 independent validation images, architecture, random
original initialization, 320-pixel input, batch size 2, SGD momentum 0.9,
learning rate 0.001, and seed 42. Early stopping began at epoch 22 with
patience 4. Validation AP50 at score 0.05 and IoU 0.5 selected `best.pt`.

| Epoch | Loss | Validation AP50 |
| ---: | ---: | ---: |
| 15 | 5.5816 | 0.09592 |
| 16 | 5.4182 | 0.10762 |
| 17 | 5.3733 | 0.09752 |
| 18 | 5.2189 | 0.10562 |
| 19 | 5.1336 | 0.10210 |
| 20 | 5.0172 | 0.08223 |
| 21 | 4.9648 | 0.09169 |
| 22 | 4.8485 | 0.10101 |
| 23 | 4.7929 | 0.11779 |
| 24 | 4.6736 | **0.13228** |
| 25 | 4.5182 | 0.12343 |
| 26 | 4.4548 | 0.12104 |
| 27 | 4.3326 | 0.12635 |
| 28 | 4.2413 | 0.12997 |

The run stopped after epoch 28; `best.pt` is epoch 24 and `last.pt` is epoch
28. The 14 additional training/validation epochs took 840.6 seconds. PyTorch
reported peak CUDA allocation 245.5 MiB and reservation 262 MiB. The new
best checkpoint SHA256 is
`b7355a393e5c6911672fb643749b75dbe91c02fa90afb10f20ecd65f6e0cc3b6`.

## Validation-only threshold review

The table uses the selected checkpoint, Torchvision's original output, IoU
0.5, and incomplete Open Images annotations. Precision and recall are micro
averages over the five selected classes. AP50 is mean per-class interpolated
AP at IoU 0.5, not COCO mAP over multiple IoUs.

| Score | Detections / image | TP / FP / FN | Precision | Recall | AP50 |
| ---: | ---: | --- | ---: | ---: | ---: |
| 0.05 | 299.1 | 290 / 62,231 / 225 | 0.46% | 56.31% | 0.13228 |
| 0.10 | 190.4 | 257 / 39,532 / 258 | 0.65% | 49.90% | 0.13135 |
| 0.15 | 47.8 | 180 / 9,806 / 335 | 1.80% | 34.95% | 0.12634 |
| 0.20 | 9.5 | 118 / 1,872 / 397 | 5.93% | 22.91% | 0.11774 |
| 0.25 | 2.9 | 89 / 509 / 426 | 14.88% | 17.28% | 0.11149 |
| 0.30 | 1.3 | 75 / 207 / 440 | 26.60% | 14.56% | 0.10543 |
| 0.40 | 0.5 | 49 / 61 / 466 | 44.55% | 9.51% | 0.08486 |
| 0.50 | 0.3 | 35 / 26 / 480 | 57.38% | 6.80% | 0.06557 |

The highest validation macro F1 among these candidates was at score 0.30.
An optional postprocessor now applies that threshold and extra classwise NMS
at 0.4, controlled by `configs/phase2b_evaluate.json`. This reduced outputs
to 1.12 per image on validation, but person recall fell to 5.26% and chair
recall to 2.30%. It is a measured diagnostic setting, **not** a safe operating
threshold for navigation. No change was made to the model's built-in NMS or
300 cap, preserving fixed-metric comparability.

## Validation and held-out results

| Split | Phase 2A fixed AP50 | Phase 2B fixed AP50 |
| --- | ---: | ---: |
| Validation, 209 images | 0.10105 | 0.13228 |
| Held-out test, 199 images | 0.09421 | 0.11496 |

| Class | Validation AP50 | Validation TP / FP / FN | Test AP50 | Test TP / FP / FN |
| --- | ---: | --- | ---: | --- |
| Person | 0.02777 | 94 / 24,488 / 96 | 0.03067 | 81 / 23,332 / 103 |
| Bicycle | 0.05655 | 35 / 2,358 / 34 | 0.05623 | 32 / 2,046 / 29 |
| Motorcycle | 0.26343 | 51 / 2,316 / 15 | 0.19584 | 37 / 2,307 / 9 |
| Car | 0.27666 | 70 / 14,379 / 33 | 0.27938 | 73 / 13,558 / 30 |
| Chair | 0.03698 | 40 / 18,690 / 47 | 0.01265 | 32 / 17,988 / 69 |

The held-out split was used in earlier development phases, so repeated
comparisons may bias future decisions. For Phase 2B it was inferred once,
after checkpoint and optional filtering settings were frozen. At the
validation-selected optional filtering setting, held-out AP50 was 0.09726
with 1.12 detections per image. Chair recall was only 0.99% and person recall
4.35%. No decisions were changed after seeing held-out results.

The fixed AP50 improvement is measurable, but the detector remains unsuitable
for active walking guidance. The annotation gaps and weak chair/person
behavior justify better labeled, representative validation and training data
before an architecture switch. A further bounded training study could test
whether gains continue; export and Android integration remain disabled.
