import pytest

from vision_ml.real_probe import approved_train_ids


def test_probe_requires_verified_training_images():
    manifest = {"images": [
        {"image_id": 1, "split": "train2017_train", "rights_status": "verified", "attribution": "Creator A", "image_sha256_after_download": "sha1"},
        {"image_id": 2, "split": "train2017_train", "rights_status": "verified", "attribution": "Creator B", "image_sha256_after_download": "sha2"},
        {"image_id": 3, "split": "val2017_held_out_test", "rights_status": "verified", "attribution": "Creator C", "image_sha256_after_download": "sha3"},
    ]}
    assert approved_train_ids(manifest, [1, 2]) == [1, 2]
    with pytest.raises(ValueError, match="training split"):
        approved_train_ids(manifest, [1, 3])
    manifest["images"][1]["rights_status"] = "pending_review"
    with pytest.raises(ValueError, match="verified rights"):
        approved_train_ids(manifest, [1, 2])
    manifest["images"][1]["rights_status"] = "verified"
    manifest["images"][1]["image_sha256_after_download"] = None
    with pytest.raises(ValueError, match="SHA-256"):
        approved_train_ids(manifest, [1, 2])
    with pytest.raises(ValueError, match="distinct"):
        approved_train_ids(manifest, [1, 1])
