package org.vision.mlprobe;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/** RGB HWC pixels to float32 CHW with PyTorch-style bilinear resize. */
public final class Preprocess {
    private Preprocess() {}

    public static FloatBuffer rgbToChw(int[] argb, int width, int height, int size) {
        if (argb == null || width <= 0 || height <= 0 || size <= 0 || argb.length != width * height) {
            throw new IllegalArgumentException("Invalid image dimensions");
        }
        int plane = size * size;
        FloatBuffer result = ByteBuffer.allocateDirect(3 * plane * Float.BYTES)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        for (int y = 0; y < size; y++) {
            float sourceY = (y + 0.5f) * height / size - 0.5f;
            int y0 = (int) Math.floor(sourceY);
            int y1 = y0 + 1;
            float wy = sourceY - y0;
            int cy0 = clamp(y0, height - 1);
            int cy1 = clamp(y1, height - 1);
            for (int x = 0; x < size; x++) {
                float sourceX = (x + 0.5f) * width / size - 0.5f;
                int x0 = (int) Math.floor(sourceX);
                int x1 = x0 + 1;
                float wx = sourceX - x0;
                int cx0 = clamp(x0, width - 1);
                int cx1 = clamp(x1, width - 1);
                int a = argb[cy0 * width + cx0];
                int b = argb[cy0 * width + cx1];
                int c = argb[cy1 * width + cx0];
                int d = argb[cy1 * width + cx1];
                int offset = y * size + x;
                for (int channel = 0; channel < 3; channel++) {
                    int shift = 16 - 8 * channel;
                    float upper = ((a >> shift) & 255) * (1 - wx) + ((b >> shift) & 255) * wx;
                    float lower = ((c >> shift) & 255) * (1 - wx) + ((d >> shift) & 255) * wx;
                    result.put(channel * plane + offset, (upper * (1 - wy) + lower * wy) / 255f);
                }
            }
        }
        return result;
    }

    private static int clamp(int value, int maximum) {
        return Math.max(0, Math.min(maximum, value));
    }
}
