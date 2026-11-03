"""Strict, small COCO detection subset with pixel-space xyxy training targets."""

import json
from collections import Counter
from pathlib import Path

import torch
from PIL import Image
from torch.utils.data import Dataset
from torchvision.transforms.functional import pil_to_tensor


def validate_coco(document: dict, image_root: Path | None = None) -> dict:
    """Raise ValueError for unsafe or inconsistent annotations; return counts."""
    if not isinstance(document, dict) or not all(k in document for k in ("images", "annotations", "categories")):
        raise ValueError("COCO document needs images, annotations, and categories")
    images, annotations, categories = (document[k] for k in ("images", "annotations", "categories"))
    if not all(isinstance(v, list) for v in (images, annotations, categories)):
        raise ValueError("COCO collections must be lists")
    ids = [i.get("id") for i in images]
    class_ids = [c.get("id") for c in categories]
    annotation_ids = [a.get("id") for a in annotations]
    if any(not isinstance(x, int) or isinstance(x, bool) for x in ids + class_ids + annotation_ids):
        raise ValueError("IDs must be integers")
    if any(n != 1 for n in (max(Counter(x).values(), default=0) for x in (ids, class_ids, annotation_ids))):
        raise ValueError("Duplicate IDs")
    if sorted(class_ids) != list(range(1, len(class_ids) + 1)):
        raise ValueError("Category IDs must be contiguous from 1; class 0 is background")
    if any(not isinstance(c.get("name"), str) or not c["name"].strip() for c in categories):
        raise ValueError("Every category needs a nonempty name")
    image_by_id = {i["id"]: i for i in images}
    for image in images:
        w, h = image.get("width"), image.get("height")
        if not all(isinstance(x, int) and not isinstance(x, bool) and x > 0 for x in (w, h)):
            raise ValueError("Image dimensions must be positive integers")
        name = image.get("file_name")
        if not isinstance(name, str) or not name or Path(name).is_absolute() or ".." in Path(name).parts:
            raise ValueError("Unsafe or empty image file_name")
        if image_root is not None:
            path = image_root / name
            if not path.is_file():
                raise ValueError(f"Missing image: {name}")
            with Image.open(path) as opened:
                if opened.size != (w, h):
                    raise ValueError(f"Image dimensions disagree: {name}")
    for ann in annotations:
        if ann.get("image_id") not in image_by_id or ann.get("category_id") not in class_ids:
            raise ValueError("Annotation references unknown image or class")
        if ann.get("iscrowd", 0) != 0:
            raise ValueError("Crowd annotations are unsupported in this training subset")
        box = ann.get("bbox")
        if not isinstance(box, list) or len(box) != 4 or any(not isinstance(v, (int, float)) or isinstance(v, bool) for v in box):
            raise ValueError("bbox must be four numeric xywh values")
        from math import isfinite
        x, y, w, h = box
        image = image_by_id[ann["image_id"]]
        if not all(isfinite(v) for v in box) or x < 0 or y < 0 or w <= 0 or h <= 0 or x + w > image["width"] or y + h > image["height"]:
            raise ValueError("Invalid or out-of-frame bbox")
    return {"images": len(images), "annotations": len(annotations), "categories": len(categories)}


class CocoDetectionDataset(Dataset):
    def __init__(self, annotation_path: str | Path, image_root: str | Path):
        self.root = Path(image_root)
        self.document = json.loads(Path(annotation_path).read_text(encoding="utf-8"))
        validate_coco(self.document, self.root)
        self.images = sorted(self.document["images"], key=lambda item: item["id"])
        self.annotations = {}
        for ann in self.document["annotations"]:
            self.annotations.setdefault(ann["image_id"], []).append(ann)

    def __len__(self):
        return len(self.images)

    def __getitem__(self, index):
        record = self.images[index]
        with Image.open(self.root / record["file_name"]) as opened:
            image = pil_to_tensor(opened.convert("RGB")).float() / 255.0
        anns = self.annotations.get(record["id"], [])
        boxes = [[a["bbox"][0], a["bbox"][1], a["bbox"][0] + a["bbox"][2], a["bbox"][1] + a["bbox"][3]] for a in anns]
        target = {
            "boxes": torch.tensor(boxes, dtype=torch.float32).reshape(-1, 4),
            "labels": torch.tensor([a["category_id"] for a in anns], dtype=torch.int64),
            "image_id": torch.tensor(record["id"]),
        }
        return image, target


def collate(batch):
    images, targets = zip(*batch)
    return list(images), list(targets)
