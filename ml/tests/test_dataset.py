import pytest

from vision_ml.dataset import validate_coco
from vision_ml.evaluate import detection_metrics


def sample():
    return {"images": [{"id": 1, "file_name": "a.png", "width": 32, "height": 32}], "categories": [{"id": 1, "name": "synthetic"}], "annotations": [{"id": 1, "image_id": 1, "category_id": 1, "bbox": [2, 3, 10, 8]}]}


def test_valid_and_bad_boxes():
    record = sample()
    assert validate_coco(record)["annotations"] == 1
    record["annotations"][0]["bbox"] = [30, 3, 10, 8]
    with pytest.raises(ValueError, match="bbox"):
        validate_coco(record)


def test_unknown_class_and_duplicate_ids():
    record = sample()
    record["annotations"][0]["category_id"] = 9
    with pytest.raises(ValueError, match="unknown"):
        validate_coco(record)
    record = sample()
    record["images"].append(dict(record["images"][0]))
    with pytest.raises(ValueError, match="Duplicate"):
        validate_coco(record)


def test_empty_split_is_structurally_valid():
    record = sample()
    record["images"] = []
    record["annotations"] = []
    assert validate_coco(record) == {"images": 0, "annotations": 0, "categories": 1}


def test_metric_matching_and_false_negative():
    import torch
    prediction = {"boxes": torch.tensor([[0., 0., 10., 10.], [20., 20., 30., 30.]]), "labels": torch.tensor([1, 1]), "scores": torch.tensor([0.9, 0.8])}
    target = {"boxes": torch.tensor([[0., 0., 10., 10.], [12., 12., 17., 17.]]), "labels": torch.tensor([1, 1])}
    result = detection_metrics([prediction], [target], 2)
    assert result["per_class"][1]["tp"] == 1
    assert result["per_class"][1]["fp"] == 1
    assert result["per_class"][1]["fn"] == 1
    assert result["per_class"][1]["precision"] == 0.5
    assert result["per_class"][1]["recall"] == 0.5


def test_config_refuses_missing_dataset():
    from vision_ml.train import load_config
    with pytest.raises(ValueError, match="approved dataset"):
        load_config("configs/train.json")


def test_training_config_requires_explicit_no_validation(tmp_path):
    import json
    from vision_ml.train import load_config
    config = json.loads(open("configs/first_experiment.json", encoding="utf-8").read())
    config["train_annotations"] = "private/train.json"
    config["train_images"] = "private/images"
    path = tmp_path / "config.json"
    path.write_text(json.dumps(config), encoding="utf-8")
    with pytest.raises(ValueError, match="allow_no_validation"):
        load_config(path)
    config["allow_no_validation"] = True
    path.write_text(json.dumps(config), encoding="utf-8")
    assert load_config(path)["validation_annotations"] is None
    config["validation_annotations"] = "private/validation.json"
    path.write_text(json.dumps(config), encoding="utf-8")
    with pytest.raises(ValueError, match="both validation paths"):
        load_config(path)


def test_empty_predictions_and_empty_ground_truth():
    import torch
    empty_pred = {"boxes": torch.empty((0, 4)), "labels": torch.empty((0,), dtype=torch.int64), "scores": torch.empty((0,))}
    empty_gt = {"boxes": torch.empty((0, 4)), "labels": torch.empty((0,), dtype=torch.int64)}
    with_gt = {"boxes": torch.tensor([[0., 0., 5., 5.]]), "labels": torch.tensor([1])}
    missed = detection_metrics([empty_pred], [with_gt], 2)["per_class"][1]
    assert (missed["tp"], missed["fp"], missed["fn"], missed["recall"], missed["ap50"]) == (0, 0, 1, 0.0, 0.0)
    absent = detection_metrics([empty_pred], [empty_gt], 2)["per_class"][1]
    assert absent["fn"] == 0 and absent["recall"] is None and absent["ap50"] is None


def test_checkpoint_selection_rule():
    from vision_ml.train import is_improvement
    assert is_improvement(0.4, float("-inf"))
    assert is_improvement(0.41, 0.4)
    assert not is_improvement(0.4, 0.4)
