"""Small detection evaluator. AP50 is per-class interpolated AP, not COCO mAP."""

from collections import defaultdict
from time import perf_counter

import torch
from torchvision.ops import box_iou


def detection_metrics(predictions, targets, num_classes, score_threshold=0.5, iou_threshold=0.5):
    if len(predictions) != len(targets):
        raise ValueError("Prediction and target lengths differ")
    per_class = {}
    for category in range(1, num_classes):
        rows = []
        total_gt = 0
        for image_index, (pred, target) in enumerate(zip(predictions, targets)):
            gt = target["boxes"][target["labels"] == category].cpu()
            total_gt += len(gt)
            mask = (pred["labels"] == category) & (pred["scores"] >= score_threshold)
            boxes = pred["boxes"][mask].cpu()
            scores = pred["scores"][mask].cpu()
            for box, score in zip(boxes, scores):
                rows.append((float(score), image_index, box))
        rows.sort(key=lambda row: row[0], reverse=True)
        matched = defaultdict(set)
        tp, fp = [], []
        for _, index, box in rows:
            gt = targets[index]["boxes"][targets[index]["labels"] == category].cpu()
            if len(gt):
                overlap = box_iou(box.unsqueeze(0), gt).squeeze(0)
                available = [candidate for candidate in range(len(gt)) if candidate not in matched[index]]
                best = max(available, key=lambda candidate: float(overlap[candidate])) if available else None
                hit = best is not None and float(overlap[best]) >= iou_threshold
            else:
                hit = False
            if hit:
                matched[index].add(best)
            tp.append(int(hit))
            fp.append(int(not hit))
        tp_count, fp_count = sum(tp), sum(fp)
        fn_count = total_gt - tp_count
        precision = tp_count / (tp_count + fp_count) if tp_count + fp_count else 0.0
        recall = tp_count / total_gt if total_gt else None
        ap = None
        if total_gt:
            cumulative_tp = cumulative_fp = 0
            recalls, precisions = [], []
            for hit, false_alarm in zip(tp, fp):
                cumulative_tp += hit
                cumulative_fp += false_alarm
                recalls.append(cumulative_tp / total_gt)
                precisions.append(cumulative_tp / (cumulative_tp + cumulative_fp))
            ap = sum(max((p for r, p in zip(recalls, precisions) if r >= k / 100), default=0.0) for k in range(101)) / 101
        per_class[category] = {"tp": tp_count, "fp": fp_count, "fn": fn_count, "precision": precision, "recall": recall, "ap50": ap}
    aps = [v["ap50"] for v in per_class.values() if v["ap50"] is not None]
    return {"per_class": per_class, "map50": sum(aps) / len(aps) if aps else None}


def latency_ms(model, image, device, warmup=5, iterations=20):
    """Synchronized single-image latency; record device alongside returned value."""
    if iterations < 1:
        raise ValueError("iterations must be positive")
    model.eval()
    def sync():
        if device.type == "cuda":
            torch.cuda.synchronize(device)
    with torch.inference_mode():
        for _ in range(warmup):
            model([image.to(device)])
        sync()
        start = perf_counter()
        for _ in range(iterations):
            model([image.to(device)])
        sync()
    return (perf_counter() - start) * 1000 / iterations
