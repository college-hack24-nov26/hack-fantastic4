"""Convert reviewed Open Images V7 files to Vision COCO JSON, without network use."""

import argparse
import json
from collections import Counter, defaultdict
from pathlib import Path

from PIL import Image

from .dataset import validate_coco
from .prepare import sha256


CLASSES = ("person", "bicycle", "motorcycle", "car", "chair")
SPLITS = ("train", "validation", "test")
CLASS_MAP = {"/m/01g317": 1, "/m/0199g": 2, "/m/04_sv": 3, "/m/0k4j": 4, "/m/01mzpv": 5}


def materialize(manifest: dict, images_root: Path) -> tuple[dict, dict]:
    if manifest.get("schema") != "vision-openimages-v7-verified-source-proposal-1":
        raise ValueError("Expected the verified Open Images acquisition proposal")
    if manifest.get("class_map") != CLASS_MAP:
        raise ValueError("Open Images class mapping differs from Vision IDs")
    documents = {}
    report = {"source_schema": manifest["schema"], "splits": {}, "image_hashes": []}
    hashes = defaultdict(list)
    seen_source_ids = set()
    next_image_id = 1
    for split in SPLITS:
        source_rows = manifest["splits"][split]["images"]
        document = {"images": [], "annotations": [], "categories": [{"id": index, "name": name} for index, name in enumerate(CLASSES, 1)]}
        classes = Counter()
        for row in source_rows:
            source_id = row["image_id"]
            if source_id in seen_source_ids or not isinstance(source_id, str) or len(source_id) != 16 or any(c not in "0123456789abcdef" for c in source_id):
                raise ValueError("Duplicate or invalid source image ID across splits")
            seen_source_ids.add(source_id)
            if row.get("official_split") != split or row.get("source_check_status") != "live_license_and_creator_verified":
                raise ValueError("Image split or live rights review is unresolved")
            if row.get("live_license", "").rstrip("/") != "https://creativecommons.org/licenses/by/2.0" or not row.get("live_creator") or not row.get("source_landing_url"):
                raise ValueError("Image lacks required creator, source, or compatible live license")
            path = images_root / split / f"{source_id}.jpg"
            if not path.is_file():
                raise ValueError(f"Missing approved image: {path}")
            try:
                with Image.open(path) as opened:
                    if opened.format != "JPEG" or opened.getexif().get(274, 1) != 1:
                        raise ValueError(f"Unsupported image format or EXIF orientation: {path}")
                    width, height = opened.size
                    opened.load()
            except OSError as error:
                raise ValueError(f"Image cannot be decoded: {path}") from error
            if width <= 0 or height <= 0:
                raise ValueError(f"Invalid image dimensions: {path}")
            digest = sha256(path)
            hashes[digest].append((split, source_id))
            document["images"].append({"id": next_image_id, "source_image_id": source_id, "file_name": path.name, "width": width, "height": height})
            for box in row["boxes"]:
                category_id = box["category_id"]
                coords = box["xyxy_normalized"]
                if category_id not in range(1, 6) or not isinstance(coords, list) or len(coords) != 4 or any(not isinstance(value, (int, float)) or isinstance(value, bool) for value in coords):
                    raise ValueError(f"Invalid class or normalized box: {source_id}")
                x0, y0, x1, y1 = coords
                if not (0 <= x0 < x1 <= 1 and 0 <= y0 < y1 <= 1):
                    raise ValueError(f"Out-of-frame normalized box: {source_id}")
                left, top, right, bottom = x0 * width, y0 * height, x1 * width, y1 * height
                document["annotations"].append({"id": len(document["annotations"]) + 1, "image_id": next_image_id, "category_id": category_id, "bbox": [left, top, right - left, bottom - top], "iscrowd": 0})
                classes[category_id] += 1
            next_image_id += 1
            report["image_hashes"].append({"source_image_id": source_id, "split": split, "sha256": digest})
        validate_coco(document, images_root / split)
        documents[split] = document
        report["splits"][split] = {"images": len(document["images"]), "boxes_per_class": {str(index): classes[index] for index in range(1, 6)}}
    if any(len({split for split, _ in entries}) > 1 for entries in hashes.values()):
        raise ValueError("Duplicate image bytes cross official splits")
    report["duplicate_image_bytes"] = [entries for entries in hashes.values() if len(entries) > 1]
    return documents, report


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--manifest", required=True)
    parser.add_argument("--images-root", required=True)
    parser.add_argument("--output-dir", required=True)
    args = parser.parse_args()
    output = Path(args.output_dir)
    if output.exists() and any(output.iterdir()):
        raise ValueError("Output directory must be empty")
    manifest = json.loads(Path(args.manifest).read_text(encoding="utf-8"))
    documents, report = materialize(manifest, Path(args.images_root))
    output.mkdir(parents=True, exist_ok=True)
    for split, document in documents.items():
        (output / f"{split}.json").write_text(json.dumps(document, indent=2), encoding="utf-8")
    (output / "integrity_report.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
    print(json.dumps(report["splits"]))


if __name__ == "__main__":
    main()
