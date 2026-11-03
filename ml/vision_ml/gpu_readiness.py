"""Bounded synthetic CUDA memory check; never trains on real images."""

import json
from time import perf_counter

import torch

from .model import make_model


def one_step(batch_size):
    torch.cuda.empty_cache()
    torch.cuda.reset_peak_memory_stats()
    free_before, total = torch.cuda.mem_get_info()
    model = make_model(6).cuda()
    model.train()
    if batch_size == 1:
        # A 1x1 feature map makes BatchNorm training statistics undefined for N=1.
        for module in model.modules():
            if isinstance(module, torch.nn.modules.batchnorm._BatchNorm):
                module.eval()
    image = torch.zeros((3, 320, 320), device="cuda")
    image[:, 64:192, 80:200] = 1.0
    target = {"boxes": torch.tensor([[80., 64., 200., 192.]], device="cuda"), "labels": torch.tensor([1], device="cuda")}
    optimizer = torch.optim.SGD(model.parameters(), lr=0.001)
    torch.cuda.synchronize()
    start = perf_counter()
    loss = sum(model([image] * batch_size, [target] * batch_size).values())
    if not torch.isfinite(loss):
        raise RuntimeError("Nonfinite synthetic loss")
    optimizer.zero_grad(set_to_none=True)
    loss.backward()
    optimizer.step()
    torch.cuda.synchronize()
    return {"batch_size": batch_size, "status": "pass", "synthetic_only": True, "elapsed_ms": round((perf_counter() - start) * 1000, 1), "free_before_bytes": free_before, "total_bytes": total, "peak_allocated_bytes": torch.cuda.max_memory_allocated(), "peak_reserved_bytes": torch.cuda.max_memory_reserved(), "batchnorm_running_stats_frozen": batch_size == 1}


def main():
    if not torch.cuda.is_available():
        print(json.dumps({"cuda_available": False}))
        return
    torch.manual_seed(42)
    results = []
    for batch_size in (1, 2):
        free, _ = torch.cuda.mem_get_info()
        if batch_size == 2 and (free < 2_000_000_000 or results[0].get("peak_reserved_bytes", 0) * 2 + 250_000_000 > free):
            results.append({"batch_size": 2, "status": "skipped_insufficient_headroom", "free_bytes": free})
            break
        try:
            results.append(one_step(batch_size))
        except torch.cuda.OutOfMemoryError as error:
            torch.cuda.empty_cache()
            results.append({"batch_size": batch_size, "status": "out_of_memory", "message": str(error).splitlines()[0]})
            break
    print(json.dumps({"cuda_available": True, "results": results}, indent=2))


if __name__ == "__main__":
    main()
