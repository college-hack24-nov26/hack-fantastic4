from vision_ml.acquisition_manifest import build_manifest


def document(image_id):
    return {
        "images": [{"id": image_id, "file_name": f"{image_id}.jpg", "license": 4, "flickr_url": "https://example.invalid/photo"}],
        "annotations": [{"image_id": image_id, "category_id": 1, "iscrowd": 0}],
        "licenses": [{"id": 4, "name": "Attribution License", "url": "http://creativecommons.org/licenses/by/2.0/"}],
    }


def test_manifest_never_grants_permission_from_license_id():
    plan = {"source_sha256": {"instances_train2017.json": "a", "instances_val2017.json": "b"}, "train2017_train": [1], "train2017_internal_validation": [], "val2017_held_out_test": [2]}
    result = build_manifest(plan, document(1), document(2))
    assert len(result["images"]) == 2
    assert all(row["rights_status"] == "pending_review" and row["attribution"] is None for row in result["images"])
    assert result["images"][0]["official_image_url"] == "https://s3.amazonaws.com/images.cocodataset.org/train2017/1.jpg"
    assert result["distribution"]["train2017_train"]["images_with_class"]["1"] == 1
