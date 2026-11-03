# Phase 3A Android CPU inference probe

This isolated Java app tests one ONNX Runtime CPU session on a **physical**
phone. It is intentionally outside `android-app/`, requests no camera or
navigation permissions, and never produces walking guidance. The ML laptop
had no device in `adb devices -l`, so no Android load, inference, or latency
result is claimed here. Coordinate with the UI owner before adapting anything
for their application.

## Verified local artifact and build

- Preferred model: `ml/runs/phase2c/export/ssdlite_phase2b_source.onnx`.
- Model SHA-256: `E6783F59DFA8D477FAFF098AD87E104C0E8721C155A8EB9EB3C239788AD3C8AD`.
- ONNX opset 17. Input: `rgb_input`, float32 `[1,3,320,320]`.
- Outputs: `boxes` float32 `[N,4]`, `scores` float32 `[N]`, `labels` int64 `[N]`.
- Runtime dependency: `com.microsoft.onnxruntime:onnxruntime-android:1.29.0`, CPU execution only. The [official mobile guide](https://onnxruntime.ai/docs/tutorials/mobile/) lists this Android package and CPU provider.
- Build tools tested locally: JDK 21, Gradle 9.5, Android Gradle Plugin 9.3.3, SDK 36. [Android's compatibility table](https://developer.android.com/build/releases/about-agp) lists Gradle 9.5 as sufficient for AGP 9.3.
- A debug APK was built locally with the private model and a permitted Open Images validation photograph. Its packaged model SHA matched the source. This verifies packaging, **not** device execution.

The approved probe photograph currently used is private validation image
`aeb065a113586816.jpg` (1024×768, no EXIF orientation tag), SHA-256
`F5861A80BE03F0EDA6286C5BD192F3D6AACD9633EDD2C7EFEF311BFE37A103C2`.
Keep it and its attribution record private. A different permitted, already
upright JPEG can be used if its rights and hash are recorded. Neither the
photo nor the model belongs in Git.

## Private transfer to the UI laptop

Use an encrypted USB drive or an approved private team channel. On the ML
laptop, in PowerShell, set `$transferRoot` to the actual transfer directory:

```powershell
$transferRoot = Read-Host 'Private transfer directory'
New-Item -ItemType Directory -Force -Path $transferRoot | Out-Null
Copy-Item -LiteralPath 'C:\Users\girid\Documents\Vision\ml\runs\phase2c\export\ssdlite_phase2b_source.onnx' -Destination (Join-Path $transferRoot 'model.onnx')
Copy-Item -LiteralPath 'C:\Users\girid\Documents\Vision\ml\datasets\openimages_v7\images\validation\aeb065a113586816.jpg' -Destination (Join-Path $transferRoot 'approved_photo.jpg')
Get-FileHash (Join-Path $transferRoot 'model.onnx') -Algorithm SHA256
Get-FileHash (Join-Path $transferRoot 'approved_photo.jpg') -Algorithm SHA256
```

On the UI laptop, set `$repo` to its local checkout of the official repository.
Place only these two files in the probe's ignored asset directory and verify
both hashes **before building**:

```powershell
$repo = Read-Host 'UI laptop repository root'
$transferRoot = Read-Host 'Private transfer directory'
$assets = Join-Path $repo 'ml\android_probe\app\src\main\assets'
New-Item -ItemType Directory -Force -Path $assets | Out-Null
Copy-Item -LiteralPath (Join-Path $transferRoot 'model.onnx') -Destination (Join-Path $assets 'model.onnx')
Copy-Item -LiteralPath (Join-Path $transferRoot 'approved_photo.jpg') -Destination (Join-Path $assets 'approved_photo.jpg')
if ((Get-FileHash (Join-Path $assets 'model.onnx') -Algorithm SHA256).Hash -ne 'E6783F59DFA8D477FAFF098AD87E104C0E8721C155A8EB9EB3C239788AD3C8AD') { throw 'Model SHA-256 mismatch' }
if ((Get-FileHash (Join-Path $assets 'approved_photo.jpg') -Algorithm SHA256).Hash -ne 'F5861A80BE03F0EDA6286C5BD192F3D6AACD9633EDD2C7EFEF311BFE37A103C2') { throw 'Photo SHA-256 mismatch' }
```

The app also verifies the model hash after copying it from the APK asset into
its private files directory. The probe fails closed on a mismatch. After the
test, remove the private asset files from the UI checkout and transfer medium
according to the team's data-handling decision. The tracked `.gitignore`
excludes the assets and build output.

## Build and run on a physical phone

Install JDK 17 or newer, Gradle 9.5.x, Android SDK Platform 36 and
platform-tools. Set `ANDROID_HOME` to the SDK root and enable USB debugging
on the phone. From PowerShell:

```powershell
Set-Location (Join-Path $repo 'ml\android_probe')
gradle :app:assembleDebug --no-daemon
$adb = Join-Path $env:ANDROID_HOME 'platform-tools\adb.exe'
& $adb devices -l
& $adb shell getprop ro.kernel.qemu
& $adb shell getprop ro.product.manufacturer
& $adb shell getprop ro.product.model
& $adb shell getprop ro.build.version.release
```

Continue only if exactly one **authorized physical phone** appears with state
`device`; `ro.kernel.qemu` must not report `1`. Then:

```powershell
& $adb install -r '.\app\build\outputs\apk\debug\app-debug.apk'
& $adb shell am force-stop org.vision.mlprobe
& $adb shell am start -n org.vision.mlprobe/.MainActivity
Start-Sleep -Seconds 45
& $adb logcat -d -s 'VISION_ML_PROBE:I' '*:S' | Select-String 'PROBE_RESULT|PROBE_FAILED' | Select-Object -Last 1
& $adb shell dumpsys meminfo org.vision.mlprobe
```

Record the complete `PROBE_RESULT` or `PROBE_FAILED` line privately, together
with device make/model, Android version, and APK/model hashes. Do not report
an emulator or desktop CPU result as a physical-phone result.

The app times asset copy, ORT environment initialization, and model session
loading separately. It measures the first inference and total time for 5
warm-up runs, then performs 30 timed `session.run` calls on a fixed 0.5
synthetic tensor. It
reports median and nearest-rank P95, observed process PSS before/after key
stages, and input/output tensor payload bytes. PSS samples are **not** a true
peak. It validates names, types, shapes, finite scores/boxes, label IDs 1–5,
and one approved photo inference. A failure is logged as `PROBE_FAILED`.

## Preprocessing and integration handoff

- Decode an **upright** RGB photo. This harness does not read EXIF orientation.
  In a camera adapter, rotate by sensor/display metadata before resizing;
  undo front-camera mirroring when mapping boxes to the displayed frame.
- Resize the upright image directly to 320×320 using the explicit bilinear
  pixel-center formula in `Preprocess.java`, matching Torchvision's
  `align_corners=False` geometry. This stretches non-square photos; no crop or
  letterbox is used. Convert interleaved RGB bytes to CHW float32 and divide
  by 255. The ONNX graph performs its own channel normalization with mean and
  standard deviation 0.5; do not apply that normalization twice.
- Output boxes are `[x1,y1,x2,y2]` in 320×320 pixels. `Geometry.java` maps
  them back to the upright source dimensions with `x*width/320` and
  `y*height/320`. If the UI track chooses letterboxing instead, use its own
  inverse scale/padding transform and revalidate; never mix the two mappings.
- `scores` are per-class softmax confidences after model NMS; `labels` use
  IDs 1 person, 2 bicycle, 3 motorcycle, 4 car, 5 chair. Background is 0 and
  is not returned. The raw model may return 300 low-score detections. The
  Phase 2B optional score/NMS filter is not included in this harness.
- For live frames, preserve capture and result timestamps on one monotonic
  clock, reject results older than an agreed stale-frame limit, and discard
  inference failures. The shared stale limit and camera transform still need
  UI/ML agreement. Low confidence or failure must not trigger spoken safety
  guidance; this model is not accurate enough for that use.

## Local verification and remaining blockers

On the ML laptop, `gradle :app:assembleDebug` succeeded twice, including a
build with both ignored private assets. Pure Java geometry and preprocessing
math tests passed. ONNX validation passed and the APK's packaged model hash
matched the source. No physical phone was connected, so **model loading,
operator support, output values, latency, and memory on Android remain
unmeasured**. The UI laptop needs the private model/photo transfer, an
authorized connected phone, and the resulting `PROBE_RESULT` log before any
integration decision. Do not modify `android-app/` or shared contracts based
only on this desktop preparation.
