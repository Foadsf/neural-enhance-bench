package org.floss.enhancetool.pipeline;

import android.graphics.Bitmap;

/**
 * Post-processing routines: unsharp masking, normalization, dynamic range clamping.
 */
public final class PostprocessingStage {

    private PostprocessingStage() {}

    /**
     * Applies subtle unsharp masking for edge sharpening while avoiding halo artifacts.
     * @param src Input bitmap
     * @param amount Sharpening strength [0.0, 1.0]
     */
    public static Bitmap unsharpMask(Bitmap src, float amount) {
        if (src == null || amount <= 0.01f) return src;

        int w = src.getWidth();
        int h = src.getHeight();
        int total = w * h;

        int[] inPx = new int[total];
        int[] outPx = new int[total];
        src.getPixels(inPx, 0, w, 0, 0, w, h);

        // Simple 3x3 Gaussian approximation
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int c = inPx[y * w + x];
                int a = (c >> 24) & 0xFF;
                int r = (c >> 16) & 0xFF;
                int g = (c >> 8) & 0xFF;
                int b = c & 0xFF;

                // 3x3 neighborhood mean
                float sumR = 0, sumG = 0, sumB = 0;
                int count = 0;
                for (int dy = -1; dy <= 1; dy++) {
                    int ny = y + dy;
                    if (ny < 0 || ny >= h) continue;
                    for (int dx = -1; dx <= 1; dx++) {
                        int nx = x + dx;
                        if (nx < 0 || nx >= w) continue;
                        int nc = inPx[ny * w + nx];
                        sumR += (nc >> 16) & 0xFF;
                        sumG += (nc >> 8) & 0xFF;
                        sumB += nc & 0xFF;
                        count++;
                    }
                }

                float blurR = sumR / count;
                float blurG = sumG / count;
                float blurB = sumB / count;

                // High pass: original + amount * (original - blur)
                int nr = clamp((int) Math.round(r + amount * (r - blurR)));
                int ng = clamp((int) Math.round(g + amount * (g - blurG)));
                int nb = clamp((int) Math.round(b + amount * (b - blurB)));

                outPx[y * w + x] = (a << 24) | (nr << 16) | (ng << 8) | nb;
            }
        }

        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        out.setPixels(outPx, 0, w, 0, 0, w, h);
        return out;
    }

    private static int clamp(int v) {
        if (v < 0) return 0;
        if (v > 255) return 255;
        return v;
    }
}
