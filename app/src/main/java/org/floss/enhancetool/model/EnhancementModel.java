package org.floss.enhancetool.model;

/**
 * Common FLOSS contract for CNN and VLM image enhancement models.
 * Designed for tiled on-device inference on resource-constrained mobile hardware.
 */
public interface EnhancementModel {
    /**
     * Unique display name of the model.
     */
    String getName();

    /**
     * Model family / architectural description.
     */
    String getDescription();

    /**
     * Scale factor for super-resolution (e.g. 1 for restoration/denoise, 2 or 4 for SR).
     */
    int getScaleFactor();

    /**
     * Whether this model preserves geometric fidelity for Structure-from-Motion (SfM).
     */
    boolean isSfMRecommended();

    /**
     * Executes inference on a single image tile (ARGB_8888 formatted pixels).
     *
     * @param inPixels  Input tile ARGB pixel buffer.
     * @param inWidth   Input tile width in pixels.
     * @param inHeight  Input tile height in pixels.
     * @return Output ARGB pixel buffer with dimensions (inWidth * scale) x (inHeight * scale).
     */
    int[] processTile(int[] inPixels, int inWidth, int inHeight);
}
