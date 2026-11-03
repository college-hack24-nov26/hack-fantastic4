# Vision ML Phase 1A

This is a local training scaffold, not a trained detector or Android model. No pretrained weights, datasets, or checkpoints are included. Hackathon rules and dataset rights must be confirmed before full training.

Phase 1B dataset candidates, provisional class mapping, split policy, and preparation commands are in [DATASETS.md](DATASETS.md).

The proposed Phase 1C.1 COCO subset, experiment settings, rights review, and Android handoff are in [FIRST_EXPERIMENT.md](FIRST_EXPERIMENT.md). `vision_ml.coco_plan` plans image IDs from official annotation metadata without downloading images.

The verified COCO annotation endpoint, candidate acquisition manifest, rights limitations, and 12-image probe are recorded in [ACCESS_1C2B.md](ACCESS_1C2B.md). `vision_ml.acquisition_manifest` builds the per-image candidate manifest without downloading images.

## Architecture choice

| Candidate | Fit for this project | Main tradeoff |
| --- | --- | --- |
| SSDLite 320 + MobileNetV3 Large | Small single-stage detector; already provided by installed Torchvision; explicit random initialization and a fixed 320-pixel model transform | Small or peripheral objects may suffer at 320 pixels; Android export and latency are unverified |
| Faster R-CNN + MobileNetV3 Large 320 FPN | Mobile backbone with region proposals; useful research alternative for object localization | Two-stage proposal processing complicates mobile export and may increase memory/latency |

**Selected experiment:** SSDLite 320 + MobileNetV3 Large. The code sets both `weights=None` and `weights_backbone=None`. Torchvision otherwise defaults to pretrained backbone weights even when detector weights are unset. This selection is a training candidate, not a claim of Android compatibility or detection quality. Architecture documentation: [SSDLite](https://docs.pytorch.org/vision/main/models/generated/torchvision.models.detection.ssdlite320_mobilenet_v3_large.html), [Faster R-CNN MobileNetV3](https://docs.pytorch.org/vision/main/models/generated/torchvision.models.detection.fasterrcnn_mobilenet_v3_large_320_fpn.html).

## Reproduce and inspect

Use a compatible Python/PyTorch/Torchvision installation. The local environment used Python 3.14.6, PyTorch 2.14.0+cu130, Torchvision 0.29.0+cu130, Pillow 12.3.0, and pytest 9.1.1. `requirements.txt` records the package versions; the CUDA wheel build depends on the installation source and is not pinned by the plain package version.

From `ml/`:

```powershell
python -B -m pytest -q
python -B -m vision_ml.smoke --device cpu
```

The smoke command creates two 320×320 synthetic images and artificial boxes in memory, runs one optimizer step, and writes no checkpoint. Batch size two avoids BatchNorm's single-element training error. A passing smoke test means only that the plumbing works. Its loss is not meaningful model performance.

Real training is intentionally gated by null dataset paths in `configs/train.json`. Once rules, licensed data, labels, and the split are approved, supply separate COCO-format train/validation annotation files and image directories. Run `python -m vision_ml.validate_dataset --annotations PATH --images DIRECTORY` before `python -m vision_ml.train --config configs/train.json`. This writes a checkpoint under the ignored `runs/` path. The loader validates image sizes, category IDs, boxes, references, and file presence before training. No production class names are assumed; class 0 is background and COCO category IDs must run from 1 to N. Each batch needs at least two images, so the loader drops a final singleton.

## Intended model I/O — provisional

- Loader input: RGB images converted to float CHW tensors in `[0,1]`. Torchvision performs its internal resize/normalization. The model's fixed transform is 320×320; external camera crop, rotation, mirroring, and letterbox policy remain joint decisions.
- Training targets: pixel-space `xyxy` boxes in the original upright image, positive class IDs, and no physical distance.
- Torchvision inference output: per-image `boxes` in pixel-space `xyxy`, integer `labels`, and `scores` in `[0,1]`. Model postprocessing is still tied to Torchvision; no raw tensor layout or Android decoder is approved.
- Application-level normalized boxes, timestamps, mock/real provenance, stale-frame handling, labels, confidence policy, and hazard decisions require agreement with the Android track. The draft `contracts/` schemas are not modified here.
- No ONNX/TFLite/other mobile export has been validated. Runtime, quantization, checksum, and device compatibility will be documented only after an actual artifact is tested.

## Evaluation plan

`vision_ml.evaluate.detection_metrics` provides per-class TP/FP/FN, precision, recall, and **AP50/mAP50** at a chosen score and IoU threshold. It is not COCO mAP (`0.50:0.95`) and must be labeled accordingly. After approved training, run `python -m vision_ml.evaluate_cli --checkpoint PATH --annotations PATH --images DIRECTORY --config configs/evaluate.json` on a held-out split. Inspect false negatives and false positives by class and scene. Add an optional `stratum` field to each COCO image record (`normal`, `motion_blur`, `low_light`, or `ultrawide`) to report each group separately with sample counts. The evaluation command measures checkpoint bytes and synchronized desktop latency; Android artifact size and device latency still require separate measurement. No real evaluation has been performed.

Generated datasets, checkpoints, and run outputs are ignored by the repository's root `.gitignore`. Keep approved source data and any private recordings outside Git.
