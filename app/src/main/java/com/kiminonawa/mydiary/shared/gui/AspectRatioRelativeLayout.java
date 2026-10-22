package com.kiminonawa.mydiary.shared.gui;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.RelativeLayout;

/**
 * A layout that is always 4:3 (the height is 3/4 of the width), like the photos of the diary.
 * It is used for the cover of an entry: a photo cropped to 4:3 fills it exactly.
 */
public class AspectRatioRelativeLayout extends RelativeLayout {

    //width : height
    public static final float COVER_RATIO = 4f / 3f;

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
