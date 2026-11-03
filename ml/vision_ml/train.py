"""Config-driven training entry point. Refuses to train without explicit real data paths."""

import argparse
import json
import random
from pathlib import Path

import torch
from torch.utils.data import DataLoader

from .dataset import CocoDetectionDataset, collate
from .evaluate import detection_metrics
from .model import make_model


def load_config(path):
    config = json.loads(Path(path).read_text(encoding="utf-8"))
    if config["architecture"] != "ssdlite320_mobilenet_v3_large":
        raise ValueError("Unsupported architecture")
    for key in ("train_annotations", "train_images", "validation_annotations", "validation_images"):
        if not config.get(key):
            raise ValueError(f"Set {key} to an approved dataset path before training")
    if config["epochs"] < 1 or config["batch_size"] < 1 or config["learning_rate"] <= 0:
        raise ValueError("Invalid training hyperparameters")
    if config.get("early_stopping_patience", 2) < 1:
        raise ValueError("early_stopping_patience must be positive")
    return config


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
    validation = CocoDetectionDataset(config["validation_annotations"], config["validation_images"])
    if len(train) < config["batch_size"] or not validation:
        raise ValueError("Training split must have at least one full batch and validation must be nonempty")
    categories = train.document["categories"]
    if categories != validation.document["categories"] or len(categories) + 1 != config["num_classes_including_background"]:
        raise ValueError("Category mapping differs between splits or config")
    loader = DataLoader(train, batch_size=config["batch_size"], shuffle=True, drop_last=True, collate_fn=collate, num_workers=0)
    model = make_model(config["num_classes_including_background"]).to(device)
    optimizer = torch.optim.SGD(model.parameters(), lr=config["learning_rate"], momentum=0.9)
    output = Path(config["output_dir"])
    output.mkdir(parents=True, exist_ok=True)
    best_ap50 = float("-inf")
    epochs_without_improvement = 0
    for epoch in range(config["epochs"]):
        model.train()
        losses = []
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
        score = validation_ap50(model, validation, device, config["num_classes_including_background"], config.get("selection_score_threshold", 0.05), config.get("selection_iou_threshold", 0.5))
        improved = is_improvement(score, best_ap50)
        if improved:
            best_ap50 = score
            epochs_without_improvement = 0
        else:
            epochs_without_improvement += 1
        checkpoint = {"model": model.state_dict(), "optimizer": optimizer.state_dict(), "epoch": epoch + 1, "config": config, "categories": categories, "validation_ap50": score, "best_validation_ap50": best_ap50}
        torch.save(checkpoint, output / "last.pt")
        if improved:
            torch.save(checkpoint, output / "best.pt")
        print(json.dumps({"epoch": epoch + 1, "mean_training_loss": sum(losses) / len(losses), "internal_validation_ap50": score, "best_validation_ap50": best_ap50, "best_updated": improved}))
        if epochs_without_improvement >= config.get("early_stopping_patience", 2):
            print(json.dumps({"stopped_early_after_epoch": epoch + 1, "reason": "internal validation AP50 did not improve"}))
            break


if __name__ == "__main__":
    main()
