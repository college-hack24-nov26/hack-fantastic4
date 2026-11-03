"""Export one trained SSDLite checkpoint to fixed-shape ONNX for feasibility tests."""

import argparse
import json
from pathlib import Path

import numpy as np
import torch

from .model import make_model


class SingleImageDetector(torch.nn.Module):
    def __init__(self, detector):
        super().__init__()
        self.detector = detector

    def forward(self, images):
        result = self.detector([images[0]])[0]
        return result["boxes"], result["scores"], result["labels"]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--checkpoint", required=True)
    parser.add_argument("--output", required=True)
    parser.add_argument("--verify", action="store_true")
    args = parser.parse_args()
    import onnx

    checkpoint = torch.load(args.checkpoint, map_location="cpu", weights_only=True)
    if checkpoint["config"]["architecture"] != "ssdlite320_mobilenet_v3_large":
        raise ValueError("Unsupported checkpoint architecture")
    detector = make_model(checkpoint["config"]["num_classes_including_background"])
    detector.load_state_dict(checkpoint["model"])
    detector.eval()
    wrapped = SingleImageDetector(detector).eval()
    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    torch.manual_seed(42)
    example = torch.rand(1, 3, 320, 320)
    with torch.inference_mode():
        torch.onnx.export(wrapped, (example,), str(output), opset_version=17, dynamo=False,
                          input_names=["rgb_input"], output_names=["boxes", "scores", "labels"])
    graph = onnx.load(output)
    onnx.checker.check_model(graph)
    report = {"status": "exported", "checkpoint_epoch": checkpoint["epoch"], "bytes": output.stat().st_size,
              "input": "float32 RGB NCHW [1,3,320,320], values [0,1]", "outputs": "boxes float32 xyxy, scores float32, labels int64"}
    if args.verify:
        import onnxruntime as ort

        session = ort.InferenceSession(str(output), providers=["CPUExecutionProvider"])
        with torch.inference_mode():
            reference_boxes, reference_scores, reference_labels = wrapped(example)
        boxes, scores, labels = session.run(None, {"rgb_input": example.numpy()})
        if reference_scores.shape != scores.shape or reference_boxes.shape != boxes.shape or reference_labels.shape != labels.shape:
            raise RuntimeError("ONNX Runtime output shapes differ from PyTorch")
        score_error = float(np.max(np.abs(reference_scores.numpy() - scores))) if scores.size else 0.0
        box_error = float(np.max(np.abs(reference_boxes.numpy() - boxes))) if boxes.size else 0.0
        labels_equal = bool(np.array_equal(reference_labels.numpy(), labels))
        report.update({"labels_equal": labels_equal, "max_score_abs_error": score_error,
                       "max_box_abs_error": box_error, "detections": len(scores)})
        if not labels_equal or score_error > 1e-4 or box_error > 1e-2:
            raise RuntimeError(f"ONNX Runtime differs from PyTorch: {json.dumps(report)}")
    print(json.dumps(report))


if __name__ == "__main__":
    main()
