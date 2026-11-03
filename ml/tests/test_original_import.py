import json

import pytest
from PIL import Image

from vision_ml.dataset import CocoDetectionDataset
from vision_ml.original_import import import_dataset
from vision_ml.real_probe import approved_train_ids


def fixture(tmp_path):
    images = tmp_path / "images"
    images.mkdir()
    records = []
    for image_id, split, color in ((1, "train", "red"), (2, "train", "blue"), (3, "validation", "green"), (4, "test", "yellow")):
        Image.new("RGB", (32, 24), color).save(images / f"{image_id}.png")
        records.append({"id": image_id, "file_name": f"{image_id}.png", "width": 32, "height": 24, "split": split, "group_id": f"session-{image_id}", "rights": {"permission_basis": "creator_owned", "record_id": f"private-{image_id}", "subject_consent": "documented" if image_id == 1 else "not_applicable"}, "boxes": [{"category_id": 1, "bbox_xywh": [2, 3, 10, 8]}] if image_id == 1 else []})
    source = tmp_path / "source.json"
    source.write_text(json.dumps({"images": records}), encoding="utf-8")
    return source, images, records


def test_import_writes_valid_splits_manifest_and_overlays(tmp_path):
    source, images, _ = fixture(tmp_path)
    output = tmp_path / "output"
    report = import_dataset(source, images, output)
    assert report["split_counts"] == {"train": 2, "validation": 1, "test": 1}
    assert report["duplicate_image_ids"] == []
    assert len(list((output / "overlays").glob("*.png"))) == 4
    assert len(CocoDetectionDataset(output / "train.json", images)) == 2
    assert approved_train_ids(report, [1, 2]) == [1, 2]
    with pytest.raises(ValueError, match="training split"):
        approved_train_ids(report, [1, 4])


@pytest.mark.parametrize("change,match", [
    (lambda rows: rows[0]["boxes"][0].update(bbox_xywh=[30, 1, 10, 8]), "bbox"),
    (lambda rows: rows[0]["boxes"][0].update(category_id=0), "category_id"),
    (lambda rows: rows[0]["rights"].update(subject_consent="not_applicable"), "subject consent"),
    (lambda rows: rows[0].update(width=99), "dimensions"),
    (lambda rows: rows[0]["rights"].update(record_id=""), "permission record"),
    (lambda rows: rows[2].update(group_id="session-1"), "recording group"),
])
def test_invalid_input_fails_before_output(tmp_path, change, match):
    source, images, rows = fixture(tmp_path)
    change(rows)
    source.write_text(json.dumps({"images": rows}), encoding="utf-8")
    output = tmp_path / "output"
    with pytest.raises(ValueError, match=match):
        import_dataset(source, images, output)
    assert not output.exists()


def test_exact_duplicate_cannot_cross_splits(tmp_path):
    source, images, rows = fixture(tmp_path)
    (images / "4.png").write_bytes((images / "1.png").read_bytes())
    source.write_text(json.dumps({"images": rows}), encoding="utf-8")
    with pytest.raises(ValueError, match="duplicate images cross"):
        import_dataset(source, images, tmp_path / "output")


def test_jpeg_and_within_split_duplicate_are_reported(tmp_path):
    source, images, rows = fixture(tmp_path)
    Image.new("RGB", (32, 24), "purple").save(images / "2.jpg")
    rows[1]["file_name"] = "2.jpg"
    (images / "2.png").unlink()
    (images / "4.png").write_bytes((images / "3.png").read_bytes())
    rows[3]["split"] = "validation"
    source.write_text(json.dumps({"images": rows}), encoding="utf-8")
    report = import_dataset(source, images, tmp_path / "output")
    assert report["duplicate_image_ids"] == [[3, 4]]


def test_rejects_format_mismatch(tmp_path):
    source, images, rows = fixture(tmp_path)
    (images / "1.png").write_bytes((images / "2.png").read_bytes())
    (images / "1.png").rename(images / "1.jpg")
    rows[0]["file_name"] = "1.jpg"
    source.write_text(json.dumps({"images": rows}), encoding="utf-8")
    with pytest.raises(ValueError, match="extension and content format"):
        import_dataset(source, images, tmp_path / "output")
