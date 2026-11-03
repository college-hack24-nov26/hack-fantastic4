"""Import privately owned/consented images into validated COCO-format splits."""

import argparse
import json
from collections import Counter, defaultdict
from pathlib import Path

from PIL import Image, ImageDraw

from .dataset import validate_coco
from .prepare import duplicate_images, sha256


CLASSES = ("person", "bicycle", "motorcycle", "car", "chair")
SPLITS = ("train", "validation", "test")


def build_dataset(source: dict, image_root: Path, source_hash: str) -> tuple[dict, dict]:
    if not isinstance(source, dict) or not isinstance(source.get("images"), list) or not source["images"]:
        raise ValueError("Source needs a nonempty images list")
    categories = [{"id": number, "name": name} for number, name in enumerate(CLASSES, 1)]
    document = {"images": [], "annotations": [], "categories": categories}
    rows, hashes, groups = [], {}, defaultdict(set)
    seen_names = set()
    for item in source["images"]:
        image_id, name = item.get("id"), item.get("file_name")
        if not isinstance(image_id, int) or isinstance(image_id, bool) or image_id <= 0:
            raise ValueError("Image IDs must be positive integers")
        if not isinstance(name, str) or not name or Path(name).name != name or "\\" in name or "/" in name:
            raise ValueError(f"Image {image_id}: file_name must be a plain filename")
        if name in seen_names:
            raise ValueError(f"Duplicate file_name: {name}")
        seen_names.add(name)
        path = image_root / name
        if path.suffix.lower() not in (".jpg", ".jpeg", ".png"):
            raise ValueError(f"Image {image_id}: only JPEG/PNG is supported")
        try:
            with Image.open(path) as opened:
                if opened.format not in ("JPEG", "PNG") or (opened.format == "JPEG" and path.suffix.lower() == ".png") or (opened.format == "PNG" and path.suffix.lower() != ".png"):
                    raise ValueError(f"Image {image_id}: extension and content format disagree")
                width, height = opened.size
                opened.load()
        except (OSError, FileNotFoundError) as error:
            raise ValueError(f"Image {image_id}: cannot decode {name}: {error}") from error
        if width <= 0 or height <= 0 or item.get("width") != width or item.get("height") != height:
            raise ValueError(f"Image {image_id}: declared dimensions disagree with decoded image")
        split, group = item.get("split"), item.get("group_id")
        if split not in SPLITS or not isinstance(group, str) or not group.strip():
            raise ValueError(f"Image {image_id}: valid split and recording group_id are required")
        groups[group].add(split)
        rights = item.get("rights")
        if not isinstance(rights, dict) or rights.get("permission_basis") not in ("creator_owned", "licensed_permission") or not isinstance(rights.get("record_id"), str) or not rights["record_id"].strip():
            raise ValueError(f"Image {image_id}: a private ownership/permission record is required")
        if rights.get("subject_consent") not in ("documented", "not_applicable"):
            raise ValueError(f"Image {image_id}: subject_consent must be documented or not_applicable")
        boxes = item.get("boxes")
        if not isinstance(boxes, list):
            raise ValueError(f"Image {image_id}: boxes must be a list (empty is allowed)")
        if any(not isinstance(box, dict) or box.get("category_id") not in range(1, 6) for box in boxes):
            raise ValueError(f"Image {image_id}: category_id must be 1–5")
        if any(box["category_id"] == 1 for box in boxes) and rights["subject_consent"] != "documented":
            raise ValueError(f"Image {image_id}: person images require documented subject consent")
        document["images"].append({"id": image_id, "file_name": name, "width": width, "height": height})
        for box in boxes:
            document["annotations"].append({"id": len(document["annotations"]) + 1, "image_id": image_id, "category_id": box["category_id"], "bbox": box.get("bbox_xywh"), "iscrowd": 0})
        digest = sha256(path)
        hashes[image_id] = digest
        rows.append({"image_id": image_id, "file_name": name, "split": split, "group_id": group, "sha256": digest, "rights_record_id": rights["record_id"], "permission_basis": rights["permission_basis"], "subject_consent": rights["subject_consent"]})
    validate_coco(document, image_root)
    if any(len(splits) != 1 for splits in groups.values()):
        raise ValueError("A recording group crosses train/validation/test splits")
    by_id = {row["image_id"]: row for row in rows}
    duplicates = duplicate_images(hashes)
    if any(len({by_id[image_id]["split"] for image_id in ids}) != 1 for ids in duplicates):
        raise ValueError("Exact duplicate images cross train/validation/test splits")
    if len({row["image_id"] for row in rows}) != len(rows):
        raise ValueError("Duplicate image IDs")
    rows.sort(key=lambda row: row["image_id"])
    manifest = {"schema": "vision-original-images-1", "source_annotation_sha256": source_hash, "classes": categories, "split_counts": dict(Counter(row["split"] for row in rows)), "duplicate_image_ids": duplicates, "images": rows}
    return document, manifest


def import_dataset(source_path: Path, image_root: Path, output: Path) -> dict:
    source_path, image_root, output = source_path.resolve(), image_root.resolve(), output.resolve()
    if output == image_root or output.is_relative_to(image_root) or source_path == output or source_path.is_relative_to(output):
        raise ValueError("Output must not overwrite source images or annotations")
    if output.exists() and any(output.iterdir()):
        raise ValueError("Output directory must be empty for a reproducible import")
    document, manifest = build_dataset(json.loads(source_path.read_text(encoding="utf-8")), image_root, sha256(source_path))
    output.mkdir(parents=True, exist_ok=True)
    overlays = output / "overlays"
    overlays.mkdir()
    for split in SPLITS:
        ids = {row["image_id"] for row in manifest["images"] if row["split"] == split}
        part = {"images": [image for image in document["images"] if image["id"] in ids], "annotations": [ann for ann in document["annotations"] if ann["image_id"] in ids], "categories": document["categories"]}
        path = output / f"{split}.json"
        path.write_text(json.dumps(part, indent=2), encoding="utf-8")
        manifest.setdefault("annotation_hashes", {})[f"{split}.json"] = sha256(path)
    for image in document["images"]:
        with Image.open(image_root / image["file_name"]) as opened:
            canvas = opened.convert("RGB")
        draw = ImageDraw.Draw(canvas)
        for ann in document["annotations"]:
            if ann["image_id"] == image["id"]:
                x, y, w, h = ann["bbox"]
                draw.rectangle((x, y, x + w, y + h), outline="red", width=3)
                draw.text((x, y), CLASSES[ann["category_id"] - 1], fill="yellow")
        canvas.save(overlays / f"{image['id']}.png")
    (output / "manifest.json").write_text(json.dumps(manifest, indent=2), encoding="utf-8")
    return manifest


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", required=True, help="Private source annotation JSON")
    parser.add_argument("--images", required=True, help="Directory of original JPEG/PNG photographs")
    parser.add_argument("--output", required=True, help="Empty private, Git-ignored output directory")
    args = parser.parse_args()
    report = import_dataset(Path(args.source), Path(args.images), Path(args.output))
    print(json.dumps({"images": len(report["images"]), "split_counts": report["split_counts"], "duplicate_image_ids": report["duplicate_image_ids"], "output": args.output}))


if __name__ == "__main__":
    main()
