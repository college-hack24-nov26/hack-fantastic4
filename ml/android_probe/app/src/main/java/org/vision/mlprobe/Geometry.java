package org.vision.mlprobe;

/** Inverse of the harness's direct stretch to 320 x 320. */
public final class Geometry {
    private Geometry() {}

    public static float[] toSource(float[] box, int sourceWidth, int sourceHeight) {
        if (box == null || box.length != 4 || sourceWidth <= 0 || sourceHeight <= 0) {
            throw new IllegalArgumentException("Invalid box or source dimensions");
        }
        return new float[]{
            clamp(box[0] * sourceWidth / 320f, 0, sourceWidth),
            clamp(box[1] * sourceHeight / 320f, 0, sourceHeight),
            clamp(box[2] * sourceWidth / 320f, 0, sourceWidth),
            clamp(box[3] * sourceHeight / 320f, 0, sourceHeight)
        };
    }

    private static float clamp(float value, float low, float high) {
        return Math.max(low, Math.min(high, value));
    }
}
