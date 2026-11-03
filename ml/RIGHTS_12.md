# Phase 1C.2C — rights audit of the 12-image probe

The COCO annotation archive gives every record below `license=4`, mapped in its `licenses` table to [CC BY 2.0](https://creativecommons.org/licenses/by/2.0/). [COCO's terms](https://github.com/cocodataset/cocodataset.github.io/blob/master/dataset/termsofuse.htm) put image-rights responsibility on the user. CC BY 2.0 generally allows copying and adaptation, including commercial use, **with appropriate creator credit, a link to the license, and notice of changes**; it does not clear privacy, publicity, or third-party rights. The intended immediate use is an internal hackathon/research detector prototype. Public demo imagery, redistribution, and later commercial use need their own review.

The source archive contains image IDs, Flickr static-image URLs, and COCO license IDs, but no creator attribution in the image records. A direct Flickr photo-page check on ID `8033724167` failed with an unreachable host from this laptop; a web search for four photo IDs found no independent record. This audit does **not** equate an unreachable page with a revoked license. It means current source rights and author credit have not been verified. No image body was downloaded. The exact official COCO-bucket HTTPS image URL for each row is in the ignored `ml/datasets/probe_12_candidate.json`.

| COCO ID | Planned split | Original image URL from COCO metadata | Reported license | Creator | Status |
| ---: | --- | --- | --- | --- | --- |
| 127 | train2017 train | [Flickr source](http://farm9.staticflickr.com/8454/8033724167_5fe4b14a99_z.jpg) | CC BY 2.0, ID 4 | unavailable | **Pending** |
| 11842 | train2017 train | [Flickr source](http://farm9.staticflickr.com/8240/8596881625_c867438d0a_z.jpg) | CC BY 2.0, ID 4 | unavailable | **Pending** |
| 6004 | train2017 train | [Flickr source](http://farm6.staticflickr.com/5331/9383582895_64d9a7521b_z.jpg) | CC BY 2.0, ID 4 | unavailable | **Pending** |
| 11690 | train2017 train | [Flickr source](http://farm8.staticflickr.com/7034/6815187123_6f6d30ef26_z.jpg) | CC BY 2.0, ID 4 | unavailable | **Pending** |
| 2347 | train2017 train | [Flickr source](http://farm7.staticflickr.com/6046/6209781526_3686316b50_z.jpg) | CC BY 2.0, ID 4 | unavailable | **Pending** |
| 165 | train2017 train | [Flickr source](http://farm8.staticflickr.com/7066/6886413159_2a3a514898_z.jpg) | CC BY 2.0, ID 4 | unavailable | **Pending** |
| 459 | train2017 train | [Flickr source](http://farm4.staticflickr.com/3051/4649315302_7f9d077ecf_z.jpg) | CC BY 2.0, ID 4 | unavailable | **Pending** |
| 1639 | train2017 train | [Flickr source](http://farm2.staticflickr.com/1221/5122225109_6095bdda30_z.jpg) | CC BY 2.0, ID 4 | unavailable | **Pending** |
| 5486 | train2017 train | [Flickr source](http://farm6.staticflickr.com/5133/5479136945_22b3c8759d_z.jpg) | CC BY 2.0, ID 4 | unavailable | **Pending** |
| 6484 | train2017 train | [Flickr source](http://farm8.staticflickr.com/7251/7638623760_7a49cd68e1_z.jpg) | CC BY 2.0, ID 4 | unavailable | **Pending** |
| 2823 | train2017 internal validation | [Flickr source](http://farm8.staticflickr.com/7027/6667041073_7bb9c42de4_z.jpg) | CC BY 2.0, ID 4 | unavailable | **Pending** |
| 872 | val2017 held-out test | [Flickr source](http://farm9.staticflickr.com/8447/7805810128_605424213d_z.jpg) | CC BY 2.0, ID 4 | unavailable | **Pending** |

**Verdict:** the 12-image manifest is **not approvable yet** under the requested per-image standard. For each row, obtain an accessible creator/source record, verify the current license and intended-use scope, record the creator credit and source/license links, and update the private acquisition manifest. Reject or replace rows that cannot be cleared. After all chosen rows are verified, present the completed exact manifest for user approval before downloading image bodies.

## Bounded probe after approval

`vision_ml.real_probe` is ready to use **after** the exact image acquisition and GPU probe are approved. It requires two distinct `train2017` training IDs with `rights_status=verified`, nonempty creator attribution, and recorded downloaded-file SHA-256 in the private manifest, the matching official annotation hash, and an explicit `--user-approved` flag. It rejects held-out IDs and verifies local image hashes. It checks actual JPEG dimensions and boxes through the existing dataset validator, writes two local inspection overlays, loads a two-image DataLoader batch, and runs exactly one CUDA optimizer step. It reports peak GPU memory and step time without claiming accuracy or saving a model checkpoint. Store inputs and output under ignored private `ml/datasets/` or `ml/runs/` paths.

```powershell
python -m vision_ml.real_probe --train-annotations datasets/source/instances_train2017.json --train-images datasets/probe_images/train2017 --rights-manifest datasets/coco_acquisition_approved.json --image-ids ID_A ID_B --output-dir runs/real_probe --user-approved
```

The command is a readiness tool, not authorization. The currently frozen candidate manifest marks every row pending, so it cannot pass the rights gate.

## Fallback: consented team recordings

Record a small private dataset using the intended Android phone at chest-pocket height with the rear camera facing outward. Obtain documented consent from every identifiable participant; avoid incidental bystanders, number plates, and private spaces without permission. Capture people, bicycles, motorcycles, cars, chairs, and empty/background scenes in daylight and indoor lighting. Vary camera height/tilt, walking-induced motion, partial occlusion, and near/far framing. Record whether 0.5× ultrawide was used; keep camera metadata and recording/session IDs outside Git. Do not infer physical distance from boxes.

Use one agreed annotation guide: five stable class IDs, pixel-space COCO `xywh` boxes around visible object extent, explicit handling for truncated/occluded objects, and reviewed empty frames. Double-check a sample of labels by a second annotator, run the repository's dataset validator and overlay tool, and record class/condition counts. Split by **recording session and participant/location** before training; use a deterministic seed, never mix adjacent video frames across splits, and freeze a held-out test set. Keep raw video, extracted frames, consent records, and annotations in ignored private storage. A small self-collected set can validate the pipeline and phone viewpoint, but cannot establish reliable object detection or safety guidance.
