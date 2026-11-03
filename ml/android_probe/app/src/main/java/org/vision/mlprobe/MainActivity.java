package org.vision.mlprobe;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Bundle;
import android.os.Debug;
import android.os.SystemClock;
import android.util.Log;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OnnxJavaType;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;
import ai.onnxruntime.TensorInfo;

/** Offline, single-shot CPU probe. It has no camera or navigation permissions. */
public final class MainActivity extends Activity {
    private static final String TAG = "VISION_ML_PROBE";
    private static final String MODEL_SHA256 = "E6783F59DFA8D477FAFF098AD87E104C0E8721C155A8EB9EB3C239788AD3C8AD";
    private static final int SIZE = 320;
    private static final int WARMUP = 5;
    private static final int REPEATS = 30;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        TextView status = new TextView(this);
        status.setText("Running offline ONNX CPU probe; see VISION_ML_PROBE in logcat.");
        setContentView(status);
        new Thread(() -> {
            try {
                JSONObject report = runProbe();
                Log.i(TAG, "PROBE_RESULT " + report);
                runOnUiThread(() -> status.setText(report.toString()));
            } catch (Exception error) {
                Log.e(TAG, "PROBE_FAILED", error);
                runOnUiThread(() -> status.setText("Probe failed: " + error));
            }
        }, "vision-ml-probe").start();
    }

    private JSONObject runProbe() throws Exception {
        JSONObject report = new JSONObject();
        report.put("device", Build.MANUFACTURER + " " + Build.MODEL);
        report.put("android", Build.VERSION.RELEASE);
        report.put("sdk", Build.VERSION.SDK_INT);
        report.put("runtime_package", "com.microsoft.onnxruntime:onnxruntime-android:1.29.0");
        report.put("execution_provider", "CPU only; no NNAPI requested");
        report.put("warmup_count", WARMUP);
        report.put("timed_count", REPEATS);

        File model = new File(getFilesDir(), "model.onnx");
        long copyStart = SystemClock.elapsedRealtimeNanos();
        try (InputStream source = getAssets().open("model.onnx"); FileOutputStream target = new FileOutputStream(model)) {
            byte[] chunk = new byte[64 * 1024];
            int read;
            while ((read = source.read(chunk)) >= 0) target.write(chunk, 0, read);
        }
        report.put("asset_copy_ms", millisSince(copyStart));
        String hash = sha256(model);
        report.put("model_sha256", hash);
        if (!MODEL_SHA256.equals(hash)) throw new IllegalStateException("Model hash mismatch");
        long environmentStart = SystemClock.elapsedRealtimeNanos();
        OrtEnvironment environment = OrtEnvironment.getEnvironment();
        report.put("environment_init_ms", millisSince(environmentStart));
        report.put("pss_before_load_kb", pssKb());

        try (OrtSession.SessionOptions options = new OrtSession.SessionOptions()) {
            options.setIntraOpNumThreads(2);
            long loadStart = SystemClock.elapsedRealtimeNanos();
            try (OrtSession session = environment.createSession(model.getAbsolutePath(), options)) {
                report.put("session_load_ms", millisSince(loadStart));
                report.put("pss_after_load_kb", pssKb());
                Set<String> inputs = session.getInputNames();
                Set<String> outputs = session.getOutputNames();
                report.put("input_names", new JSONArray(inputs));
                report.put("output_names", new JSONArray(outputs));
                if (!inputs.equals(new HashSet<>(Arrays.asList("rgb_input"))) ||
                    !outputs.equals(new HashSet<>(Arrays.asList("boxes", "scores", "labels")))) {
                    throw new IllegalStateException("Unexpected model input/output names");
                }
                TensorInfo inputInfo = (TensorInfo) session.getInputInfo().get("rgb_input").getInfo();
                TensorInfo boxInfo = (TensorInfo) session.getOutputInfo().get("boxes").getInfo();
                TensorInfo scoreInfo = (TensorInfo) session.getOutputInfo().get("scores").getInfo();
                TensorInfo labelInfo = (TensorInfo) session.getOutputInfo().get("labels").getInfo();
                if (inputInfo.type != OnnxJavaType.FLOAT || !Arrays.equals(inputInfo.getShape(), new long[]{1, 3, SIZE, SIZE}) ||
                    boxInfo.type != OnnxJavaType.FLOAT || scoreInfo.type != OnnxJavaType.FLOAT || labelInfo.type != OnnxJavaType.INT64 ||
                    boxInfo.getShape().length != 2 || boxInfo.getShape()[1] != 4 || scoreInfo.getShape().length != 1 || labelInfo.getShape().length != 1) {
                    throw new IllegalStateException("Unexpected model input/output types or shapes");
                }
                report.put("input_shape", Arrays.toString(inputInfo.getShape()));
                report.put("output_types", "boxes=float32,scores=float32,labels=int64");
                FloatBuffer synthetic = directFloats(3 * SIZE * SIZE);
                for (int i = 0; i < synthetic.capacity(); i++) synthetic.put(i, 0.5f);
                try (OnnxTensor tensor = OnnxTensor.createTensor(environment, synthetic, new long[]{1, 3, SIZE, SIZE})) {
                    Map<String, OnnxTensor> feeds = new HashMap<>();
                    feeds.put("rgb_input", tensor);
                    long firstStart = SystemClock.elapsedRealtimeNanos();
                    try (OrtSession.Result result = session.run(feeds)) {
                        report.put("first_inference_ms", millisSince(firstStart));
                        report.put("synthetic", validate(result));
                    }
                    long warmupStart = SystemClock.elapsedRealtimeNanos();
                    for (int i = 0; i < WARMUP; i++) {
                        try (OrtSession.Result ignored = session.run(feeds)) { /* warm-up */ }
                    }
                    report.put("warmup_total_ms", millisSince(warmupStart));
                    report.put("pss_after_warmup_kb", pssKb());
                    double[] times = new double[REPEATS];
                    for (int i = 0; i < REPEATS; i++) {
                        long start = SystemClock.elapsedRealtimeNanos();
                        try (OrtSession.Result ignored = session.run(feeds)) { times[i] = millisSince(start); }
                    }
                    Arrays.sort(times);
                    report.put("median_inference_ms", (times[14] + times[15]) / 2);
                    report.put("p95_inference_ms", times[(int) Math.ceil(REPEATS * 0.95) - 1]);
                }
                report.put("pss_after_repeats_kb", pssKb());
                report.put("input_tensor_payload_bytes", 3 * SIZE * SIZE * Float.BYTES);
                try (InputStream stream = getAssets().open("approved_photo.jpg")) {
                    Bitmap decoded = BitmapFactory.decodeStream(stream);
                    if (decoded == null) throw new IllegalStateException("Photo decoding failed");
                    report.put("photo_original_width", decoded.getWidth());
                    report.put("photo_original_height", decoded.getHeight());
                    FloatBuffer photo = bitmapToChw(decoded);
                    report.put("photo_top_left_rgb_0_1", new JSONArray(new float[]{photo.get(0), photo.get(SIZE * SIZE), photo.get(2 * SIZE * SIZE)}));
                    decoded.recycle();
                    try (OnnxTensor tensor = OnnxTensor.createTensor(environment, photo, new long[]{1, 3, SIZE, SIZE})) {
                        Map<String, OnnxTensor> feeds = new HashMap<>();
                        feeds.put("rgb_input", tensor);
                        try (OrtSession.Result result = session.run(feeds)) {
                            JSONObject photoOutput = validate(result);
                            float[][] boxes = (float[][]) ((OnnxTensor) result.get("boxes").get()).getValue();
                            JSONArray mapped = new JSONArray();
                            for (int i = 0; i < Math.min(5, boxes.length); i++) {
                                mapped.put(new JSONArray(Geometry.toSource(boxes[i], report.getInt("photo_original_width"), report.getInt("photo_original_height"))));
                            }
                            photoOutput.put("top_five_boxes_original_xyxy", mapped);
                            report.put("photo", photoOutput);
                        }
                    }
                }
                report.put("pss_after_photo_kb", pssKb());
            }
        }
        report.put("max_observed_pss_kb", Math.max(Math.max(report.getLong("pss_before_load_kb"), report.getLong("pss_after_load_kb")),
                Math.max(report.getLong("pss_after_warmup_kb"), Math.max(report.getLong("pss_after_repeats_kb"), report.getLong("pss_after_photo_kb")))));
        return report;
    }

    private static JSONObject validate(OrtSession.Result result) throws Exception {
        float[][] boxes = (float[][]) ((OnnxTensor) result.get("boxes").get()).getValue();
        float[] scores = (float[]) ((OnnxTensor) result.get("scores").get()).getValue();
        long[] labels = (long[]) ((OnnxTensor) result.get("labels").get()).getValue();
        if (boxes.length != scores.length || scores.length != labels.length || scores.length > 300) {
            throw new IllegalStateException("Invalid output lengths");
        }
        for (int i = 0; i < scores.length; i++) {
            float[] b = boxes[i];
            if (b.length != 4 || !Float.isFinite(scores[i]) || scores[i] < 0 || scores[i] > 1 || labels[i] < 1 || labels[i] > 5 ||
                !Float.isFinite(b[0]) || !Float.isFinite(b[1]) || !Float.isFinite(b[2]) || !Float.isFinite(b[3]) ||
                b[0] < -0.01f || b[1] < -0.01f || b[2] > SIZE + 0.01f || b[3] > SIZE + 0.01f || b[0] > b[2] || b[1] > b[3]) {
                throw new IllegalStateException("Invalid detection tensor at index " + i);
            }
        }
        JSONObject summary = new JSONObject();
        summary.put("detections", scores.length);
        summary.put("output_tensor_payload_bytes", scores.length * (4 * Float.BYTES + Float.BYTES + Long.BYTES));
        JSONArray top = new JSONArray();
        for (int i = 0; i < Math.min(5, scores.length); i++) {
            JSONObject item = new JSONObject();
            item.put("label", labels[i]);
            item.put("score", scores[i]);
            item.put("box_xyxy", new JSONArray(new float[]{boxes[i][0], boxes[i][1], boxes[i][2], boxes[i][3]}));
            top.put(item);
        }
        summary.put("top_five", top);
        return summary;
    }

    private static FloatBuffer bitmapToChw(Bitmap source) {
        int[] pixels = new int[source.getWidth() * source.getHeight()];
        source.getPixels(pixels, 0, source.getWidth(), 0, 0, source.getWidth(), source.getHeight());
        return Preprocess.rgbToChw(pixels, source.getWidth(), source.getHeight(), SIZE);
    }

    private static FloatBuffer directFloats(int count) {
        return ByteBuffer.allocateDirect(count * Float.BYTES).order(ByteOrder.nativeOrder()).asFloatBuffer();
    }

    private static long pssKb() {
        Debug.MemoryInfo info = new Debug.MemoryInfo();
        Debug.getMemoryInfo(info);
        return info.getTotalPss();
    }

    private static double millisSince(long started) {
        return (SystemClock.elapsedRealtimeNanos() - started) / 1_000_000.0;
    }

    private static String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = Files.newInputStream(file.toPath())) {
            byte[] chunk = new byte[64 * 1024];
            int read;
            while ((read = input.read(chunk)) >= 0) digest.update(chunk, 0, read);
        }
        StringBuilder result = new StringBuilder();
        for (byte value : digest.digest()) result.append(String.format("%02X", value));
        return result.toString();
    }
}
