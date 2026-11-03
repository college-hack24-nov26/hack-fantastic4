"""Evaluate a locally trained checkpoint on a held-out COCO split."""

import argparse
import json
from pathlib import Path

import torch

from .dataset import CocoDetectionDataset
from .evaluate import detection_metrics, latency_ms
from .model import make_model
from .postprocess import filter_detections


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--config", default="configs/evaluate.json")
    parser.add_argument("--checkpoint", required=True)
    parser.add_argument("--annotations", required=True)
    parser.add_argument("--images", required=True)
    args = parser.parse_args()
    config = json.loads(Path(args.config).read_text(encoding="utf-8"))
    device = torch.device("cuda" if config["device"] == "auto" and torch.cuda.is_available() else ("cpu" if config["device"] == "auto" else config["device"]))
    dataset = CocoDetectionDataset(args.annotations, args.images)
    if not dataset:
        raise ValueError("Evaluation split is empty")
    checkpoint = torch.load(args.checkpoint, map_location="cpu", weights_only=True)
    num_classes = len(dataset.document["categories"]) + 1
    if checkpoint["config"]["num_classes_including_background"] != num_classes:
        raise ValueError("Checkpoint class count differs from evaluation data")
    if checkpoint["categories"] != dataset.document["categories"]:
        raise ValueError("Checkpoint category mapping differs from evaluation data")
    model = make_model(num_classes).to(device)
    model.load_state_dict(checkpoint["model"])
    model.eval()
    predictions, targets, strata = [], [], []
    with torch.inference_mode():
        for index in range(len(dataset)):
            image, target = dataset[index]
            prediction = model([image.to(device)])[0]
            predictions.append({key: value.cpu() for key, value in prediction.items()})
            targets.append(target)
            strata.append(dataset.images[index].get("stratum", "unlabeled"))
    result = {
        "dataset_type": config.get("dataset_type", "user_supplied_held_out"),
        "images": len(dataset),
        "device": str(device),
        "score_threshold": config["score_threshold"],
        "iou_threshold": config["iou_threshold"],
        "overall": detection_metrics(predictions, targets, num_classes, config["score_threshold"], config["iou_threshold"]),
        "strata": {},
        "checkpoint_bytes": Path(args.checkpoint).stat().st_size,
    }
    if "operating_score_threshold" in config:
        operating = [filter_detections(prediction, config["operating_score_threshold"], config["operating_nms_iou_threshold"], config.get("operating_max_detections")) for prediction in predictions]
        result["operating_point"] = {
            "score_threshold": config["operating_score_threshold"],
            "nms_iou_threshold": config["operating_nms_iou_threshold"],
            "max_detections": config.get("operating_max_detections"),
            "mean_detections_per_image": sum(len(prediction["boxes"]) for prediction in operating) / len(operating),
            "metrics": detection_metrics(operating, targets, num_classes, config["operating_score_threshold"], config["iou_threshold"]),
        }
    for stratum in config["strata"]:
        indices = [index for index, value in enumerate(strata) if value == stratum]
        result["strata"][stratum] = {"images": len(indices), "metrics": detection_metrics([predictions[i] for i in indices], [targets[i] for i in indices], num_classes, config["score_threshold"], config["iou_threshold"]) if indices else None}
    image, _ = dataset[0]
    result["latency_ms_per_image"] = latency_ms(model, image, device, config["warmup_iterations"], config["timed_iterations"])
    print(json.dumps(result, indent=2))


if __name__ == "__main__":
    main()
