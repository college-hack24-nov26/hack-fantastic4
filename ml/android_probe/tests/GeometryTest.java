package org.vision.mlprobe;

import java.util.Arrays;

public final class GeometryTest {
    public static void main(String[] args) {
        float[] mapped = Geometry.toSource(new float[]{80, 40, 240, 280}, 1024, 768);
        if (!Arrays.equals(mapped, new float[]{256, 96, 768, 672})) {
            throw new AssertionError(Arrays.toString(mapped));
        }
        float[] clipped = Geometry.toSource(new float[]{-10, 0, 330, 320}, 640, 480);
        if (!Arrays.equals(clipped, new float[]{0, 0, 640, 480})) {
            throw new AssertionError(Arrays.toString(clipped));
        }
        try {
            Geometry.toSource(new float[]{0, 0, 1, 1}, 0, 480);
            throw new AssertionError("Expected invalid dimensions");
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
        System.out.println("Geometry mapping passed");
    }
}
