package com.kiminonawa.mydiary.shared.gui;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.RelativeLayout;

/**
 * A layout with a fixed shape: the height is always the width divided by COVER_RATIO (4:5, taller than wide).
 * It is used for the cover of an entry: a photo cropped with the same shape fills it exactly.
 */
public class AspectRatioRelativeLayout extends RelativeLayout {

    //width : height of the cover of an entry (4:5 = a portrait cover)
    public static final int COVER_RATIO_W = 4;
    public static final int COVER_RATIO_H = 5;
    public static final float COVER_RATIO = (float) COVER_RATIO_W / COVER_RATIO_H;

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
        int height = Math.round(width / COVER_RATIO);
        super.onMeasure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
    }
}
