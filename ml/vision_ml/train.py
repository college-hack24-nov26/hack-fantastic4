"""Config-driven training entry point. Refuses to train without explicit real data paths."""

import argparse
import json
import random
from pathlib import Path
from time import perf_counter

import torch
from torch.utils.data import DataLoader

from .dataset import CocoDetectionDataset, collate
from .evaluate import detection_metrics
from .model import make_model


def load_config(path):
    config = json.loads(Path(path).read_text(encoding="utf-8"))
    if config["architecture"] != "ssdlite320_mobilenet_v3_large":
        raise ValueError("Unsupported architecture")
    for key in ("train_annotations", "train_images"):
        if not config.get(key):
            raise ValueError(f"Set {key} to an approved dataset path before training")
    validation_paths = (bool(config.get("validation_annotations")), bool(config.get("validation_images")))
    if validation_paths[0] != validation_paths[1]:
        raise ValueError("Set both validation paths, or neither")
    if not any(validation_paths) and config.get("allow_no_validation") is not True:
        raise ValueError("Set both validation paths or explicitly set allow_no_validation=true")
    if config["epochs"] < 1 or config["batch_size"] < 1 or config["learning_rate"] <= 0:
        raise ValueError("Invalid training hyperparameters")
    if config.get("early_stopping_patience", 2) < 1:
        raise ValueError("early_stopping_patience must be positive")
    return config


def epoch_memory(device):
    if device.type != "cuda":
        return {"peak_allocated_bytes": None, "peak_reserved_bytes": None}
    return {"peak_allocated_bytes": torch.cuda.max_memory_allocated(), "peak_reserved_bytes": torch.cuda.max_memory_reserved()}


def validation_ap50(model, dataset, device, num_classes, score_threshold=0.05, iou_threshold=0.5):
    """Internal validation only; this function must never receive held-out test data."""
    model.eval()
    predictions, targets = [], []
    with torch.inference_mode():
        for index in range(len(dataset)):
            image, target = dataset[index]
            prediction = model([image.to(device)])[0]
            predictions.append({key: value.cpu() for key, value in prediction.items()})
            targets.append(target)
    score = detection_metrics(predictions, targets, num_classes, score_threshold, iou_threshold)["map50"]
    if score is None:
        raise ValueError("Internal validation has no selected-class ground truth")
    return score


def is_improvement(score, best):
    return score > best + 1e-12


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--config", required=True)
    args = parser.parse_args()
    config = load_config(args.config)
    random.seed(config["seed"])
    torch.manual_seed(config["seed"])
    device = torch.device("cuda" if config["device"] == "auto" and torch.cuda.is_available() else ("cpu" if config["device"] == "auto" else config["device"]))
    train = CocoDetectionDataset(config["train_annotations"], config["train_images"])
    validation = CocoDetectionDataset(config["validation_annotations"], config["validation_images"]) if config.get("validation_annotations") else None
    if len(train) < config["batch_size"]:
        raise ValueError("Training split must have at least one full batch")
    if validation is not None and not len(validation):
        raise ValueError("Validation split must be nonempty")
    categories = train.document["categories"]
    if len(categories) + 1 != config["num_classes_including_background"] or (validation is not None and categories != validation.document["categories"]):
        raise ValueError("Category mapping differs between splits or config")
    if validation is not None and {image["id"] for image in train.images} & {image["id"] for image in validation.images}:
        raise ValueError("Training and validation image IDs overlap")
    loader = DataLoader(train, batch_size=config["batch_size"], shuffle=True, drop_last=True, collate_fn=collate, num_workers=0)
    model = make_model(config["num_classes_including_background"]).to(device)
    optimizer = torch.optim.SGD(model.parameters(), lr=config["learning_rate"], momentum=0.9)
    output = Path(config["output_dir"])
    if any((output / name).exists() for name in ("last.pt", "best.pt")):
        raise ValueError("Output contains an existing checkpoint; choose a new run directory")
    output.mkdir(parents=True, exist_ok=True)
    best_ap50 = float("-inf")
    epochs_without_improvement = 0
    for epoch in range(config["epochs"]):
        model.train()
        losses = []
        if device.type == "cuda":
            torch.cuda.reset_peak_memory_stats()
            torch.cuda.synchronize()
        started = perf_counter()
        try:
            for images, targets in loader:
                images = [image.to(device) for image in images]
                targets = [{key: value.to(device) for key, value in target.items()} for target in targets]
                loss = sum(model(images, targets).values())
                if not torch.isfinite(loss):
                    raise RuntimeError("Nonfinite loss")
                optimizer.zero_grad(set_to_none=True)
                loss.backward()
                optimizer.step()
                losses.append(float(loss.detach()))
            score = validation_ap50(model, validation, device, config["num_classes_including_background"], config.get("selection_score_threshold", 0.05), config.get("selection_iou_threshold", 0.5)) if validation is not None else None
        except torch.cuda.OutOfMemoryError as error:
            print(json.dumps({"status": "cuda_oom", "epoch": epoch + 1, **epoch_memory(device)}), flush=True)
            raise RuntimeError("CUDA out of memory; incomplete epoch was not checkpointed") from error
        if device.type == "cuda":
            torch.cuda.synchronize()
        elapsed = perf_counter() - started
        improved = validation is not None and is_improvement(score, best_ap50)
        if validation is not None:
            if improved:
                best_ap50 = score
                epochs_without_improvement = 0
            else:
                epochs_without_improvement += 1
        checkpoint = {"model": model.state_dict(), "optimizer": optimizer.state_dict(), "epoch": epoch + 1, "config": config, "categories": categories, "validation_ap50": score, "best_validation_ap50": best_ap50 if validation is not None else None}
        torch.save(checkpoint, output / "last.pt")
        if improved:
            torch.save(checkpoint, output / "best.pt")
        print(json.dumps({"epoch": epoch + 1, "mean_training_loss": sum(losses) / len(losses), "internal_validation_ap50": score, "validation_status": "measured" if validation is not None else "not_available", "best_validation_ap50": best_ap50 if validation is not None else None, "best_updated": improved, "epoch_seconds": round(elapsed, 3), **epoch_memory(device)}), flush=True)
        if validation is not None and epochs_without_improvement >= config.get("early_stopping_patience", 2):
            print(json.dumps({"stopped_early_after_epoch": epoch + 1, "reason": "internal validation AP50 did not improve"}))
            break


if __name__ == "__main__":
    main()
