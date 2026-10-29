package com.kiminonawa.mydiary.shared.gui;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.RelativeLayout;

/**
 * A layout with a fixed shape: the height is always the width divided by COVER_RATIO (3:4, taller than wide).
 * It is used for the cover of an entry: a photo cropped with the same shape fills it exactly.
 * <p>
 * It can also be folded (see {@link #setCollapse(float)}), so the cover is out of the way while the person types.
 */
public class AspectRatioRelativeLayout extends RelativeLayout {

    //width : height of the cover of an entry (3:4 = a portrait cover)
    public static final int COVER_RATIO_W = 3;
    public static final int COVER_RATIO_H = 4;
    public static final float COVER_RATIO = (float) COVER_RATIO_W / COVER_RATIO_H;

    //0 = the whole cover is seen, 1 = folded (no height)
    private float collapse = 0f;

    public AspectRatioRelativeLayout(Context context) {
        super(context);
    }

    public AspectRatioRelativeLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public AspectRatioRelativeLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        if (width <= 0 || MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.UNSPECIFIED) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            return;
        }
        int height = Math.round(width / COVER_RATIO * (1f - collapse));
        super.onMeasure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
    }

    /**
     * @param collapse 0 = the whole cover, 1 = no height at all (the cover is folded away)
     */
    public void setCollapse(float collapse) {
        float value = Math.max(0f, Math.min(1f, collapse));
        if (value != this.collapse) {
            this.collapse = value;
            requestLayout();
        }
    }

    public float getCollapse() {
        return collapse;
    }
}
