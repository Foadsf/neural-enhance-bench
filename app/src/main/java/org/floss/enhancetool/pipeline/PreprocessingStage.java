package org.floss.enhancetool.pipeline;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

import java.util.Random;

/**
 * Preprocessing stages and synthetic degradation generators for mobile benchmarking.
 * Generates test targets (Siemens star, grid, edge steps) and simulates real-world degradations
 * (low-light, sensor shot/read noise, downsampling).
 */
public final class PreprocessingStage {

    public enum DegradationType {
        NONE("Original / No Degradation"),
        LOW_LIGHT("Low Light & Underexposure"),
        GAUSSIAN_NOISE("Gaussian Sensor Noise"),
        POISSON_NOISE("Poisson Photon Shot Noise"),
        DOWNSCALE_2X("2x Downscaling (Bilinear)"),
        DOWNSCALE_4X("4x Downscaling (Bilinear)");

        public final String displayName;
        DegradationType(String displayName) {
            this.displayName = displayName;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    private PreprocessingStage() {}

    /**
     * Synthesizes a high-contrast resolution and feature benchmark target:
     * - Siemens resolution star in center
     * - Checkerboard calibration grid
     * - Linear gray gradient ramp
     * - High-contrast text and edge steps
     */
    public static Bitmap generateSyntheticBenchmarkTarget(int width, int height) {
        Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);

        // Background neutral gray
        canvas.drawColor(0xFFEEEEEE);

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        // 1. Checkerboard corners in corners
        int checkSize = Math.max(16, width / 20);
        paint.setStyle(Paint.Style.FILL);
        for (int y = 0; y < height; y += checkSize) {
            for (int x = 0; x < width; x += checkSize) {
                if (((x / checkSize) + (y / checkSize)) % 2 == 0) {
                    paint.setColor(0xFF222222);
                } else {
                    paint.setColor(0xFFFAFAFA);
                }
                // Only draw in boundary 1/6 area
                if (x < width / 5 || x > width * 4 / 5 || y < height / 5 || y > height * 4 / 5) {
                    canvas.drawRect(x, y, x + checkSize, y + checkSize, paint);
                }
            }
        }

        // 2. Central white circle for Siemens star
        float cx = width / 2f;
        float cy = height / 2f;
        float radius = Math.min(width, height) * 0.28f;

        paint.setColor(0xFFFFFFFF);
        canvas.drawCircle(cx, cy, radius, paint);

        // Siemens star spokes (36 sectors = 18 black, 18 white)
        paint.setColor(0xFF111111);
        int numSectors = 36;
        for (int i = 0; i < numSectors; i += 2) {
            float startAngle = (float) (i * 360.0 / numSectors);
            float sweepAngle = (float) (360.0 / numSectors);
            canvas.drawArc(cx - radius, cy - radius, cx + radius, cy + radius,
                    startAngle, sweepAngle, true, paint);
        }

        // Concentric rings inside Siemens star
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(0xFF444444);
        paint.setStrokeWidth(2f);
        canvas.drawCircle(cx, cy, radius * 0.33f, paint);
        canvas.drawCircle(cx, cy, radius * 0.66f, paint);
        canvas.drawCircle(cx, cy, radius, paint);

        // 3. Horizontal grayscale step chart (10 steps)
        int barTop = (int) (height * 0.76f);
        int barBottom = (int) (height * 0.84f);
        int numSteps = 10;
        int stepWidth = (int) (width * 0.6f / numSteps);
        int barStartX = (int) (width * 0.2f);

        paint.setStyle(Paint.Style.FILL);
        for (int i = 0; i < numSteps; i++) {
            int lum = (int) (i * 255.0 / (numSteps - 1));
            paint.setColor(Color.rgb(lum, lum, lum));
            int x1 = barStartX + i * stepWidth;
            int x2 = x1 + stepWidth;
            canvas.drawRect(x1, barTop, x2, barBottom, paint);
        }

        // 4. Photogrammetry corner crosses at fixed grid coordinates
        paint.setColor(0xFFFF0055);
        paint.setStrokeWidth(3f);
        int[] crossXs = { (int)(width * 0.25f), (int)(width * 0.75f), (int)(width * 0.25f), (int)(width * 0.75f) };
        int[] crossYs = { (int)(height * 0.25f), (int)(height * 0.25f), (int)(height * 0.70f), (int)(height * 0.70f) };
        for (int i = 0; i < 4; i++) {
            int x = crossXs[i];
            int y = crossYs[i];
            canvas.drawLine(x - 12, y, x + 12, y, paint);
            canvas.drawLine(x, y - 12, x, y + 12, paint);
        }

        return bmp;
    }

    /**
     * Applies synthetic degradation to an input bitmap for reproducible benchmarking.
     */
    public static Bitmap applyDegradation(Bitmap src, DegradationType type) {
        if (src == null || type == DegradationType.NONE) {
            return src;
        }

        int w = src.getWidth();
        int h = src.getHeight();

        switch (type) {
            case DOWNSCALE_2X: {
                int dw = Math.max(1, w / 2);
                int dh = Math.max(1, h / 2);
                return Bitmap.createScaledBitmap(src, dw, dh, true);
            }
            case DOWNSCALE_4X: {
                int dw = Math.max(1, w / 4);
                int dh = Math.max(1, h / 4);
                return Bitmap.createScaledBitmap(src, dw, dh, true);
            }
            case LOW_LIGHT: {
                // Simulate underexposure: 0.25x brightness with gamma curve compression
                Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                int[] px = new int[w * h];
                src.getPixels(px, 0, w, 0, 0, w, h);
                for (int i = 0; i < px.length; i++) {
                    int c = px[i];
                    int a = (c >> 24) & 0xFF;
                    int r = (c >> 16) & 0xFF;
                    int g = (c >> 8) & 0xFF;
                    int b = c & 0xFF;

                    r = (int) (Math.pow(r / 255.0, 1.4) * 65.0);
                    g = (int) (Math.pow(g / 255.0, 1.4) * 65.0);
                    b = (int) (Math.pow(b / 255.0, 1.4) * 65.0);

                    px[i] = (a << 24) | (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
                }
                out.setPixels(px, 0, w, 0, 0, w, h);
                return out;
            }
            case GAUSSIAN_NOISE: {
                // Additive Gaussian noise (sigma = 22)
                Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                int[] px = new int[w * h];
                src.getPixels(px, 0, w, 0, 0, w, h);
                Random rng = new Random(42);
                final double sigma = 22.0;

                for (int i = 0; i < px.length; i++) {
                    int c = px[i];
                    int a = (c >> 24) & 0xFF;
                    int r = (c >> 16) & 0xFF;
                    int g = (c >> 8) & 0xFF;
                    int b = c & 0xFF;

                    double nR = rng.nextGaussian() * sigma;
                    double nG = rng.nextGaussian() * sigma;
                    double nB = rng.nextGaussian() * sigma;

                    int nr = clamp((int) Math.round(r + nR));
                    int ng = clamp((int) Math.round(g + nG));
                    int nb = clamp((int) Math.round(b + nB));

                    px[i] = (a << 24) | (nr << 16) | (ng << 8) | nb;
                }
                out.setPixels(px, 0, w, 0, 0, w, h);
                return out;
            }
            case POISSON_NOISE: {
                // Approximate shot noise scaled to intensity
                Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                int[] px = new int[w * h];
                src.getPixels(px, 0, w, 0, 0, w, h);
                Random rng = new Random(101);

                for (int i = 0; i < px.length; i++) {
                    int c = px[i];
                    int a = (c >> 24) & 0xFF;
                    int r = (c >> 16) & 0xFF;
                    int g = (c >> 8) & 0xFF;
                    int b = c & 0xFF;

                    // Variance proportional to signal intensity
                    double sR = Math.sqrt(Math.max(1.0, r)) * 2.2;
                    double sG = Math.sqrt(Math.max(1.0, g)) * 2.2;
                    double sB = Math.sqrt(Math.max(1.0, b)) * 2.2;

                    int nr = clamp((int) Math.round(r + rng.nextGaussian() * sR));
                    int ng = clamp((int) Math.round(g + rng.nextGaussian() * sG));
                    int nb = clamp((int) Math.round(b + rng.nextGaussian() * sB));

                    px[i] = (a << 24) | (nr << 16) | (ng << 8) | nb;
                }
                out.setPixels(px, 0, w, 0, 0, w, h);
                return out;
            }
            default:
                return src;
        }
    }

    private static int clamp(int v) {
        if (v < 0) return 0;
        if (v > 255) return 255;
        return v;
    }
}
