package org.floss.enhancetool.model;

/**
 * Classical Bilateral Filter baseline.
 * Performs edge-preserving spatial and photometric range filtering to compare against CNN architectures.
 */
public class BilateralBaseline implements EnhancementModel {

    private final float sigmaSpace = 2.0f;
    private final float sigmaRange = 30.0f;

    @Override
    public String getName() {
        return "Bilateral Filter (Classical Baseline)";
    }

    @Override
    public String getDescription() {
        return "Classical edge-preserving photometric smoothing baseline";
    }

    @Override
    public int getScaleFactor() {
        return 1;
    }

    @Override
    public boolean isSfMRecommended() {
        return true;
    }

    @Override
    public int[] processTile(int[] inPixels, int inWidth, int inHeight) {
        int[] out = new int[inWidth * inHeight];
        int radius = (int) Math.ceil(2 * sigmaSpace);
        float twoSigmaSpaceSq = 2.0f * sigmaSpace * sigmaSpace;
        float twoSigmaRangeSq = 2.0f * sigmaRange * sigmaRange;

        for (int y = 0; y < inHeight; y++) {
            for (int x = 0; x < inWidth; x++) {
                int centerPixel = inPixels[y * inWidth + x];
                int cR = (centerPixel >> 16) & 0xFF;
                int cG = (centerPixel >> 8) & 0xFF;
                int cB = centerPixel & 0xFF;

                float sumR = 0f, sumG = 0f, sumB = 0f;
                float totalWeight = 0f;

                for (int dy = -radius; dy <= radius; dy++) {
                    int py = y + dy;
                    if (py < 0 || py >= inHeight) continue;
                    int row = py * inWidth;

                    for (int dx = -radius; dx <= radius; dx++) {
                        int px = x + dx;
                        if (px < 0 || px >= inWidth) continue;

                        int neighborPixel = inPixels[row + px];
                        int nR = (neighborPixel >> 16) & 0xFF;
                        int nG = (neighborPixel >> 8) & 0xFF;
                        int nB = neighborPixel & 0xFF;

                        float spatialDistSq = dx * dx + dy * dy;
                        float colorDistSq = (cR - nR) * (cR - nR) +
                                            (cG - nG) * (cG - nG) +
                                            (cB - nB) * (cB - nB);

                        float weight = (float) Math.exp(-(spatialDistSq / twoSigmaSpaceSq) -
                                                        (colorDistSq / twoSigmaRangeSq));

                        sumR += nR * weight;
                        sumG += nG * weight;
                        sumB += nB * weight;
                        totalWeight += weight;
                    }
                }

                int r = Math.min(255, Math.max(0, (int) (sumR / totalWeight)));
                int g = Math.min(255, Math.max(0, (int) (sumG / totalWeight)));
                int b = Math.min(255, Math.max(0, (int) (sumB / totalWeight)));

                out[y * inWidth + x] = 0xFF000000 | (r << 16) | (g << 8) | b;
            }
        }

        return out;
    }
}
