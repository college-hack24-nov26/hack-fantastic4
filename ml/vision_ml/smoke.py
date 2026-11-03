"""One synthetic optimizer step; only tests plumbing, never model quality."""

import argparse
import json

import torch

from .model import make_model


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--device", choices=("cpu", "cuda"), default="cpu")
    args = parser.parse_args()
    torch.manual_seed(42)
    device = torch.device(args.device)
    model = make_model(2).to(device)
    model.train()
    image = torch.zeros((3, 320, 320), device=device)
    image[:, 64:192, 80:200] = 1.0
    target = {"boxes": torch.tensor([[80., 64., 200., 192.]], device=device), "labels": torch.tensor([1], device=device)}
    optimizer = torch.optim.SGD(model.parameters(), lr=0.001)
    losses = model([image, image.clone()], [target, target])
    loss = sum(losses.values())
    if not torch.isfinite(loss):
        raise RuntimeError("Smoke loss is not finite")
    optimizer.zero_grad(set_to_none=True)
    loss.backward()
    optimizer.step()
    print(json.dumps({"status": "pass", "data": "synthetic", "purpose": "infrastructure only", "batch_size": 2, "optimizer_steps": 1, "device": str(device), "loss_finite": True}))


if __name__ == "__main__":
    main()
