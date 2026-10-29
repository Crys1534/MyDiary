package com.kiminonawa.mydiary.shared.gui;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

/**
 * Puts its children in rows, from left to right, and starts a new row when the next one does not fit
 * (like words in a paragraph). The last child can be stretched to fill what is left of its row.
 */
public class FlowLayout extends ViewGroup {

    private int horizontalSpacing;
    private int verticalSpacing;
    private boolean stretchLast = false;

    public FlowLayout(Context context) {
        super(context);
        float density = context.getResources().getDisplayMetrics().density;
        horizontalSpacing = Math.round(6 * density);
        verticalSpacing = Math.round(6 * density);
    }

    public void setSpacingDp(int horizontalDp, int verticalDp) {
        float density = getResources().getDisplayMetrics().density;
        horizontalSpacing = Math.round(horizontalDp * density);
        verticalSpacing = Math.round(verticalDp * density);
        requestLayout();
    }

    /**
     * The last child (a text box, for example) fills the rest of its row.
     */
    public void setStretchLast(boolean stretchLast) {
        this.stretchLast = stretchLast;
        requestLayout();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int maxWidth = MeasureSpec.getSize(widthMeasureSpec) - getPaddingLeft() - getPaddingRight();
        int x = 0;
        int y = 0;
        int rowHeight = 0;
        int usedWidth = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == GONE) {
                continue;
            }
            measureChild(child, MeasureSpec.makeMeasureSpec(Math.max(maxWidth, 0), MeasureSpec.AT_MOST), heightMeasureSpec);
            int width = child.getMeasuredWidth();
            int height = child.getMeasuredHeight();
            if (x > 0 && x + width > maxWidth) {
                x = 0;
                y += rowHeight + verticalSpacing;
                rowHeight = 0;
            }
            x += width + horizontalSpacing;
            rowHeight = Math.max(rowHeight, height);
            usedWidth = Math.max(usedWidth, x - horizontalSpacing);
        }
        int height = y + rowHeight + getPaddingTop() + getPaddingBottom();
        int width = MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.EXACTLY
                ? MeasureSpec.getSize(widthMeasureSpec)
                : usedWidth + getPaddingLeft() + getPaddingRight();
        setMeasuredDimension(width, resolveSize(height, heightMeasureSpec));
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int maxWidth = r - l - getPaddingLeft() - getPaddingRight();
        int x = 0;
        int y = 0;
        int rowHeight = 0;
        View last = null;
        for (int i = getChildCount() - 1; i >= 0; i--) {
            if (getChildAt(i).getVisibility() != GONE) {
                last = getChildAt(i);
                break;
            }
        }
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == GONE) {
                continue;
            }
            int width = child.getMeasuredWidth();
            int height = child.getMeasuredHeight();
            if (x > 0 && x + width > maxWidth) {
                x = 0;
                y += rowHeight + verticalSpacing;
                rowHeight = 0;
            }
            if (stretchLast && child == last) {
                width = Math.max(width, maxWidth - x);
            }
            int left = getPaddingLeft() + x;
            int top = getPaddingTop() + y;
            child.layout(left, top, left + width, top + height);
            x += width + horizontalSpacing;
            rowHeight = Math.max(rowHeight, height);
        }
    }
}
