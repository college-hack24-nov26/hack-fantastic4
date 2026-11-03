"""Optional output filtering after Torchvision's built-in SSD postprocessing."""

import torch
from torchvision.ops import batched_nms


def filter_detections(prediction, score_threshold, nms_iou_threshold, max_detections=None):
    """Return score-sorted detections after an additional classwise NMS pass."""
    if not 0 <= score_threshold <= 1 or not 0 < nms_iou_threshold <= 1:
        raise ValueError("Invalid score or NMS threshold")
    if max_detections is not None and (not isinstance(max_detections, int) or isinstance(max_detections, bool) or max_detections < 1):
        raise ValueError("max_detections must be a positive integer")
    keep = torch.where(prediction["scores"] >= score_threshold)[0]
    selected = batched_nms(prediction["boxes"][keep], prediction["scores"][keep], prediction["labels"][keep], nms_iou_threshold)
    if max_detections is not None:
        selected = selected[:max_detections]
    indices = keep[selected]
    return {key: value[indices] for key, value in prediction.items()}
