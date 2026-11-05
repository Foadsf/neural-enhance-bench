package org.floss.enhancetool.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import java.util.List;

/**
 * Interactive split-screen comparison View with draggable slider, pinch-zoom, pan,
 * and Structure-from-Motion (SfM) keypoint overlay support.
 */
public class SplitComparisonView extends View {

    private Bitmap originalBitmap;
    private Bitmap enhancedBitmap;

    private List<Point> originalKeypoints;
    private List<Point> enhancedKeypoints;
    private boolean showKeypoints = false;

    private float splitPosition = 0.5f; // [0.0, 1.0]
    private boolean isDraggingSplit = false;

    // Zoom & pan state
    private float scaleFactor = 1.0f;
    private float translateX = 0.0f;
    private float translateY = 0.0f;
    private float lastTouchX;
    private float lastTouchY;
    private int activePointerId = MotionEvent.INVALID_POINTER_ID;

    private ScaleGestureDetector scaleDetector;

    // Paints
    private final Paint dividerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint thumbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint kpRefPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint kpEnhPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint kpCenterPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint emptyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public SplitComparisonView(Context context) {
        super(context);
        init(context);
    }

    public SplitComparisonView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public SplitComparisonView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        dividerPaint.setColor(0xFF00E5FF);
        dividerPaint.setStrokeWidth(4f);
        dividerPaint.setStyle(Paint.Style.STROKE);

        thumbPaint.setColor(0xFF00E5FF);
        thumbPaint.setStyle(Paint.Style.FILL);

        textBgPaint.setColor(0xCC1A2130);
        textBgPaint.setStyle(Paint.Style.FILL);

        textPaint.setColor(0xFFFFFFFF);
        textPaint.setTextSize(32f);
        textPaint.setFakeBoldText(true);
        textPaint.setTextAlign(Paint.Align.CENTER);

        kpRefPaint.setColor(0xFFFF5252); // Red/coral for original
        kpRefPaint.setStyle(Paint.Style.STROKE);
        kpRefPaint.setStrokeWidth(3f);

        kpEnhPaint.setColor(0xFF00E676); // Green for enhanced
        kpEnhPaint.setStyle(Paint.Style.STROKE);
        kpEnhPaint.setStrokeWidth(3f);

        kpCenterPaint.setColor(Color.WHITE);
        kpCenterPaint.setStyle(Paint.Style.FILL);

        emptyPaint.setColor(0xFF888888);
        emptyPaint.setTextSize(40f);
        emptyPaint.setTextAlign(Paint.Align.CENTER);

        scaleDetector = new ScaleGestureDetector(context, new ScaleListener());
    }

    public void setBitmaps(Bitmap original, Bitmap enhanced) {
        this.originalBitmap = original;
        this.enhancedBitmap = enhanced;
        resetZoomAndPan();
        invalidate();
    }

    public void setKeypoints(List<Point> origPts, List<Point> enhPts) {
        this.originalKeypoints = origPts;
        this.enhancedKeypoints = enhPts;
        invalidate();
    }

    public void setShowKeypoints(boolean show) {
        this.showKeypoints = show;
        invalidate();
    }

    public boolean isShowingKeypoints() {
        return showKeypoints;
    }

    public void setSplitPosition(float pos) {
        this.splitPosition = Math.max(0.02f, Math.min(0.98f, pos));
        invalidate();
    }

    public void resetZoomAndPan() {
        this.scaleFactor = 1.0f;
        this.translateX = 0.0f;
        this.translateY = 0.0f;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int viewW = getWidth();
        int viewH = getHeight();

        if (originalBitmap == null && enhancedBitmap == null) {
            canvas.drawText("No Image Loaded. Pick an image or generate a test target.",
                    viewW / 2f, viewH / 2f, emptyPaint);
            return;
        }

        Bitmap leftBmp = (originalBitmap != null) ? originalBitmap : enhancedBitmap;
        Bitmap rightBmp = (enhancedBitmap != null) ? enhancedBitmap : originalBitmap;

        float splitX = viewW * splitPosition;

        canvas.save();
        // Apply zoom and pan around center
        canvas.translate(translateX, translateY);
        canvas.scale(scaleFactor, scaleFactor, viewW / 2f, viewH / 2f);

        // Aspect fit bounding box
        float bmpW = leftBmp.getWidth();
        float bmpH = leftBmp.getHeight();
        float fitScale = Math.min((float) viewW / bmpW, (float) viewH / bmpH);
        float destW = bmpW * fitScale;
        float destH = bmpH * fitScale;
        float destLeft = (viewW - destW) / 2f;
        float destTop = (viewH - destH) / 2f;
        RectF destRect = new RectF(destLeft, destTop, destLeft + destW, destTop + destH);

        // Draw Left (Original) clipped to [0, splitX]
        canvas.save();
        canvas.clipRect(0, 0, splitX, viewH);
        canvas.drawBitmap(leftBmp, null, destRect, null);

        // Keypoints on Left
        if (showKeypoints && originalKeypoints != null) {
            float sx = destW / leftBmp.getWidth();
            float sy = destH / leftBmp.getHeight();
            for (Point p : originalKeypoints) {
                float kx = destLeft + p.x * sx;
                float ky = destTop + p.y * sy;
                canvas.drawCircle(kx, ky, 6f, kpRefPaint);
                canvas.drawCircle(kx, ky, 2f, kpCenterPaint);
            }
        }
        canvas.restore();

        // Draw Right (Enhanced) clipped to [splitX, viewW]
        canvas.save();
        canvas.clipRect(splitX, 0, viewW, viewH);
        canvas.drawBitmap(rightBmp, null, destRect, null);

        // Keypoints on Right
        if (showKeypoints && enhancedKeypoints != null) {
            float sx = destW / rightBmp.getWidth();
            float sy = destH / rightBmp.getHeight();
            for (Point p : enhancedKeypoints) {
                float kx = destLeft + p.x * sx;
                float ky = destTop + p.y * sy;
                canvas.drawCircle(kx, ky, 6f, kpEnhPaint);
                canvas.drawCircle(kx, ky, 2f, kpCenterPaint);
            }
        }
        canvas.restore();

        canvas.restore(); // Restore zoom/pan

        // Draw Split Divider and Handle in screen space
        canvas.drawLine(splitX, 0, splitX, viewH, dividerPaint);

        // Thumb handle circle
        float thumbY = viewH / 2f;
        canvas.drawCircle(splitX, thumbY, 26f, thumbPaint);
        Paint innerThumbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        innerThumbPaint.setColor(0xFF0F172A);
        canvas.drawCircle(splitX, thumbY, 18f, innerThumbPaint);

        // Double arrow icon (< | >)
        Paint arrowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        arrowPaint.setColor(0xFF00E5FF);
        arrowPaint.setStrokeWidth(3f);
        canvas.drawLine(splitX - 6, thumbY - 6, splitX - 10, thumbY, arrowPaint);
        canvas.drawLine(splitX - 10, thumbY, splitX - 6, thumbY + 6, arrowPaint);
        canvas.drawLine(splitX + 6, thumbY - 6, splitX + 10, thumbY, arrowPaint);
        canvas.drawLine(splitX + 10, thumbY, splitX + 6, thumbY + 6, arrowPaint);

        // Labels "BEFORE" / "AFTER"
        RectF leftLabel = new RectF(16, 16, 180, 72);
        canvas.drawRoundRect(leftLabel, 12, 12, textBgPaint);
        canvas.drawText("ORIGINAL", 98, 55, textPaint);

        RectF rightLabel = new RectF(viewW - 196, 16, viewW - 16, 72);
        canvas.drawRoundRect(rightLabel, 12, 12, textBgPaint);
        canvas.drawText("ENHANCED", viewW - 106, 55, textPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        scaleDetector.onTouchEvent(event);

        final int action = event.getActionMasked();

        switch (action) {
            case MotionEvent.ACTION_DOWN: {
                final float x = event.getX();
                final float y = event.getY();
                lastTouchX = x;
                lastTouchY = y;
                activePointerId = event.getPointerId(0);

                float splitX = getWidth() * splitPosition;
                // If touched near divider or thumb, initiate split drag
                if (Math.abs(x - splitX) < 80f) {
                    isDraggingSplit = true;
                    getParent().requestDisallowInterceptTouchEvent(true);
                } else {
                    isDraggingSplit = false;
                }
                break;
            }

            case MotionEvent.ACTION_MOVE: {
                final int pointerIndex = event.findPointerIndex(activePointerId);
                if (pointerIndex == -1) break;

                final float x = event.getX(pointerIndex);
                final float y = event.getY(pointerIndex);

                if (isDraggingSplit) {
                    setSplitPosition(x / (float) getWidth());
                } else if (!scaleDetector.isInProgress() && scaleFactor > 1.05f) {
                    // Pan image when zoomed in
                    final float dx = x - lastTouchX;
                    final float dy = y - lastTouchY;
                    translateX += dx;
                    translateY += dy;
                    invalidate();
                }

                lastTouchX = x;
                lastTouchY = y;
                break;
            }

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                isDraggingSplit = false;
                activePointerId = MotionEvent.INVALID_POINTER_ID;
                break;
            }

            case MotionEvent.ACTION_POINTER_UP: {
                final int pointerIndex = event.getActionIndex();
                final int pointerId = event.getPointerId(pointerIndex);
                if (pointerId == activePointerId) {
                    final int newPointerIndex = (pointerIndex == 0) ? 1 : 0;
                    lastTouchX = event.getX(newPointerIndex);
                    lastTouchY = event.getY(newPointerIndex);
                    activePointerId = event.getPointerId(newPointerIndex);
                }
                break;
            }
        }
        return true;
    }

    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            scaleFactor *= detector.getScaleFactor();
            // Restrict zoom limits
            scaleFactor = Math.max(1.0f, Math.min(scaleFactor, 8.0f));
            if (scaleFactor == 1.0f) {
                translateX = 0;
                translateY = 0;
            }
            invalidate();
            return true;
        }
    }
}
