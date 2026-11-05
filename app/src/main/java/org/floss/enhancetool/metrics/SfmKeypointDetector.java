package org.floss.enhancetool.metrics;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Photogrammetry / Structure-from-Motion (SfM) Keypoint Detector.
 * Implements Harris Corner Detection and spatial retention scoring
 * to verify geometric fidelity and feature repeatability across enhancement.
 */
public final class SfmKeypointDetector {

    public static class KeypointCandidate {
        public final int x;
        public final int y;
        public final float response;

        public KeypointCandidate(int x, int y, float response) {
            this.x = x;
            this.y = y;
            this.response = response;
        }
    }

    /**
     * Detects corner keypoints using Harris corner operator with non-maximum suppression.
     * @param bitmap Input bitmap
     * @param maxPoints Maximum keypoints to return (highest response first)
     * @return List of detected (x, y) coordinates
     */
    public static List<Point> detectKeypoints(Bitmap bitmap, int maxPoints) {
        if (bitmap == null) return new ArrayList<>();

        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        int total = w * h;

        int[] pixels = new int[total];
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h);

        // Luminance map
        float[] lum = new float[total];
        for (int i = 0; i < total; i++) {
            int c = pixels[i];
            int r = (c >> 16) & 0xFF;
            int g = (c >> 8) & 0xFF;
            int b = c & 0xFF;
            lum[i] = 0.299f * r + 0.587f * g + 0.114f * b;
        }

        // 3x3 Sobel gradients
        float[] ix = new float[total];
        float[] iy = new float[total];
        for (int y = 1; y < h - 1; y++) {
            int rowPrev = (y - 1) * w;
            int rowCurr = y * w;
            int rowNext = (y + 1) * w;

            for (int x = 1; x < w - 1; x++) {
                // Sobel X:
                // [-1 0 +1]
                // [-2 0 +2]
                // [-1 0 +1]
                float gx = (lum[rowPrev + x + 1] - lum[rowPrev + x - 1]) +
                           2f * (lum[rowCurr + x + 1] - lum[rowCurr + x - 1]) +
                           (lum[rowNext + x + 1] - lum[rowNext + x - 1]);

                // Sobel Y:
                // [-1 -2 -1]
                // [ 0  0  0]
                // [+1 +2 +1]
                float gy = (lum[rowNext + x - 1] - lum[rowPrev + x - 1]) +
                           2f * (lum[rowNext + x] - lum[rowPrev + x]) +
                           (lum[rowNext + x + 1] - lum[rowPrev + x + 1]);

                ix[rowCurr + x] = gx / 8f;
                iy[rowCurr + x] = gy / 8f;
            }
        }

        // Second moment matrix components smoothed with 3x3 box filter
        float[] ix2 = new float[total];
        float[] iy2 = new float[total];
        float[] ixy = new float[total];
        for (int i = 0; i < total; i++) {
            ix2[i] = ix[i] * ix[i];
            iy2[i] = iy[i] * iy[i];
            ixy[i] = ix[i] * iy[i];
        }

        float[] rResponse = new float[total];
        final float k = 0.04f;
        float maxR = 0f;

        for (int y = 2; y < h - 2; y++) {
            for (int x = 2; x < w - 2; x++) {
                float a = 0, b = 0, c = 0;
                for (int dy = -1; dy <= 1; dy++) {
                    int offset = (y + dy) * w + x;
                    a += ix2[offset - 1] + ix2[offset] + ix2[offset + 1];
                    b += iy2[offset - 1] + iy2[offset] + iy2[offset + 1];
                    c += ixy[offset - 1] + ixy[offset] + ixy[offset + 1];
                }
                // Harris score: det(M) - k * trace(M)^2
                float det = (a * b) - (c * c);
                float trace = a + b;
                float r = det - (k * trace * trace);
                if (r > 0) {
                    rResponse[y * w + x] = r;
                    if (r > maxR) maxR = r;
                }
            }
        }

        // Non-maximum suppression with 5x5 window and dynamic threshold
        float threshold = maxR * 0.015f;
        List<KeypointCandidate> candidates = new ArrayList<>();

        for (int y = 3; y < h - 3; y++) {
            int rowIdx = y * w;
            for (int x = 3; x < w - 3; x++) {
                float val = rResponse[rowIdx + x];
                if (val < threshold) continue;

                boolean isMax = true;
                suppressLoop:
                for (int dy = -2; dy <= 2; dy++) {
                    int neighborRow = (y + dy) * w;
                    for (int dx = -2; dx <= 2; dx++) {
                        if (dx == 0 && dy == 0) continue;
                        if (rResponse[neighborRow + x + dx] > val) {
                            isMax = false;
                            break suppressLoop;
                        }
                    }
                }

                if (isMax) {
                    candidates.add(new KeypointCandidate(x, y, val));
                }
            }
        }

        // Sort descending by response
        Collections.sort(candidates, new Comparator<KeypointCandidate>() {
            @Override
            public int compare(KeypointCandidate o1, KeypointCandidate o2) {
                return Float.compare(o2.response, o1.response);
            }
        });

        int limit = Math.min(maxPoints, candidates.size());
        List<Point> result = new ArrayList<>(limit);
        for (int i = 0; i < limit; i++) {
            KeypointCandidate c = candidates.get(i);
            result.add(new Point(c.x, c.y));
        }

        return result;
    }

    /**
     * Compute spatial retention percentage:
     * Fraction of reference keypoints that have a counterpart in the target keypoint set
     * within toleranceRadius pixels (normalized by scale if dimensions differ).
     */
    public static float computeRetentionRate(List<Point> refPts, List<Point> targetPts,
                                             float scaleX, float scaleY, float toleranceRadius) {
        if (refPts == null || refPts.isEmpty()) return 1.0f;
        if (targetPts == null || targetPts.isEmpty()) return 0.0f;

        float tolSq = toleranceRadius * toleranceRadius;
        int matchedCount = 0;

        for (Point pRef : refPts) {
            float mappedX = pRef.x * scaleX;
            float mappedY = pRef.y * scaleY;

            boolean matched = false;
            for (Point pTgt : targetPts) {
                float dx = mappedX - pTgt.x;
                float dy = mappedY - pTgt.y;
                if ((dx * dx + dy * dy) <= tolSq) {
                    matched = true;
                    break;
                }
            }
            if (matched) {
                matchedCount++;
            }
        }

        return (float) matchedCount / (float) refPts.size();
    }

    /**
     * Renders keypoint indicators (circles + center dot) onto a copy of the input bitmap.
     */
    public static Bitmap createOverlayBitmap(Bitmap src, List<Point> points, int color) {
        if (src == null) return null;
        Bitmap out = src.copy(Bitmap.Config.ARGB_8888, true);
        Canvas canvas = new Canvas(out);

        Paint circlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        circlePaint.setStyle(Paint.Style.STROKE);
        circlePaint.setColor(color);
        circlePaint.setStrokeWidth(2.5f);

        Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        dotPaint.setStyle(Paint.Style.FILL);
        dotPaint.setColor(Color.WHITE);

        for (Point pt : points) {
            canvas.drawCircle(pt.x, pt.y, 4f, circlePaint);
            canvas.drawCircle(pt.x, pt.y, 1.2f, dotPaint);
        }

        return out;
    }
}
