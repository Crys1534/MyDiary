package com.kiminonawa.mydiary.entries.diary.area;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

/**
 * The screen where the person chooses what part of a photo is seen.
 * <p>
 * The whole photo is drawn, and over it a frame with the shape that will be used (4:3 or 4:5). The outside of the
 * frame is darkened. One finger moves the photo, two fingers zoom it. The photo always covers the frame.
 * Nothing is cut: {@link #getArea()} gives the part inside the frame in fractions of the photo (0 to 1).
 */
public class AreaPickerView extends View {

    //How much the photo can be zoomed, compared with "just covering the frame"
    private static final float MAX_ZOOM = 4f;

    private Bitmap bitmap;
    //The shape of the frame: width / height
    private float frameRatio = 1f;
    private final RectF frame = new RectF();
    //Where the photo is: scale (photo pixels to screen pixels) and position of its top left corner
    private float scale = 1f;
    private float translateX = 0f;
    private float translateY = 0f;
    private float minScale = 1f;
    private float maxScale = 1f;
    //The area to show when the screen opens (null = center)
    private RectF initialArea = null;

    private final Paint bitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint dimPaint = new Paint();
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint guidePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final ScaleGestureDetector scaleDetector;
    private float lastX;
    private float lastY;

    public AreaPickerView(Context context) {
        this(context, null);
    }

    public AreaPickerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        float density = context.getResources().getDisplayMetrics().density;
        dimPaint.setColor(0xB3000000);
        borderPaint.setColor(0xFFFFFFFF);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(2f * density);
        guidePaint.setColor(0x66FFFFFF);
        guidePaint.setStyle(Paint.Style.STROKE);
        guidePaint.setStrokeWidth(1f * density);
        scaleDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                zoomBy(detector.getScaleFactor(), detector.getFocusX(), detector.getFocusY());
                return true;
            }
        });
    }

    /**
     * @param bitmap      the photo (already turned the right way up)
     * @param frameRatio  width / height of the frame (4/3 for the photos of the entry, 4/5 for the cover)
     * @param initialArea the area chosen before, or null for the center
     */
    public void setPhoto(Bitmap bitmap, float frameRatio, RectF initialArea) {
        this.bitmap = bitmap;
        this.frameRatio = frameRatio;
        this.initialArea = initialArea == null ? null : new RectF(initialArea);
        if (getWidth() > 0) {
            layoutFrame();
            placePhoto(this.initialArea);
        }
        invalidate();
    }

    /**
     * Puts the photo back in the center, as big as needed to cover the frame.
     */
    public void resetToCenter() {
        if (bitmap != null && getWidth() > 0) {
            placePhoto(null);
            invalidate();
        }
    }

    /**
     * The part of the photo that is inside the frame, in fractions of the photo (0 to 1).
     */
    public RectF getArea() {
        if (bitmap == null) {
            return new RectF(0f, 0f, 1f, 1f);
        }
        float width = bitmap.getWidth() * scale;
        float height = bitmap.getHeight() * scale;
        RectF area = new RectF((frame.left - translateX) / width, (frame.top - translateY) / height,
                (frame.right - translateX) / width, (frame.bottom - translateY) / height);
        area.left = clamp(area.left, 0f, 1f);
        area.top = clamp(area.top, 0f, 1f);
        area.right = clamp(area.right, 0f, 1f);
        area.bottom = clamp(area.bottom, 0f, 1f);
        return area;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (bitmap != null && w > 0 && h > 0) {
            //Keep the part that was chosen when the screen changes its size
            RectF keep = oldw > 0 ? getAreaSafe() : initialArea;
            layoutFrame();
            placePhoto(keep);
        }
    }

    private RectF getAreaSafe() {
        return frame.width() > 0 ? getArea() : initialArea;
    }

    private void layoutFrame() {
        float margin = 20f * getResources().getDisplayMetrics().density;
        float availableWidth = getWidth() - 2 * margin;
        float availableHeight = getHeight() - 2 * margin;
        float frameWidth = Math.min(availableWidth, availableHeight * frameRatio);
        float frameHeight = frameWidth / frameRatio;
        float left = (getWidth() - frameWidth) / 2f;
        float top = (getHeight() - frameHeight) / 2f;
        frame.set(left, top, left + frameWidth, top + frameHeight);
    }

    private void placePhoto(RectF area) {
        float bitmapWidth = bitmap.getWidth();
        float bitmapHeight = bitmap.getHeight();
        minScale = Math.max(frame.width() / bitmapWidth, frame.height() / bitmapHeight);
        maxScale = minScale * MAX_ZOOM;
        if (area != null && area.width() > 0f && area.height() > 0f) {
            scale = clamp(Math.max(frame.width() / (area.width() * bitmapWidth),
                    frame.height() / (area.height() * bitmapHeight)), minScale, maxScale);
            translateX = frame.centerX() - area.centerX() * bitmapWidth * scale;
            translateY = frame.centerY() - area.centerY() * bitmapHeight * scale;
        } else {
            scale = minScale;
            translateX = frame.centerX() - bitmapWidth * scale / 2f;
            translateY = frame.centerY() - bitmapHeight * scale / 2f;
        }
        keepPhotoOverFrame();
    }

    private void keepPhotoOverFrame() {
        float width = bitmap.getWidth() * scale;
        float height = bitmap.getHeight() * scale;
        translateX = Math.min(frame.left, Math.max(frame.right - width, translateX));
        translateY = Math.min(frame.top, Math.max(frame.bottom - height, translateY));
    }

    private void zoomBy(float factor, float focusX, float focusY) {
        if (bitmap == null) {
            return;
        }
        float newScale = clamp(scale * factor, minScale, maxScale);
        float realFactor = newScale / scale;
        //The point under the fingers stays under the fingers
        translateX = focusX - (focusX - translateX) * realFactor;
        translateY = focusY - (focusY - translateY) * realFactor;
        scale = newScale;
        keepPhotoOverFrame();
        invalidate();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (bitmap == null) {
            return false;
        }
        scaleDetector.onTouchEvent(event);
        int action = event.getActionMasked();
        //The middle point of the fingers that are down moves the photo
        int skip = action == MotionEvent.ACTION_POINTER_UP ? event.getActionIndex() : -1;
        float sumX = 0f;
        float sumY = 0f;
        int count = 0;
        for (int i = 0; i < event.getPointerCount(); i++) {
            if (i == skip) {
                continue;
            }
            sumX += event.getX(i);
            sumY += event.getY(i);
            count++;
        }
        if (count == 0) {
            return true;
        }
        float x = sumX / count;
        float y = sumY / count;
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                getParent().requestDisallowInterceptTouchEvent(true);
                lastX = x;
                lastY = y;
                break;
            case MotionEvent.ACTION_POINTER_DOWN:
            case MotionEvent.ACTION_POINTER_UP:
                lastX = x;
                lastY = y;
                break;
            case MotionEvent.ACTION_MOVE:
                translateX += x - lastX;
                translateY += y - lastY;
                lastX = x;
                lastY = y;
                keepPhotoOverFrame();
                invalidate();
                break;
            default:
                break;
        }
        return true;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawColor(0xFF111111);
        if (bitmap == null) {
            return;
        }
        canvas.save();
        canvas.translate(translateX, translateY);
        canvas.scale(scale, scale);
        canvas.drawBitmap(bitmap, 0f, 0f, bitmapPaint);
        canvas.restore();
        //Dark outside the frame
        canvas.drawRect(0f, 0f, getWidth(), frame.top, dimPaint);
        canvas.drawRect(0f, frame.bottom, getWidth(), getHeight(), dimPaint);
        canvas.drawRect(0f, frame.top, frame.left, frame.bottom, dimPaint);
        canvas.drawRect(frame.right, frame.top, getWidth(), frame.bottom, dimPaint);
        //The frame and its thirds (to help to compose the picture)
        float thirdX = frame.width() / 3f;
        float thirdY = frame.height() / 3f;
        for (int i = 1; i <= 2; i++) {
            canvas.drawLine(frame.left + thirdX * i, frame.top, frame.left + thirdX * i, frame.bottom, guidePaint);
            canvas.drawLine(frame.left, frame.top + thirdY * i, frame.right, frame.top + thirdY * i, guidePaint);
        }
        canvas.drawRect(frame, borderPaint);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
