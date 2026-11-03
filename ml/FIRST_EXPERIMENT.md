# Phase 1C.1 — proposed first real-data experiment

**Status:** preparation only. No COCO data has been downloaded and no real training or GPU memory benchmark has run. The hackathon's weight/data rules and image rights are still prerequisites.

## COCO sources, rights, and acquisition

Use the [official COCO 2017 detection page](https://cocodataset.org/dataset/detection-2017.htm) and [official download listing](https://github.com/cocodataset/cocodataset.github.io/blob/master/dataset/download.htm) for `annotations_trainval2017.zip`, containing `instances_train2017.json` and `instances_val2017.json`. The official listing puts the annotation archive at about **241 MB**, the complete train image archive at **18 GB** for about **118,000** images, and the val image archive at **1 GB** for **5,000** images. Do not download either full image archive for this experiment.

The [COCO terms](https://github.com/cocodataset/cocodataset.github.io/blob/master/dataset/termsofuse.htm) license annotations CC BY 4.0. COCO does not own image copyright; image use follows Flickr terms and the individual image license. Attribution and allowed use must be checked for each selected image. The `license` IDs in COCO metadata are a screening aid, **not legal clearance**. Keep source IDs, license URL/identifier, and attribution records locally; do not put the images in Git. If usable image rights cannot be verified, switch to a smaller consented phone collection or other clearly permitted images and keep the same split/validation discipline.

## Reproducible subset plan

1. Obtain only the official annotation archive after approval. Preserve its checksum and the extracted originals; do not combine the two annotation JSON files.
2. Review the `licenses` table and intended use. Put explicitly permitted license IDs in `configs/coco_subset.json`; the shipped `null` value prevents selection by default. Inspect individual image rights and attribution before downloading any selected image.
3. Run the metadata-only planner from `ml/`:

   ```powershell
   python -m vision_ml.coco_plan --train-annotations PATH/instances_train2017.json --val-annotations PATH/instances_val2017.json --output datasets/coco_subset_plan.json
   ```

   Seed **42** selects **1,500 train images** and **250 internal-validation images**, both disjoint from official train2017, plus **250 untouched final-test images** from official val2017. It targets at least **100 train images per foreground class** and about **10% images with no selected-class annotation**. It checks category names, license IDs, image ID overlap, and requested quotas. If eligible images are insufficient, it fails rather than silently changing quotas. This is a proposal; inspect actual class counts, image content, and rights before acquisition.
4. Fetch only the selected files from their official COCO image URLs after rights review. Record file SHA-256, source partition, license, attribution, and failures in a private acquisition manifest. Never fetch the complete train/val image archives merely to access this subset.
5. Convert the frozen ID plan to three COCO JSON files using `python -m vision_ml.coco_materialize --plan datasets/coco_subset_plan.json --train-annotations PATH/instances_train2017.json --val-annotations PATH/instances_val2017.json --output-dir datasets/coco_selected`. It verifies the original annotation hashes and maps all five selected classes to model IDs 1–5. Images with no selected-class box remain as negatives. The train and internal-validation image files come from `train2017`; final-test files come from `val2017`.
6. Validate boxes and actual image dimensions, inspect overlays, and check exact duplicates across train/internal-validation/final-test before creating training inputs. Freeze the final-test IDs before model selection. Never use final-test results to tune epochs, thresholds, or preprocessing.

The full archive sizes imply a rough compressed-image average of 150–200 KB each. **2,000 selected images may therefore require roughly 0.3–0.6 GB of image downloads**, plus the ~241 MB annotation archive. Actual image sizes vary; reserve **at least 2 GB** for images, extracted annotations, manifests, and failed-download retries, and separate space for generated checkpoints. This is a storage plan, not a measured transfer size. The metadata planner alone downloads nothing.

## Proposed training configuration

`configs/first_experiment.json` records SSDLite320-MobileNetV3 Large, five foreground classes plus background ID 0, seed 42, RGB input, **320×320** Torchvision model transform, SGD learning rate **0.001**, batch size **2**, and a ceiling of **8 epochs** on the RTX 3050 Laptop GPU. Both model and backbone use random weights. Dataset paths remain null, so this config cannot start training as shipped.

Before any real run, remeasure peak allocated/reserved VRAM on representative images. The bounded synthetic GPU probe in `READINESS_1C2A.md` fit batch size two; this does not establish real-image training throughput. The current training code needs at least two images per batch because of BatchNorm at a 1×1 feature map. Check train/validation category tables and image ID overlap. The trainer now saves `last.pt` each epoch under ignored `runs/` and replaces `best.pt` only when internal-validation AP50 at IoU 0.5 and score floor 0.05 improves. It stops after two nonimproving epochs or the eight-epoch ceiling. This validation path has synthetic unit coverage but has not run on real data. Evaluate the frozen best model once on the held-out final set. Record per-class precision, recall, AP50, false positives and negatives, low light/motion strata, artifact bytes, and inference latency with actual measurements.

Random initialization on only 1,500 images may underfit; no accuracy, FPS, or Android compatibility is promised. Torchvision's SSDLite postprocessing and operator set may complicate ONNX or other mobile export. Test export with an actual artifact and target runtime before integration.

## Android handoff draft

| Model ID | Class | COCO source ID |
| --- | --- | --- |
| 0 | background | none |
| 1 | person | 1 |
| 2 | bicycle | 2 |
| 3 | motorcycle | 4 |
| 4 | car | 3 |
| 5 | chair | 62 |

The Python loader converts decoded images to **RGB, float CHW, `[0,1]`**. The checked Torchvision model then applies fixed **320×320** resizing and channel normalization `(value − 0.5) / 0.5`; this was inspected in the installed model, not validated in Android. Torchvision inference currently returns pixel-space `xyxy` boxes in the supplied image's coordinate system, integer class IDs, and confidence scores. The Android application contract instead proposes normalized boxes in an upright frame. Rotation, mirroring, crop/letterbox, ultrawide lens handling, pixel-to-normalized conversion, timestamp/age policy, label localization, and mock-versus-real provenance require joint agreement. Raw tensor names/shapes, quantization, runtime, and exported decoder are **undecided**. This handoff does not change shared contracts.
