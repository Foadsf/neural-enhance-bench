package org.floss.enhancetool.pipeline;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Point;
import org.floss.enhancetool.metrics.QualityMetrics;
import org.floss.enhancetool.metrics.SfmKeypointDetector;
import org.floss.enhancetool.model.EnhancementModel;

import java.util.ArrayList;
import java.util.List;

/**
 * High-performance, memory-conservative Tiled Inference Engine with Hann/Cosine blending.
 * Designed specifically for mobile thermal and heap budgets (Exynos 9825 Mali-G76, 8GB RAM).
 */
public class TiledInferenceEngine {

    private final int tileSize;
    private final int overlap;

    public TiledInferenceEngine(int tileSize, int overlap) {
        this.tileSize = Math.max(32, tileSize);
        // Overlap cannot be half or more of tile size
        this.overlap = Math.min(overlap, this.tileSize / 4);
    }

    public InferenceResult execute(Bitmap inputBitmap,
                                  EnhancementModel model,
                                  InferenceCallback callback) {
        if (inputBitmap == null || model == null) {
            return null;
        }

        long startTime = System.nanoTime();
        long peakMemory = 0;

        int inWidth = inputBitmap.getWidth();
        int inHeight = inputBitmap.getHeight();
        int scale = model.getScaleFactor();

        int outWidth = inWidth * scale;
        int outHeight = inHeight * scale;

        // Calculate tile grid
        int stride = tileSize - overlap;
        List<int[]> tiles = new ArrayList<>();
        for (int y = 0; y < inHeight; y += stride) {
            int th = Math.min(tileSize, inHeight - y);
            for (int x = 0; x < inWidth; x += stride) {
                int tw = Math.min(tileSize, inWidth - x);
                tiles.add(new int[]{x, y, tw, th});
            }
        }

        int totalTiles = tiles.size();

        // High-precision float accumulation buffers for seamless overlap reconstruction
        int totalOutPixels = outWidth * outHeight;
        float[] accumR = new float[totalOutPixels];
        float[] accumG = new float[totalOutPixels];
        float[] accumB = new float[totalOutPixels];
        float[] accumW = new float[totalOutPixels];

        // Process tiles sequentially to preserve strict mobile memory footprint
        for (int i = 0; i < totalTiles; i++) {
            int[] t = tiles.get(i);
            int tx = t[0];
            int ty = t[1];
            int tw = t[2];
            int th = t[3];

            // Slice tile pixels directly from source bitmap (zero intermediate Bitmap overhead)
            int[] inTilePixels = new int[tw * th];
            inputBitmap.getPixels(inTilePixels, 0, tw, tx, ty, tw, th);

            // Execute neural or baseline model on tile pixels
            int[] outPixels = model.processTile(inTilePixels, tw, th);

            int tileOutW = tw * scale;
            int tileOutH = th * scale;

            // Destination base coordinates in output resolution
            int dstBaseX = tx * scale;
            int dstBaseY = ty * scale;
            int scaledOverlap = overlap * scale;

            // Generate 2D blend weights for this tile
            for (int ly = 0; ly < tileOutH; ly++) {
                int globalY = dstBaseY + ly;
                if (globalY >= outHeight) break;

                // Vertical blend weight
                float wy = 1.0f;
                if (scaledOverlap > 0) {
                    if (ty > 0 && ly < scaledOverlap) {
                        // Blend top edge
                        double norm = (ly + 0.5) / scaledOverlap;
                        wy = (float) Math.sin(norm * (Math.PI / 2.0));
                        wy = wy * wy;
                    } else if ((ty + th < inHeight) && (ly >= tileOutH - scaledOverlap)) {
                        // Blend bottom edge
                        double norm = (tileOutH - 1 - ly + 0.5) / scaledOverlap;
                        wy = (float) Math.sin(norm * (Math.PI / 2.0));
                        wy = wy * wy;
                    }
                }

                int outRowOffset = globalY * outWidth;
                int tileRowOffset = ly * tileOutW;

                for (int lx = 0; lx < tileOutW; lx++) {
                    int globalX = dstBaseX + lx;
                    if (globalX >= outWidth) break;

                    // Horizontal blend weight
                    float wx = 1.0f;
                    if (scaledOverlap > 0) {
                        if (tx > 0 && lx < scaledOverlap) {
                            // Blend left edge
                            double norm = (lx + 0.5) / scaledOverlap;
                            wx = (float) Math.sin(norm * (Math.PI / 2.0));
                            wx = wx * wx;
                        } else if ((tx + tw < inWidth) && (lx >= tileOutW - scaledOverlap)) {
                            // Blend right edge
                            double norm = (tileOutW - 1 - lx + 0.5) / scaledOverlap;
                            wx = (float) Math.sin(norm * (Math.PI / 2.0));
                            wx = wx * wx;
                        }
                    }

                    float weight = wx * wy;
                    if (weight <= 0.0001f) weight = 0.0001f;

                    int c = outPixels[tileRowOffset + lx];
                    int r = (c >> 16) & 0xFF;
                    int g = (c >> 8) & 0xFF;
                    int b = c & 0xFF;

                    int dstIdx = outRowOffset + globalX;
                    accumR[dstIdx] += r * weight;
                    accumG[dstIdx] += g * weight;
                    accumB[dstIdx] += b * weight;
                    accumW[dstIdx] += weight;
                }
            }

            // Track memory and notify progress
            long usedMem = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
            if (usedMem > peakMemory) {
                peakMemory = usedMem;
            }

            if (callback != null) {
                callback.onProgress(i + 1, totalTiles, usedMem);
            }
        }

        // Reconstruct blended final output Bitmap
        int[] finalPixels = new int[totalOutPixels];
        for (int i = 0; i < totalOutPixels; i++) {
            float w = accumW[i];
            if (w <= 0.0001f) w = 1.0f;

            int r = clamp((int) Math.round(accumR[i] / w));
            int g = clamp((int) Math.round(accumG[i] / w));
            int b = clamp((int) Math.round(accumB[i] / w));

            finalPixels[i] = 0xFF000000 | (r << 16) | (g << 8) | b;
        }

        Bitmap enhanced = Bitmap.createBitmap(outWidth, outHeight, Bitmap.Config.ARGB_8888);
        enhanced.setPixels(finalPixels, 0, outWidth, 0, 0, outWidth, outHeight);

        long endTime = System.nanoTime();
        long latencyMs = (endTime - startTime) / 1_000_000L;
        float latencySec = Math.max(0.001f, latencyMs / 1000.0f);
        float throughputMp = ((float) (inWidth * inHeight) / 1_000_000.0f) / latencySec;

        // Compute objective metrics
        QualityMetrics.MetricsResult metrics = QualityMetrics.compute(inputBitmap, enhanced);

        // Compute Structure-from-Motion (SfM) geometric keypoints and retention
        List<Point> origPts = SfmKeypointDetector.detectKeypoints(inputBitmap, 300);
        List<Point> enhPts = SfmKeypointDetector.detectKeypoints(enhanced, 300);
        float retention = SfmKeypointDetector.computeRetentionRate(origPts, enhPts, scale, scale, 6.0f);

        return new InferenceResult(
                inputBitmap,
                enhanced,
                model.getName(),
                tileSize,
                overlap,
                latencyMs,
                throughputMp,
                peakMemory,
                metrics.psnr,
                metrics.ssim,
                metrics.snr,
                metrics.mse,
                origPts,
                enhPts,
                retention
        );
    }

    private static int clamp(int v) {
        if (v < 0) return 0;
        if (v > 255) return 255;
        return v;
    }
}
