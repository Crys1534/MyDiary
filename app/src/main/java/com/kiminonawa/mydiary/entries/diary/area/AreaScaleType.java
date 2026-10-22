package com.kiminonawa.mydiary.entries.diary.area;

import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;

import com.facebook.drawee.drawable.ScalingUtils;

/**
 * Shows only a part of a photo (the "visible area" the person chose), filling the whole picture frame.
 * <p>
 * The photo is not cut: Fresco just draws it bigger and moved, and the frame only lets the chosen part be seen.
 * The area is centered in the frame; if the frame has another shape than the area (for example the small square
 * pictures of the list), the extra of the area is left out at the sides.
 */
public class AreaScaleType implements ScalingUtils.ScaleType {

    private final RectF area;

    /**
     * @param area fractions of the photo (0 to 1): left, top, right, bottom
     */
    public AreaScaleType(RectF area) {
        this.area = new RectF(area);
    }

    @Override
    public Matrix getTransform(Matrix outTransform, Rect parentBounds, int childWidth, int childHeight,
                               float focusX, float focusY) {
        if (childWidth <= 0 || childHeight <= 0) {
            outTransform.reset();
            return outTransform;
        }
        float areaWidth = area.width() * childWidth;
        float areaHeight = area.height() * childHeight;
        float scale = Math.max(parentBounds.width() / areaWidth, parentBounds.height() / areaHeight);
        float dx = parentBounds.left + parentBounds.width() * 0.5f - area.centerX() * childWidth * scale;
        float dy = parentBounds.top + parentBounds.height() * 0.5f - area.centerY() * childHeight * scale;
        //The photo always covers the frame (no empty edges)
        dx = Math.min(parentBounds.left, Math.max(parentBounds.right - childWidth * scale, dx));
        dy = Math.min(parentBounds.top, Math.max(parentBounds.bottom - childHeight * scale, dy));
        outTransform.setScale(scale, scale);
        outTransform.postTranslate(dx, dy);
        return outTransform;
    }

    @Override
    public String toString() {
        return "area(" + area.left + "," + area.top + "," + area.right + "," + area.bottom + ")";
    }
}
