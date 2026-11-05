package org.floss.enhancetool.metrics;

import android.graphics.Bitmap;
import android.graphics.Color;

/**
 * Objective image quality assessment metrics:
 * - PSNR (Peak Signal-to-Noise Ratio)
 * - SSIM (Structural Similarity Index Measure)
 * - SNR (Signal-to-Noise Ratio)
 * - MSE (Mean Squared Error)
 */
public final class QualityMetrics {

    private QualityMetrics() {}

    public static class MetricsResult {
        public final double psnr;
        public final double ssim;
        public final double snr;
        public final double mse;

        public MetricsResult(double psnr, double ssim, double snr, double mse) {
            this.psnr = psnr;
            this.ssim = ssim;
            this.snr = snr;
            this.mse = mse;
        }
    }

    /**
     * Compute full suite of metrics between reference and enhanced image.
     * Note: If dimensions differ (e.g. super-resolution), the reference is bilinearly
     * scaled to match target dimensions for relative quality evaluation.
     */
    public static MetricsResult compute(Bitmap ref, Bitmap target) {
        if (ref == null || target == null) {
            return new MetricsResult(0, 0, 0, 0);
        }

        Bitmap scaledRef = ref;
        boolean needRecycle = false;
        if (ref.getWidth() != target.getWidth() || ref.getHeight() != target.getHeight()) {
            scaledRef = Bitmap.createScaledBitmap(ref, target.getWidth(), target.getHeight(), true);
            needRecycle = true;
        }

        int width = target.getWidth();
        int height = target.getHeight();
        int totalPixels = width * height;

        int[] p1 = new int[totalPixels];
        int[] p2 = new int[totalPixels];
        scaledRef.getPixels(p1, 0, width, 0, 0, width, height);
        target.getPixels(p2, 0, width, 0, 0, width, height);

        if (needRecycle && scaledRef != ref) {
            scaledRef.recycle();
        }

        double sumSqErr = 0.0;
        double signalPower = 0.0;
        double noisePower = 0.0;

        for (int i = 0; i < totalPixels; i++) {
            int c1 = p1[i];
            int c2 = p2[i];

            int r1 = (c1 >> 16) & 0xFF;
            int g1 = (c1 >> 8) & 0xFF;
            int b1 = c1 & 0xFF;

            int r2 = (c2 >> 16) & 0xFF;
            int g2 = (c2 >> 8) & 0xFF;
            int b2 = c2 & 0xFF;

            // Greyscale / luminance conversion for signal & SSIM
            double y1 = 0.299 * r1 + 0.587 * g1 + 0.114 * b1;
            double y2 = 0.299 * r2 + 0.587 * g2 + 0.114 * b2;

            double diffR = r1 - r2;
            double diffG = g1 - g2;
            double diffB = b1 - b2;

            sumSqErr += (diffR * diffR + diffG * diffG + diffB * diffB) / 3.0;

            signalPower += y1 * y1;
            double diffY = y1 - y2;
            noisePower += diffY * diffY;
        }

        double mse = sumSqErr / (double) totalPixels;
        double psnr = (mse <= 1e-10) ? 99.0 : 10.0 * Math.log10((255.0 * 255.0) / mse);

        double snr = (noisePower <= 1e-10) ? 99.0 : 10.0 * Math.log10(signalPower / noisePower);

        double ssim = calculateSSIM(p1, p2, width, height);

        return new MetricsResult(psnr, ssim, snr, mse);
    }

    /**
     * Standard block-based Structural Similarity Index (SSIM) on luminance channel.
     * Block size 8x8 with constants C1=(0.01*255)^2, C2=(0.03*255)^2.
     */
    public static double calculateSSIM(int[] p1, int[] p2, int width, int height) {
        final double C1 = 6.5025;   // (0.01 * 255)^2
        final double C2 = 58.5225;  // (0.03 * 255)^2
        final int BLOCK_SIZE = 8;

        int numBlocksX = width / BLOCK_SIZE;
        int numBlocksY = height / BLOCK_SIZE;
        if (numBlocksX == 0 || numBlocksY == 0) return 1.0;

        double totalSSIM = 0.0;
        int blockCount = 0;

        for (int by = 0; by < numBlocksY; by++) {
            for (int bx = 0; bx < numBlocksX; bx++) {
                int startX = bx * BLOCK_SIZE;
                int startY = by * BLOCK_SIZE;

                double sumX = 0, sumY = 0;
                int N = BLOCK_SIZE * BLOCK_SIZE;

                for (int y = 0; y < BLOCK_SIZE; y++) {
                    int rowIdx = (startY + y) * width;
                    for (int x = 0; x < BLOCK_SIZE; x++) {
                        int idx = rowIdx + startX + x;
                        int c1 = p1[idx];
                        int c2 = p2[idx];
                        double l1 = 0.299 * ((c1 >> 16) & 0xFF) + 0.587 * ((c1 >> 8) & 0xFF) + 0.114 * (c1 & 0xFF);
                        double l2 = 0.299 * ((c2 >> 16) & 0xFF) + 0.587 * ((c2 >> 8) & 0xFF) + 0.114 * (c2 & 0xFF);
                        sumX += l1;
                        sumY += l2;
                    }
                }

                double muX = sumX / N;
                double muY = sumY / N;

                double varX = 0, varY = 0, covXY = 0;
                for (int y = 0; y < BLOCK_SIZE; y++) {
                    int rowIdx = (startY + y) * width;
                    for (int x = 0; x < BLOCK_SIZE; x++) {
                        int idx = rowIdx + startX + x;
                        int c1 = p1[idx];
                        int c2 = p2[idx];
                        double l1 = 0.299 * ((c1 >> 16) & 0xFF) + 0.587 * ((c1 >> 8) & 0xFF) + 0.114 * (c1 & 0xFF);
                        double l2 = 0.299 * ((c2 >> 16) & 0xFF) + 0.587 * ((c2 >> 8) & 0xFF) + 0.114 * (c2 & 0xFF);

                        double dx = l1 - muX;
                        double dy = l2 - muY;
                        varX += dx * dx;
                        varY += dy * dy;
                        covXY += dx * dy;
                    }
                }

                varX /= (N - 1);
                varY /= (N - 1);
                covXY /= (N - 1);

                double ssimBlock = ((2 * muX * muY + C1) * (2 * covXY + C2)) /
                        ((muX * muX + muY * muY + C1) * (varX + varY + C2));

                totalSSIM += ssimBlock;
                blockCount++;
            }
        }

        return (blockCount > 0) ? (totalSSIM / blockCount) : 1.0;
    }
}
