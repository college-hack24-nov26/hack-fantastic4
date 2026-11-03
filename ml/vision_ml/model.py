"""Explicit random initialization: both detector and backbone weights are None."""

from torchvision.models.detection import ssdlite320_mobilenet_v3_large


def make_model(num_classes: int):
    if num_classes < 2:
        raise ValueError("At least background and one object class are required")
    return ssdlite320_mobilenet_v3_large(
        weights=None, weights_backbone=None, num_classes=num_classes
    )
