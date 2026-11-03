# Dataset candidates and split policy (Phase 1B)

No dataset has been downloaded or approved for training. The five **provisional** foreground classes are person, bicycle, motorcycle, car, and chair. They do not cover all walking hazards. Torchvision class 0 is background; model IDs 1–5 map respectively to COCO IDs 1, 2, 4, 3, and 62. The mapping lives in `configs/dataset.json`, not the shared application contracts.

| Candidate | Relevant annotations and format | Scale / download | Terms and suitability |
| --- | --- | --- | --- |
| [COCO 2017 detection](https://cocodataset.org/dataset/detection-2017.htm) | All five proposed classes; COCO JSON pixel `xywh` boxes. Official train/val partitions. | About 115k train and 5k val images; full images and annotation archives are a substantial download. Select only approved data after license review. | [COCO's own terms](https://github.com/cocodataset/cocodataset.github.io/blob/master/dataset/termsofuse.htm) license annotations CC BY 4.0, but COCO does **not** own image copyright. Each Flickr image's terms must be checked before redistribution or use beyond the permitted scope. Useful general object starting point; camera viewpoint differs from chest-mounted, moving ultrawide video. |
| [JRDB](https://jrdb.erc.monash.edu/dataset/) | Mainly people in moving robot, pedestrian-height 360° video; 2D detection labels with its own format, requiring conversion. Does not cover the five-class objective. | About 60k frames across 54 sequences; login required for download. | Site states CC BY-NC-SA 3.0. Good candidate for **person-only** motion/occlusion evaluation after confirming terms and conversion. Robot camera and panoramic stitching differ from phone ultrawide. Do not redistribute images here. |
| [BDD100K](https://github.com/bdd100k/bdd100k) | Person, bicycle, motorcycle, car in vehicle-view detection labels; chair generally irrelevant. Native labels require conversion to this COCO subset. | 100k videos; [100k keyframes archive ~5.3 GB and full videos ~1.8 TB](https://github.com/bdd100k/bdd100k/blob/master/doc/source/download.rst). Avoid full download. | [Data/labels terms](https://github.com/bdd100k/bdd100k/blob/master/doc/source/license.rst) permit educational/research/not-for-profit use with notices; commercial rights are more limited. Driving viewpoint is a domain mismatch, though low-light and weather variation may help secondary evaluation. |

**Recommendation:** investigate a small, licensed COCO 2017 subset first for five-class tooling and any later approved training. Keep COCO's official train2017 and val2017 identities: select training and internal validation only from train2017; reserve a disjoint portion of val2017 for final held-out evaluation, never for model selection. The generic 70/15/15 grouped splitter is for a **single custom source partition**, especially phone video; it must never be run over a combined COCO train+val collection. JRDB can later test person detection under motion, subject to its noncommercial share-alike terms and format conversion. The actual chest-pocket Android phone should supply a separately held-out, consented evaluation set across normal daylight, low light, motion blur, tilt, partial obstruction, ultrawide distortion, peripheral/small objects, and crowds. Record source recording/session IDs before splitting. Do not infer physical distances from box size.

## Preparation workflow

Keep original annotation JSON and images outside Git. `vision_ml.prepare` never edits them. Its output should go under ignored `ml/datasets/` or another private location. For independent still images, missing `group_id` is filled with a unique image ID. For video-derived frames, pass `--video-derived`; every record must then carry a stable `group_id` for its recording/session. Exact byte duplicates are SHA-256 grouped, including across sessions. The split is deterministic for a fixed seed, input, and group metadata; ratios are approximate when groups are large. The manifest records original annotation SHA-256, image hashes, class mapping, requested ratios, assignments, duplicate groups, and per-class/empty-image counts. Protect the manifest if file names or source metadata reveal private sessions.

Example, after preparing a lawful local source and choosing an output outside tracked files:

```powershell
python -m vision_ml.prepare --annotations SOURCE.json --images IMAGE_DIRECTORY --output datasets/prepared --video-derived
python -m vision_ml.validate_dataset --annotations datasets/prepared/train.json --images IMAGE_DIRECTORY
python -m vision_ml.visualize --annotations datasets/prepared/train.json --images IMAGE_DIRECTORY --output datasets/overlays --limit 8
```

For actual COCO 2017, run selection and split preparation **separately within each official source partition** and document how train2017 and val2017 were assigned. The current generic CLI does not enforce that boundary; do not pass a combined source file. Keep a held-out test split untouched during model selection.

Source image licenses, hackathon rules, and any planned model redistribution must be reviewed before real training. These notes are dataset research, not legal clearance.
