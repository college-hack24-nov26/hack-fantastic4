# Phase 1C.2B — COCO access and controlled training preparation

## Verified endpoint and TLS diagnosis

The COCO site's [official download listing](https://github.com/cocodataset/cocodataset.github.io/blob/master/dataset/download.htm) points to `http://images.cocodataset.org/annotations/annotations_trainval2017.zip`. HTTP is not used here. HTTPS on that hostname fails **before any redirect**: DNS resolves through `s3-1-w.amazonaws.com` to Amazon S3 addresses, while the presented certificate has subject `CN=s3.amazonaws.com` and SANs for S3 hostnames, not `images.cocodataset.org`. OpenSSL reports `verify error:num=62:hostname mismatch`; Python reports `CERTIFICATE_VERIFY_FAILED`; Windows curl reports `SEC_E_WRONG_PRINCIPAL`. No configured application proxy was detected. Python uses its default OpenSSL trust paths; curl uses Windows Schannel. Their independent hostname failures support an endpoint certificate mismatch rather than a missing CA. No certificate check was bypassed.

Amazon's **HTTPS path-style URL to the same named bucket**, `https://s3.amazonaws.com/images.cocodataset.org/annotations/annotations_trainval2017.zip`, returned 200 with a valid TLS connection, length **252,907,541 bytes**, and ETag `"f4bbac642086de4f52a3fdda2de5fa2c"`. `https://s3.us-east-1.amazonaws.com/images.cocodataset.org/annotations/annotations_trainval2017.zip` returned the same size and ETag. The path-style URL maps the COCO site's bucket name and object key to Amazon's certificate-covered endpoint; it is not a third-party mirror. The annotation archive was fetched from that endpoint only, into ignored `ml/datasets/source/`, with SHA-256 `113a836d90195ee1f884e704da6304dfaaecff1f023f49b6ca93c4aaae470268`.

Extracted files (kept out of Git):

| File | Bytes | SHA-256 |
| --- | ---: | --- |
| `instances_train2017.json` | 469,785,474 | `610fce4944abdeb15354cc765333805529359d12d88f2f711393ca586901d01d` |
| `instances_val2017.json` | 19,987,840 | `e8c7f7908f1d7278341fae127d0da654f102f11bd7b21d8aeefa635b8c810b6f` |

Three sample image URLs in the same bucket returned valid HTTPS HEAD responses (`127`: 205,469 bytes; `11842`: 213,797 bytes; `6004`: 218,249 bytes). **No image body was downloaded.**

## Rights and frozen candidate manifest

The [COCO terms](https://github.com/cocodataset/cocodataset.github.io/blob/master/dataset/termsofuse.htm) license annotations CC BY 4.0 but assign image-rights responsibility to users. The extracted COCO `licenses` table identifies ID 4 as [CC BY 2.0](https://creativecommons.org/licenses/by/2.0/). That license permits copying and adaptation, including commercial use, with appropriate creator credit, license link, and change notice. It does not waive privacy or publicity rights. We screened the candidate subset to COCO metadata ID 4 only; NC, ND, ShareAlike, public-domain-mark, and government-work entries were left out of this initial plan. **A metadata ID is not per-image rights verification.** Live Flickr photo pages/creator attribution were not reachable from this laptop, so all 2,000 entries remain `pending_review`. Future public redistribution or model commercialization needs separate review of the actual use and attribution.

The metadata-only candidate plan is frozen in ignored `ml/datasets/coco_subset_candidate.json`, SHA-256 `ae20eb29d09cec9361a05aa90705daceb6a949464ccf96e0e9f061da69f637c6`. It uses seed 42 and the two official source partitions:

| Split | Images | Person | Bicycle | Car | Motorcycle | Chair | Empty for five classes |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| Train from train2017 | 1,500 | 1,150 | 100 | 241 | 100 | 234 | 150 |
| Internal validation from train2017 | 250 | 190 | 13 | 43 | 11 | 44 | 25 |
| Held-out test from val2017 | 250 | 194 | 12 | 35 | 9 | 51 | 25 |

Counts are **images containing each class**, so one image can count for multiple classes. The imbalance, especially bicycle and motorcycle in validation/test, limits per-class conclusions. The candidate per-image manifest is ignored at `ml/datasets/coco_acquisition_candidate.json`, SHA-256 `757dd092b105b3d5704ba8f1ccb3f5724fe0382723c5c4f11413d92adf0cc785`. Every row has image ID, official partition, official HTTPS S3 URL, Flickr URL, COCO license metadata, permission-basis explanation, and unresolved attribution/rights status. It is **not approved for download**. No image SHA-256 exists yet.

The first **12-image probe proposal** is ignored at `ml/datasets/probe_12_candidate.json`, SHA-256 `f73443d4dd7d3e78a92ceb872998cdad87fd096c1c6de39113a716017c1bc123`. Proposed IDs: train `127, 11842, 6004, 11690, 2347, 165, 459, 1639, 5486, 6484`; internal validation `2823`; held-out `872`. These cover the five classes in candidate metadata and retain the official partition boundary. All have pending rights review. After rights verification and user approval, download only those image bodies, preserve attribution, validate JPEG decode/dimensions and annotation matching, render boxes, run the DataLoader, and repeat one controlled GPU step on real images. Do not run eight epochs in this probe.

## Storage and training decision

The 2,000-image candidate remains roughly **0.3–0.6 GB** of image transfers based on full-archive averages; actual sizes and failures vary. The 12-image HEAD sample was about 0.2 MB each, only a small spot check. The verified annotation archive is 252.9 MB compressed and the two extracted instance files total about 489.8 MB; with the archive retained, annotation storage alone is about 742.7 MB. Reserve at least 2 GB for selected images, annotations, and retries, plus separate checkpoint space. No full image archive was downloaded.

The model uses random detector and backbone weights, six total classes (background 0; person 1; bicycle 2; motorcycle 3; car 4; chair 5), RGB float CHW `[0,1]`, and the Torchvision 320×320 transform. A batch of two, SGD momentum 0.9 and learning rate 0.001 are conservative **starting values** for a GPU probe. Eight epochs with only 1,500 images and random initialization may underfit; no accuracy is predicted. `best.pt` uses internal-validation AP50 at IoU 0.5, score floor 0.05, and patience two. `last.pt` is the most recent epoch. The held-out `val2017` test split must not influence model selection. Re-check license and class distributions, run a real-image memory probe, and assess whether a longer schedule or different sampling is justified before full training.
