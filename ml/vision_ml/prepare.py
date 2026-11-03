"""Reproducible COCO selection, grouped splits, leakage checks, and manifests."""

import argparse
import hashlib
import json
import random
from collections import Counter, defaultdict
from pathlib import Path

from .dataset import validate_coco


def class_map(config):
    classes = config["classes"]
    model_ids = [item["model_id"] for item in classes]
    coco_ids = [item["coco_id"] for item in classes]
    if sorted(model_ids) != list(range(1, len(classes) + 1)) or len(set(coco_ids)) != len(coco_ids):
        raise ValueError("Model IDs must be contiguous from 1 and COCO IDs unique")
    return {item["coco_id"]: item["model_id"] for item in classes}


def select_classes(source, config):
    mapping = class_map(config)
    source_categories = {c["id"]: c["name"] for c in source["categories"]}
    for entry in config["classes"]:
        if source_categories.get(entry["coco_id"]) != entry["name"]:
            raise ValueError(f"COCO class mismatch: {entry['name']}")
    selected = dict(source)
    selected["images"] = [dict(image) for image in source["images"]]
    selected["categories"] = [{"id": c["model_id"], "name": c["name"], "source_coco_id": c["coco_id"]} for c in config["classes"]]
    selected["annotations"] = [{**ann, "category_id": mapping[ann["category_id"]]} for ann in source["annotations"] if ann["category_id"] in mapping and ann.get("iscrowd", 0) == 0]
    return selected


def sha256(path):
    digest = hashlib.sha256()
    with open(path, "rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def image_hashes(document, image_root):
    """Hash bytes to find exact duplicate images; expensive on full COCO."""
    root = Path(image_root)
    return {image["id"]: sha256(root / image["file_name"]) for image in document["images"]}


def duplicate_images(hashes):
    groups = defaultdict(list)
    for image_id, digest in hashes.items():
        groups[digest].append(image_id)
    return [sorted(ids) for ids in groups.values() if len(ids) > 1]


def split_grouped(document, hashes, ratios, seed, group_field="group_id"):
    """Split whole source groups, binding exact duplicate images into the same group."""
    if set(ratios) != {"train", "validation", "test"} or any(v < 0 for v in ratios.values()) or abs(sum(ratios.values()) - 1) > 1e-9:
        raise ValueError("Split ratios must be nonnegative and total 1")
    images = document["images"]
    if set(hashes) != {image["id"] for image in images}:
        raise ValueError("Every image needs a content hash")
    parent = {image["id"]: image["id"] for image in images}
    def find(value):
        while parent[value] != value:
            parent[value] = parent[parent[value]]
            value = parent[value]
        return value
    def union(left, right):
        parent[find(right)] = find(left)
    by_group, by_hash = {}, {}
    for image in images:
        if group_field not in image or not str(image[group_field]).strip():
            raise ValueError(f"Every image needs {group_field}; use a unique image ID for independent stills")
        image_id = image["id"]
        group, digest = str(image[group_field]), hashes[image_id]
        for registry, key in ((by_group, group), (by_hash, digest)):
            if key in registry:
                union(image_id, registry[key])
            else:
                registry[key] = image_id
    groups = defaultdict(list)
    for image in images:
        groups[find(image["id"])].append(image["id"])
    ordered = sorted((sorted(ids) for ids in groups.values()), key=lambda ids: ids[0])
    random.Random(seed).shuffle(ordered)
    assignment = {}
    counts = Counter()
    total = len(images)
    for group in ordered:
        split = min(ratios, key=lambda name: (counts[name] / total - ratios[name], ("train", "validation", "test").index(name)))
        for image_id in group:
            assignment[image_id] = split
        counts[split] += len(group)
    return assignment


def partition(document, assignment):
    outputs = {}
    for name in ("train", "validation", "test"):
        image_ids = {image_id for image_id, split in assignment.items() if split == name}
        outputs[name] = {"images": [image for image in document["images"] if image["id"] in image_ids], "annotations": [ann for ann in document["annotations"] if ann["image_id"] in image_ids], "categories": document["categories"]}
    return outputs


def distribution(document):
    counts = Counter(ann["category_id"] for ann in document["annotations"])
    images_with_objects = len({ann["image_id"] for ann in document["annotations"]})
    return {"images": len(document["images"]), "empty_images": len(document["images"]) - images_with_objects, "annotations_per_class": {str(c["id"]): counts[c["id"]] for c in document["categories"]}}


def manifest(source_path, source_hash, prepared, assignment, hashes, config):
    partitions = partition(prepared, assignment)
    return {"schema": "vision-ml-dataset-manifest-1", "source": config["source"], "source_annotation_path": str(source_path), "source_annotation_sha256": source_hash, "seed": config["seed"], "split_ratios_requested": config["split_ratios"], "split_policy": config["split_policy"], "class_mapping": config["classes"], "split_distribution": {name: distribution(part) for name, part in partitions.items()}, "duplicate_images": duplicate_images(hashes), "image_assignments": [{"image_id": image["id"], "split": assignment[image["id"]], "sha256": hashes[image["id"]]} for image in sorted(prepared["images"], key=lambda i: i["id"])]}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--annotations", required=True)
    parser.add_argument("--images", required=True)
    parser.add_argument("--config", default="configs/dataset.json")
    parser.add_argument("--output", required=True, help="Output directory outside Git; source is never changed")
    parser.add_argument("--group-field", default="group_id")
    parser.add_argument("--video-derived", action="store_true", help="Require a recording/session group for every frame")
    args = parser.parse_args()
    source_path = Path(args.annotations)
    output = Path(args.output)
    if output.resolve() == source_path.resolve().parent or source_path.resolve().is_relative_to(output.resolve()):
        raise ValueError("Output must not overwrite source annotations")
    config = json.loads(Path(args.config).read_text(encoding="utf-8"))
    source = json.loads(source_path.read_text(encoding="utf-8"))
    prepared = select_classes(source, config)
    validate_coco(prepared, Path(args.images))
    if not args.video_derived:
        for image in prepared["images"]:
            image.setdefault(args.group_field, f"still:{image['id']}")
    hashes = image_hashes(prepared, args.images)
    assignments = split_grouped(prepared, hashes, config["split_ratios"], config["seed"], args.group_field)
    parts = partition(prepared, assignments)
    report = manifest(source_path, sha256(source_path), prepared, assignments, hashes, config)
    output.mkdir(parents=True, exist_ok=True)
    for name, part in parts.items():
        (output / f"{name}.json").write_text(json.dumps(part, indent=2), encoding="utf-8")
    (output / "manifest.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
    print(json.dumps({"output": str(output), "distribution": report["split_distribution"]}))


if __name__ == "__main__":
    main()
