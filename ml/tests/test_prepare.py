import json

import pytest

from PIL import Image

from vision_ml.dataset import CocoDetectionDataset, validate_coco
from vision_ml.prepare import class_map, distribution, duplicate_images, manifest, partition, select_classes, split_grouped


def config():
    return {"seed": 42, "source": "synthetic", "split_policy": "grouped", "split_ratios": {"train": 0.7, "validation": 0.15, "test": 0.15}, "classes": [{"model_id": 1, "name": "person", "coco_id": 1}, {"model_id": 2, "name": "motorcycle", "coco_id": 4}]}


def source():
    return {"images": [{"id": i, "file_name": f"{i}.png", "width": 20, "height": 20, "group_id": f"session-{i // 2}"} for i in range(8)], "categories": [{"id": 1, "name": "person"}, {"id": 4, "name": "motorcycle"}, {"id": 99, "name": "unselected"}], "annotations": [{"id": 1, "image_id": 0, "category_id": 4, "bbox": [1, 2, 3, 4]}, {"id": 2, "image_id": 1, "category_id": 99, "bbox": [1, 2, 3, 4]}]}


def test_class_remap_and_empty_images(tmp_path):
    selected = select_classes(source(), config())
    assert class_map(config()) == {1: 1, 4: 2}
    assert selected["annotations"][0]["category_id"] == 2
    assert len(selected["annotations"]) == 1
    assert distribution(selected)["empty_images"] == 7
    for image in selected["images"]:
        Image.new("RGB", (20, 20)).save(tmp_path / image["file_name"])
    assert validate_coco(selected, tmp_path)["images"] == 8
    path = tmp_path / "selected.json"
    path.write_text(json.dumps(selected), encoding="utf-8")
    loaded = CocoDetectionDataset(path, tmp_path)
    _, empty_target = loaded[1]
    assert empty_target["boxes"].shape == (0, 4)


def test_grouped_split_determinism_no_leakage():
    selected = select_classes(source(), config())
    hashes = {image["id"]: f"hash-{image['id']}" for image in selected["images"]}
    hashes[6] = hashes[2]  # exact duplicate across source sessions binds both groups
    first = split_grouped(selected, hashes, config()["split_ratios"], 42)
    assert first == split_grouped(selected, hashes, config()["split_ratios"], 42)
    assert first[0] == first[1]
    assert first[2] == first[3] == first[6] == first[7]
    assert sum(len(part["images"]) for part in partition(selected, first).values()) == 8
    assert duplicate_images(hashes) == [[2, 6]]
    report = manifest("source.json", "digest", selected, first, hashes, config())
    assert report["source_annotation_sha256"] == "digest"
    assert len(report["image_assignments"]) == 8
    assert report["split_distribution"][first[0]]["images"] >= 2


def test_video_group_required_and_bad_mapping():
    selected = select_classes(source(), config())
    del selected["images"][0]["group_id"]
    try:
        split_grouped(selected, {image["id"]: str(image["id"]) for image in selected["images"]}, config()["split_ratios"], 42)
    except ValueError as error:
        assert "group_id" in str(error)
    else:
        raise AssertionError("Missing video group was accepted")
    bad = config()
    bad["classes"][1]["model_id"] = 3
    try:
        class_map(bad)
    except ValueError:
        pass
    else:
        raise AssertionError("Noncontiguous model IDs were accepted")


def test_model_builder_requests_no_pretrained_weights(monkeypatch):
    from vision_ml import model

    captured = {}
    def builder(**kwargs):
        captured.update(kwargs)
        return object()
    monkeypatch.setattr(model, "ssdlite320_mobilenet_v3_large", builder)
    model.make_model(6)
    assert captured == {"weights": None, "weights_backbone": None, "num_classes": 6}
    with pytest.raises(ValueError):
        model.make_model(1)
