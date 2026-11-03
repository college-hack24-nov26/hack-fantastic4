import pytest
from PIL import Image

from vision_ml.openimages_materialize import CLASS_MAP, materialize


def sample(tmp_path):
    splits = {}
    for split, source_id, color in (("train", "0000000000000001", "red"), ("validation", "0000000000000002", "blue"), ("test", "0000000000000003", "green")):
        folder = tmp_path / split
        folder.mkdir(exist_ok=True)
        Image.new("RGB", (100, 80), color).save(folder / f"{source_id}.jpg")
        splits[split] = {"images": [{"image_id": source_id, "official_split": split, "source_check_status": "live_license_and_creator_verified", "live_license": "https://creativecommons.org/licenses/by/2.0/", "live_creator": "Example creator", "source_landing_url": "https://www.flickr.com/photos/example/1", "boxes": [{"category_id": 5, "xyxy_normalized": [0.1, 0.2, 0.5, 0.6]}]}]}
    return {"schema": "vision-openimages-v7-verified-source-proposal-1", "class_map": CLASS_MAP, "splits": splits}


def test_materialize_maps_normalized_box_and_splits(tmp_path):
    documents, report = materialize(sample(tmp_path), tmp_path)
    assert report["splits"]["train"] == {"images": 1, "boxes_per_class": {"1": 0, "2": 0, "3": 0, "4": 0, "5": 1}}
    assert documents["train"]["annotations"][0]["bbox"] == pytest.approx([10, 16, 40, 32])
    assert len({document["images"][0]["id"] for document in documents.values()}) == 3


def test_materialize_rejects_unverified_license_and_cross_split_duplicates(tmp_path):
    source = sample(tmp_path)
    source["splits"]["train"]["images"][0]["live_license"] = "https://creativecommons.org/licenses/by-nc/2.0/"
    with pytest.raises(ValueError, match="compatible live license"):
        materialize(source, tmp_path)
    source = sample(tmp_path)
    (tmp_path / "validation" / "0000000000000002.jpg").write_bytes((tmp_path / "train" / "0000000000000001.jpg").read_bytes())
    with pytest.raises(ValueError, match="Duplicate image bytes cross"):
        materialize(source, tmp_path)


def test_materialize_rejects_bad_box(tmp_path):
    source = sample(tmp_path)
    source["splits"]["train"]["images"][0]["boxes"][0]["xyxy_normalized"] = [0.1, 0.2, 1.1, 0.6]
    with pytest.raises(ValueError, match="Out-of-frame"):
        materialize(source, tmp_path)
