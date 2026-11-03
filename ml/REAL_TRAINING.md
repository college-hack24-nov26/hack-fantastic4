# Real-data training gate and validation modes

The frozen COCO 2017 selection contains 1,500 train, 250 internal-validation, and 250 held-out-test image IDs. Its private acquisition manifest still marks **all 2,000 images `pending_review`**. COCO's [terms](https://github.com/cocodataset/cocodataset.github.io/blob/master/dataset/termsofuse.htm) license annotations separately and put image rights on the user. COCO reports image license ID 4 as [CC BY 2.0](https://creativecommons.org/licenses/by/2.0/), which permits copying and adaptation with attribution, a license link, and change notice, but the COCO record does not supply author credit or prove each photographer's current rights. Do not use the candidate image bodies for training until rights and attribution are checked per selected image. Keep the held-out test split outside training and checkpoint selection.

[Open Images V7](https://storage.googleapis.com/openimages/web/download_v7.html) is the fallback *candidate* because its image-information table includes original landing URL, license URL, author, title, and profile URL alongside box annotations. Its [dataset description](https://storage.googleapis.com/openimages/web/factsfigures_v7.html) explicitly says that it does not warrant each image's license and asks users to verify it. Select only images whose individual source/license and attribution can be confirmed; this fallback is not yet an approved dataset. Keep image bytes and private attribution records outside Git. The five-class mapping and completeness of boxes require review before importing an Open Images subset.

## Open Images V7 acquisition checkpoint

The Phase 1F private proposal under `datasets/openimages_v7_metadata/verified_acquisition_proposal.json` contains **1,131 training, 209 validation, and 199 held-out test images** whose official metadata records CC BY 2.0, an author and source URL, and whose live Flickr page showed CC BY 2.0 and a creator at review time. Records with missing pages or conflicting live licenses were excluded. This is a proposed download, not a dataset already acquired or a guarantee that uploaders own every image. The private acquisition summary records class coverage, attribution, residual privacy/publicity uncertainty, and the exact proposal hash. Download image bodies only after that review is approved.

After approved JPEGs exist under an ignored `datasets/openimages_v7/images/{train,validation,test}/` tree using Open Images IDs as filenames, convert them with:

```powershell
python -B -m vision_ml.openimages_materialize --manifest datasets/openimages_v7_metadata/verified_acquisition_proposal.json --images-root datasets/openimages_v7/images --output-dir datasets/openimages_v7/prepared
```

The converter reads no network data. It checks live-rights review status, the five exact Open Images class IDs, JPEG decode and orientation, normalized box geometry, and duplicate bytes across official splits. It assigns safe numeric Vision image IDs, converts boxes to pixel `xywh`, and writes private COCO-format split files plus an integrity report. Inspect overlays and class counts before setting training paths. CVDF images may differ in size from original Flickr uploads, so downloaded image hashes and decoded dimensions are recorded separately; source metadata MD5 is not assumed to match a resized CVDF JPEG.

## Training modes

The trainer supports two explicit modes:

- **With independent validation:** set both `validation_annotations` and `validation_images`. It records internal-validation AP50, saves `last.pt` every completed epoch, updates `best.pt` only on an improved AP50, and applies patience-based early stopping. Never point these fields at held-out test data.
- **Technical training without validation:** set both validation paths to `null` and `allow_no_validation` to `true`. It records per-epoch training loss, elapsed time, and peak CUDA allocated/reserved memory; saves `last.pt` only; reports AP50 as unavailable; and does not apply validation-based early stopping. This mode cannot estimate generalization or select a best checkpoint.

Use a fresh private `runs/` output directory for each attempt. The trainer refuses to overwrite an existing `last.pt` or `best.pt`. Both modes use the same random-initialized SSDLite320-MobileNetV3 Large (`weights=None`, `weights_backbone=None`). Batch size 2, learning rate 0.001, and up to 8 epochs remain first-run settings rather than validated hyperparameters. The current two team photos, both from one scene, are suitable for infrastructure probing only and should not be presented as a meaningful detector training set.
