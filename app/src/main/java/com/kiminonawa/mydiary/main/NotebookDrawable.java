package com.kiminonawa.mydiary.main;

import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;

/**
 * The cover of a notebook: its color with rounded corners and a darker spine on the left.
 */
public class NotebookDrawable extends LayerDrawable {
    private final int spineWidth;

    private NotebookDrawable(Drawable cover, Drawable spine, int spineWidth) {
        super(new Drawable[]{cover, spine});
        this.spineWidth = spineWidth;
    }

    public static NotebookDrawable of(int color, float density, float scale) {
        GradientDrawable cover = new GradientDrawable();
        cover.setColor(color);
        float small = 6 * density * scale, big = 12 * density * scale;
        cover.setCornerRadii(new float[]{small, small, big, big, big, big, small, small});
        GradientDrawable spine = new GradientDrawable();
        spine.setColor(0x38000000);
        spine.setCornerRadii(new float[]{small, small, 0, 0, 0, 0, small, small});
        return new NotebookDrawable(cover, spine, (int) (10 * density * scale));
    }

    @Override
    protected void onBoundsChange(Rect bounds) {
        super.onBoundsChange(bounds);
        getDrawable(1).setBounds(bounds.left, bounds.top, bounds.left + spineWidth, bounds.bottom);
    }
}
