import pytest

from vision_ml.coco_plan import plan
from vision_ml.coco_materialize import materialize


CLASSES = {"classes": [{"model_id": 1, "coco_id": 1, "name": "person"}, {"model_id": 2, "coco_id": 2, "name": "bicycle"}]}


def document(start, count):
    return {
        "categories": [{"id": 1, "name": "person"}, {"id": 2, "name": "bicycle"}],
        "images": [{"id": start + n, "license": 1 if n != 0 else 9, "file_name": f"{start+n}.jpg", "width": 20, "height": 20} for n in range(count)],
        "annotations": [{"id": start + n, "image_id": start + n, "category_id": 1 if n % 2 else 2, "bbox": [1, 2, 3, 4]} for n in range(1, count - 2)],
    }


def test_official_partitions_and_license_filter_are_deterministic():
    train, val = document(0, 30), document(100, 20)
    config = {"seed": 42, "train_images": 12, "internal_validation_images": 5, "held_out_val2017_images": 4, "min_images_per_class_in_train": 2, "empty_fraction": 0.1, "allowed_image_license_ids": [1]}
    first = plan(train, val, CLASSES, config)
    assert first == plan(train, val, CLASSES, config)
    assert len(first["train2017_train"]) == 12
    assert len(first["train2017_internal_validation"]) == 5
    assert len(first["val2017_held_out_test"]) == 4
    assert not (set(first["train2017_train"]) & set(first["train2017_internal_validation"]))
    assert all(image_id < 100 for image_id in first["train2017_train"] + first["train2017_internal_validation"])
    assert all(image_id >= 100 for image_id in first["val2017_held_out_test"])
    assert 0 not in first["train2017_train"]
    assert 100 not in first["val2017_held_out_test"]
    subset = materialize(train, first["train2017_train"], CLASSES)
    assert len(subset["images"]) == 12
    assert all(ann["category_id"] in (1, 2) for ann in subset["annotations"])


def test_no_license_approval_or_partition_overlap_fails():
    train, val = document(0, 30), document(100, 20)
    config = {"seed": 42, "train_images": 12, "internal_validation_images": 5, "held_out_val2017_images": 4, "min_images_per_class_in_train": 2, "empty_fraction": 0.1, "allowed_image_license_ids": None}
    with pytest.raises(ValueError, match="Review image licenses"):
        plan(train, val, CLASSES, config)
    config["allowed_image_license_ids"] = [1]
    val["images"][1]["id"] = train["images"][1]["id"]
    with pytest.raises(ValueError, match="share image IDs"):
        plan(train, val, CLASSES, config)
