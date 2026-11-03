"""Plan a small COCO subset from metadata only; never downloads images."""

import argparse
import hashlib
import json
import random
from collections import Counter, defaultdict
from pathlib import Path

from .prepare import class_map


def select_ids(document, count, mapping, allowed_licenses, seed, min_per_class=0, empty_fraction=0.0, excluded=()):
    if count < 1 or not 0 <= empty_fraction < 1 or min_per_class < 0:
        raise ValueError("Invalid subset targets")
    if not allowed_licenses:
        raise ValueError("Explicit permitted image license IDs are required")
    target_categories = set(mapping)
    by_image = defaultdict(set)
    for ann in document["annotations"]:
        if ann["category_id"] in target_categories and ann.get("iscrowd", 0) == 0:
            by_image[ann["image_id"]].add(ann["category_id"])
    candidates = [image for image in document["images"] if image.get("license") in allowed_licenses and image["id"] not in excluded]
    by_id = {image["id"]: image for image in candidates}
    if len(by_id) != len(candidates):
        raise ValueError("Duplicate eligible image IDs")
    if len(by_id) < count:
        raise ValueError("Not enough eligible images")
    rng = random.Random(seed)
    positive = [image_id for image_id in by_id if by_image[image_id]]
    negative = [image_id for image_id in by_id if not by_image[image_id]]
    rng.shuffle(positive)
    rng.shuffle(negative)
    empty_target = round(count * empty_fraction)
    if len(negative) < empty_target or len(positive) < count - empty_target:
        raise ValueError("Not enough eligible positives or empty images")
    chosen = set()
    class_counts = Counter()
    for category in sorted(target_categories, key=lambda c: sum(c in by_image[i] for i in positive)):
        for image_id in positive:
            if class_counts[category] >= min_per_class:
                break
            if image_id not in chosen and category in by_image[image_id]:
                chosen.add(image_id)
                class_counts.update(by_image[image_id])
        if class_counts[category] < min_per_class:
            raise ValueError(f"Insufficient eligible images for COCO class {category}")
    if len(chosen) > count - empty_target:
        raise ValueError("Class quotas exceed positive-image budget")
    for image_id in positive:
        if len(chosen) >= count - empty_target:
            break
        chosen.add(image_id)
    chosen.update(negative[:empty_target])
    if len(chosen) != count:
        raise ValueError("Could not fill requested subset")
    return sorted(chosen)


def plan(train_doc, val_doc, classes, config):
    mapping = class_map(classes)
    for document in (train_doc, val_doc):
        names = {c["id"]: c["name"] for c in document["categories"]}
        if any(names.get(item["coco_id"]) != item["name"] for item in classes["classes"]):
            raise ValueError("COCO category names/IDs differ from configured mapping")
    licenses = set(config["allowed_image_license_ids"] or [])
    if not licenses:
        raise ValueError("Review image licenses and set allowed_image_license_ids before planning")
    overlap = {image["id"] for image in train_doc["images"]} & {image["id"] for image in val_doc["images"]}
    if overlap:
        raise ValueError("Official source partitions share image IDs")
    train_ids = select_ids(train_doc, config["train_images"], mapping, licenses, config["seed"], config["min_images_per_class_in_train"], config["empty_fraction"])
    validation_ids = select_ids(train_doc, config["internal_validation_images"], mapping, licenses, config["seed"] + 1, 0, config["empty_fraction"], train_ids)
    test_ids = select_ids(val_doc, config["held_out_val2017_images"], mapping, licenses, config["seed"] + 2, 0, config["empty_fraction"])
    return {"train2017_train": train_ids, "train2017_internal_validation": validation_ids, "val2017_held_out_test": test_ids}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--train-annotations", required=True)
    parser.add_argument("--val-annotations", required=True)
    parser.add_argument("--classes", default="configs/dataset.json")
    parser.add_argument("--config", default="configs/coco_subset.json")
    parser.add_argument("--output", required=True)
    args = parser.parse_args()
    train_path, val_path = Path(args.train_annotations), Path(args.val_annotations)
    output = Path(args.output)
    if output.resolve() in (train_path.resolve(), val_path.resolve()):
        raise ValueError("Output must not overwrite source annotations")
    train_raw, val_raw = train_path.read_bytes(), val_path.read_bytes()
    result = plan(json.loads(train_raw), json.loads(val_raw), json.loads(Path(args.classes).read_text(encoding="utf-8")), json.loads(Path(args.config).read_text(encoding="utf-8")))
    result["source_sha256"] = {"instances_train2017.json": hashlib.sha256(train_raw).hexdigest(), "instances_val2017.json": hashlib.sha256(val_raw).hexdigest()}
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(result, indent=2), encoding="utf-8")
    print(json.dumps({name: len(ids) for name, ids in result.items() if isinstance(ids, list)}))


if __name__ == "__main__":
    main()
