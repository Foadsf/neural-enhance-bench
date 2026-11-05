package org.floss.enhancetool.pipeline;

import android.graphics.Bitmap;
import android.graphics.Point;

import java.util.List;

/**
 * Encapsulates the complete benchmarking result from an enhancement run.
 */
public class InferenceResult {
    public final Bitmap originalBitmap;
    public final Bitmap enhancedBitmap;
    public final String modelName;
    public final int tileSize;
    public final int overlap;

    public final long latencyMs;
    public final float throughputMpPerSec;
    public final long peakMemoryBytes;

    public final double psnr;
    public final double ssim;
    public final double snr;
    public final double mse;

    public final List<Point> originalKeypoints;
    public final List<Point> enhancedKeypoints;
    public final float keypointRetention;

    public InferenceResult(Bitmap originalBitmap,
                           Bitmap enhancedBitmap,
                           String modelName,
                           int tileSize,
                           int overlap,
                           long latencyMs,
                           float throughputMpPerSec,
                           long peakMemoryBytes,
                           double psnr,
                           double ssim,
                           double snr,
                           double mse,
                           List<Point> originalKeypoints,
                           List<Point> enhancedKeypoints,
                           float keypointRetention) {
        this.originalBitmap = originalBitmap;
        this.enhancedBitmap = enhancedBitmap;
        this.modelName = modelName;
        this.tileSize = tileSize;
        this.overlap = overlap;
        this.latencyMs = latencyMs;
        this.throughputMpPerSec = throughputMpPerSec;
        this.peakMemoryBytes = peakMemoryBytes;
        this.psnr = psnr;
        this.ssim = ssim;
        this.snr = snr;
        this.mse = mse;
        this.originalKeypoints = originalKeypoints;
        this.enhancedKeypoints = enhancedKeypoints;
        this.keypointRetention = keypointRetention;
    }

    public double getPeakMemoryMb() {
        return peakMemoryBytes / (1024.0 * 1024.0);
    }
}
