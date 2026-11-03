"""Draw inspection overlays for selected images; writes only to requested output."""

import argparse
import json
from pathlib import Path

from PIL import Image, ImageDraw

from .dataset import validate_coco


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--annotations", required=True)
    parser.add_argument("--images", required=True)
    parser.add_argument("--output", required=True)
    parser.add_argument("--limit", type=int, default=8)
    args = parser.parse_args()
    if not 1 <= args.limit <= 100:
        raise ValueError("limit must be between 1 and 100")
    document = json.loads(Path(args.annotations).read_text(encoding="utf-8"))
    validate_coco(document, Path(args.images))
    labels = {category["id"]: category["name"] for category in document["categories"]}
    annotations = {}
    for ann in document["annotations"]:
        annotations.setdefault(ann["image_id"], []).append(ann)
    output = Path(args.output)
    output.mkdir(parents=True, exist_ok=True)
    for image in sorted(document["images"], key=lambda item: item["id"])[:args.limit]:
        with Image.open(Path(args.images) / image["file_name"]) as opened:
            canvas = opened.convert("RGB")
        draw = ImageDraw.Draw(canvas)
        for ann in annotations.get(image["id"], []):
            x, y, w, h = ann["bbox"]
            draw.rectangle((x, y, x + w, y + h), outline="red", width=3)
            draw.text((x, y), labels[ann["category_id"]], fill="yellow")
        canvas.save(output / f"{image['id']}.png")
    print(f"Rendered {min(args.limit, len(document['images']))} inspection overlays to {output}")


if __name__ == "__main__":
    main()
