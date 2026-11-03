"""One approved real-image CUDA step; no epoch, checkpoint, or accuracy claim."""

import argparse
import hashlib
import json
from pathlib import Path
from time import perf_counter

import torch
from torch.utils.data import DataLoader

from .dataset import CocoDetectionDataset, collate, validate_coco
from .model import make_model
from .prepare import select_classes


def approved_train_ids(manifest, requested_ids):
    if len(requested_ids) != 2 or len(set(requested_ids)) != 2:
        raise ValueError("Probe requires exactly two distinct training images")
    by_id = {row["image_id"]: row for row in manifest["images"]}
    for image_id in requested_ids:
        row = by_id.get(image_id)
        if row is None or row.get("split") != "train2017_train":
            raise ValueError("Probe image must belong to train2017 training split")
        if row.get("rights_status") != "verified" or not row.get("attribution"):
            raise ValueError("Probe image needs verified rights and creator attribution")
        if not row.get("image_sha256_after_download"):
            raise ValueError("Probe image needs a recorded download SHA-256")
    return requested_ids


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--train-annotations", required=True)
    parser.add_argument("--train-images", required=True)
    parser.add_argument("--rights-manifest", required=True)
    parser.add_argument("--image-ids", type=int, nargs=2, required=True)
    parser.add_argument("--output-dir", required=True)
    parser.add_argument("--user-approved", action="store_true", help="Assert explicit user approval of this exact two-image probe")
    args = parser.parse_args()
    if not args.user_approved:
        raise ValueError("Explicit user approval is required before a real-image GPU step")
    if not torch.cuda.is_available():
        raise RuntimeError("CUDA unavailable")
    raw = Path(args.train_annotations).read_bytes()
    manifest = json.loads(Path(args.rights_manifest).read_text(encoding="utf-8"))
    expected = manifest["annotation_hashes"]["instances_train2017.json"]
    if hashlib.sha256(raw).hexdigest() != expected:
        raise ValueError("Training annotations do not match approved manifest")
    ids = approved_train_ids(manifest, args.image_ids)
    source = json.loads(raw)
    chosen = set(ids)
    subset = {"images": [image for image in source["images"] if image["id"] in chosen], "annotations": [ann for ann in source["annotations"] if ann["image_id"] in chosen], "categories": source["categories"]}
    classes = json.loads((Path(__file__).parents[1] / "configs/dataset.json").read_text(encoding="utf-8"))
    selected = select_classes(subset, classes)
    if len(selected["images"]) != 2:
        raise ValueError("An approved image is missing from source annotations")
    validate_coco(selected, Path(args.train_images))
    manifest_by_id = {row["image_id"]: row for row in manifest["images"]}
    for image in selected["images"]:
        image_bytes = (Path(args.train_images) / image["file_name"]).read_bytes()
        if hashlib.sha256(image_bytes).hexdigest() != manifest_by_id[image["id"]]["image_sha256_after_download"]:
            raise ValueError(f"Downloaded image hash mismatch: {image['id']}")
    output = Path(args.output_dir)
    output.mkdir(parents=True, exist_ok=True)
    subset_path = output / "probe_annotations.json"
    subset_path.write_text(json.dumps(selected, indent=2), encoding="utf-8")
    dataset = CocoDetectionDataset(subset_path, args.train_images)
    from PIL import Image, ImageDraw
    for image in selected["images"]:
        with Image.open(Path(args.train_images) / image["file_name"]) as opened:
            canvas = opened.convert("RGB")
        draw = ImageDraw.Draw(canvas)
        for ann in selected["annotations"]:
            if ann["image_id"] == image["id"]:
                x, y, w, h = ann["bbox"]
                draw.rectangle((x, y, x + w, y + h), outline="red", width=3)
                draw.text((x, y), str(ann["category_id"]), fill="yellow")
        canvas.save(output / f"overlay_{image['id']}.png")
    images, targets = next(iter(DataLoader(dataset, batch_size=2, shuffle=False, collate_fn=collate, num_workers=0)))
    torch.cuda.empty_cache()
    torch.cuda.reset_peak_memory_stats()
    free_before, total = torch.cuda.mem_get_info()
    model = make_model(6).cuda()
    model.train()
    optimizer = torch.optim.SGD(model.parameters(), lr=0.001)
    images = [image.cuda() for image in images]
    targets = [{key: value.cuda() for key, value in target.items()} for target in targets]
    torch.cuda.synchronize()
    start = perf_counter()
    try:
        loss = sum(model(images, targets).values())
        if not torch.isfinite(loss):
            raise RuntimeError("Nonfinite real-image probe loss")
        optimizer.zero_grad(set_to_none=True)
        loss.backward()
        optimizer.step()
        torch.cuda.synchronize()
    except torch.cuda.OutOfMemoryError:
        torch.cuda.empty_cache()
        raise
    result = {"status": "one_step_completed", "image_ids": ids, "training_steps": 1, "full_epoch": False, "accuracy_measured": False, "gpu_free_before_bytes": free_before, "gpu_total_bytes": total, "peak_allocated_bytes": torch.cuda.max_memory_allocated(), "peak_reserved_bytes": torch.cuda.max_memory_reserved(), "step_ms": round((perf_counter() - start) * 1000, 1), "loss_finite": True}
    (output / "probe_result.json").write_text(json.dumps(result, indent=2), encoding="utf-8")
    print(json.dumps(result))


if __name__ == "__main__":
    main()
