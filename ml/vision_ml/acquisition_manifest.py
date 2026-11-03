"""Create a candidate per-image acquisition manifest; no network requests."""

import argparse
import hashlib
import json
from collections import Counter, defaultdict
from pathlib import Path


def build_manifest(plan, train_doc, val_doc):
    sources = {
        "train2017_train": ("train2017", train_doc),
        "train2017_internal_validation": ("train2017", train_doc),
        "val2017_held_out_test": ("val2017", val_doc),
    }
    records = []
    distributions = {}
    for split, (partition, doc) in sources.items():
        by_id = {image["id"]: image for image in doc["images"]}
        licenses = {item["id"]: item for item in doc["licenses"]}
        labels = defaultdict(set)
        for ann in doc["annotations"]:
            if ann["category_id"] in {1, 2, 3, 4, 62} and not ann.get("iscrowd", 0):
                labels[ann["image_id"]].add(ann["category_id"])
        counts = Counter()
        for image_id in plan[split]:
            image = by_id[image_id]
            license_record = licenses[image["license"]]
            if image["license"] != 4 or not image["file_name"].endswith(".jpg"):
                raise ValueError("Candidate has unexpected license or filename")
            for class_id in labels[image_id]:
                counts[class_id] += 1
            records.append({
                "image_id": image_id,
                "split": split,
                "source_partition": partition,
                "official_image_url": f"https://s3.amazonaws.com/images.cocodataset.org/{partition}/{image['file_name']}",
                "flickr_url": image.get("flickr_url"),
                "license_id": image["license"],
                "license_name": license_record["name"],
                "license_url": license_record["url"].replace("http://", "https://", 1),
                "permission_basis": "COCO metadata says CC BY 2.0; individual image license, attribution, and intended-use review pending",
                "rights_status": "pending_review",
                "attribution": None,
                "image_sha256_after_download": None,
            })
        distributions[split] = {"images": len(plan[split]), "images_with_class": {str(k): counts[k] for k in (1, 2, 3, 4, 62)}, "empty_for_selected_classes": len(plan[split]) - len({image_id for image_id in plan[split] if labels[image_id]})}
    if len({record["image_id"] for record in records}) != len(records):
        raise ValueError("Image ID leakage across splits")
    return {"status": "candidate_not_approved_for_download", "annotation_hashes": plan["source_sha256"], "source": "official COCO 2017 annotations", "license_screen": "CC BY 2.0 metadata only; per-image review pending", "distribution": distributions, "images": records}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--plan", required=True)
    parser.add_argument("--train-annotations", required=True)
    parser.add_argument("--val-annotations", required=True)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()
    plan_path = Path(args.plan)
    plan = json.loads(plan_path.read_text(encoding="utf-8"))
    raw = {"instances_train2017.json": Path(args.train_annotations).read_bytes(), "instances_val2017.json": Path(args.val_annotations).read_bytes()}
    if any(hashlib.sha256(content).hexdigest() != plan["source_sha256"][name] for name, content in raw.items()):
        raise ValueError("Annotation hash differs from frozen plan")
    manifest = build_manifest(plan, json.loads(raw["instances_train2017.json"]), json.loads(raw["instances_val2017.json"]))
    manifest["plan_sha256"] = hashlib.sha256(plan_path.read_bytes()).hexdigest()
    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(manifest, indent=2), encoding="utf-8")
    print(json.dumps({"images": len(manifest["images"]), "distribution": manifest["distribution"]}))


if __name__ == "__main__":
    main()
