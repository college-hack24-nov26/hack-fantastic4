"""Build selected COCO annotation files from a frozen metadata-only ID plan."""

import argparse
import hashlib
import json
from pathlib import Path

from .dataset import validate_coco
from .prepare import select_classes


def materialize(document, image_ids, classes):
    chosen = set(image_ids)
    if len(chosen) != len(image_ids):
        raise ValueError("Duplicate planned image IDs")
    available = {image["id"] for image in document["images"]}
    if not chosen <= available:
        raise ValueError("Planned image ID absent from source partition")
    subset = {"images": [image for image in document["images"] if image["id"] in chosen], "annotations": [ann for ann in document["annotations"] if ann["image_id"] in chosen], "categories": document["categories"]}
    selected = select_classes(subset, classes)
    validate_coco(selected)
    return selected


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--plan", required=True)
    parser.add_argument("--train-annotations", required=True)
    parser.add_argument("--val-annotations", required=True)
    parser.add_argument("--classes", default="configs/dataset.json")
    parser.add_argument("--output-dir", required=True)
    args = parser.parse_args()
    plan = json.loads(Path(args.plan).read_text(encoding="utf-8"))
    train_raw = Path(args.train_annotations).read_bytes()
    val_raw = Path(args.val_annotations).read_bytes()
    expected = plan["source_sha256"]
    if hashlib.sha256(train_raw).hexdigest() != expected["instances_train2017.json"] or hashlib.sha256(val_raw).hexdigest() != expected["instances_val2017.json"]:
        raise ValueError("Source annotations changed since ID planning")
    train, val = json.loads(train_raw), json.loads(val_raw)
    classes = json.loads(Path(args.classes).read_text(encoding="utf-8"))
    outputs = {
        "train": materialize(train, plan["train2017_train"], classes),
        "internal_validation": materialize(train, plan["train2017_internal_validation"], classes),
        "held_out_test": materialize(val, plan["val2017_held_out_test"], classes),
    }
    output_dir = Path(args.output_dir)
    source_paths = {Path(args.train_annotations).resolve(), Path(args.val_annotations).resolve()}
    if any((output_dir / f"{name}.json").resolve() in source_paths for name in outputs):
        raise ValueError("Output would overwrite source annotations")
    output_dir.mkdir(parents=True, exist_ok=True)
    for name, document in outputs.items():
        (output_dir / f"{name}.json").write_text(json.dumps(document, indent=2), encoding="utf-8")
    print(json.dumps({name: len(document["images"]) for name, document in outputs.items()}))


if __name__ == "__main__":
    main()
