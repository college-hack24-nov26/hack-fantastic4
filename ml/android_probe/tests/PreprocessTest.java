package org.vision.mlprobe;

import java.nio.FloatBuffer;

public final class PreprocessTest {
    public static void main(String[] args) {
        int[] pixels = {0xFFFF0000, 0xFF00FF00, 0xFF0000FF, 0xFFFFFFFF};
        FloatBuffer same = Preprocess.rgbToChw(pixels, 2, 2, 2);
        assertNear(same.get(0), 1f);
        assertNear(same.get(4 + 1), 1f);
        assertNear(same.get(8 + 2), 1f);
        FloatBuffer reduced = Preprocess.rgbToChw(pixels, 2, 2, 1);
        assertNear(reduced.get(0), 0.5f);
        assertNear(reduced.get(1), 0.5f);
        assertNear(reduced.get(2), 0.5f);
        System.out.println("Preprocessing math passed");
    }

    private static void assertNear(float actual, float expected) {
        if (Math.abs(actual - expected) > 1e-6f) throw new AssertionError(actual + " != " + expected);
    }
}
