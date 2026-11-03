# Team-owned image import and one-step probe

Collect 12–20 original photographs with the intended Android phone where possible. Include consented people, bicycles, motorcycles, cars, chairs, and background-only views. Vary camera height/tilt, distance, daylight/indoor light, partial occlusion, and walking motion. Keep each recording session together; a tiny validation/test split checks plumbing, **not** detection accuracy. Do not collect incidental bystanders or visible registration numbers without appropriate permission. Possessing a photo does not establish rights for identifiable people or private property.

Keep all photographs, consent/permission records, source JSON, overlays, and generated manifests under the ignored `ml/datasets/` directory or another private location outside Git. Record permission for the intended prototype use and retain the actual signed consent or license separately. `record_id` is only a private reference; the importer cannot verify legal permission. Do not put names, contact details, or consent forms in Git or public issue trackers.

## Private folder layout

```text
ml/datasets/original/
  images/                 # Original JPEG/PNG files; flat directory
  source.json             # Private annotation and rights references
  permissions/            # Private consent and ownership evidence
  prepared/               # Generated split JSON, manifest, overlays
```

## Annotation format

Create `source.json` with an `images` array. Every image needs a unique positive integer `id`, a plain filename, **actual decoded** `width` and `height` in pixels, `split` (`train`, `validation`, or `test`), and a `group_id` shared by all frames from the same recording/session. Independent stills can each use a unique group. Keep participant/location sessions in one split. The `rights` object needs `permission_basis` (`creator_owned` or `licensed_permission`), a nonempty private `record_id`, and `subject_consent` (`documented` or `not_applicable`). An image labeled `person` requires `documented`; unboxed identifiable people also require consent. Provide `boxes: []` for reviewed background-only images.

Each box uses `category_id` 1 person, 2 bicycle, 3 motorcycle, 4 car, or 5 chair, and `bbox_xywh: [left, top, width, height]` in **original upright image pixels**. The box must have positive size and remain inside the image. Do not silently resize, rotate, or correct boxes; resolve EXIF orientation before annotating and record the dimensions of the saved file. Label the visible object extent consistently; review partially occluded examples manually.

```json
{
  "images": [
    {
      "id": 1,
      "file_name": "frame-001.jpg",
      "width": 1280,
      "height": 720,
      "split": "train",
      "group_id": "private-session-01",
      "rights": {
        "permission_basis": "creator_owned",
        "record_id": "private-permission-01",
        "subject_consent": "documented"
      },
      "boxes": [{"category_id": 1, "bbox_xywh": [120, 90, 200, 500]}]
    }
  ]
}
```

From `ml/`, after adding real photographs and private rights records:

```powershell
python -B -m vision_ml.original_import --source datasets/original/source.json --images datasets/original/images --output datasets/original/prepared
python -B -m vision_ml.validate_dataset --annotations datasets/original/prepared/train.json --images datasets/original/images
```

The output directory must be empty. The importer decodes JPEG/PNG, checks declared dimensions, validates all boxes and class IDs, hashes image bytes and source annotations, detects exact duplicates, rejects recording groups or duplicate bytes spanning splits, and writes `train.json`, `validation.json`, `test.json`, `manifest.json`, and one overlay per image. Inspect **every overlay** and the split/rights records before approving a probe. The importer does not infer rights or create annotations.

Once two training images and the complete private manifest have been reviewed, the **separate approved** one-step CUDA command is:

```powershell
python -B -m vision_ml.real_probe --train-annotations datasets/original/prepared/train.json --train-images datasets/original/images --rights-manifest datasets/original/prepared/manifest.json --image-ids 1 2 --output-dir runs/original_probe --user-approved
```

The flag records explicit approval of this exact execution; do not run it before approval. `real_probe` excludes validation/test IDs, verifies hashes, draws two overlays, performs one CUDA optimizer step from random detector/backbone weights, and reports synchronized step time plus peak allocated/reserved VRAM. It saves no checkpoint and reports no accuracy. Batch size two is still subject to actual 4 GB GPU memory availability.
