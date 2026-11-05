package org.floss.enhancetool.pipeline;

/**
 * Progress and memory telemetry callback during tiled inference.
 */
public interface InferenceCallback {
    void onProgress(int currentTile, int totalTiles, long memoryBytes);
}
