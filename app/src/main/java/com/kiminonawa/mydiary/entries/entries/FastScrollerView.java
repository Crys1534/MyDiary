package com.kiminonawa.mydiary.entries.entries;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Handler;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

/**
 * A scroll bar like the one of Google Photos for the list of entries: a handle on the right side that
 * can be dragged to jump far through the list, with a small label that says the month and the year
 * of the place where the handle is.
 * <p>
 * It is drawn over the list. It only takes the touches that start on the handle.
 */
public class FastScrollerView extends View {

    public interface Callback {
        int getItemCount();

        /**
         * Text of the label for the item, like "October 2026".
         */
        String getLabel(int index);
    }

    private static final long HIDE_DELAY_MS = 1400;

    private final Paint handlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bubblePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Handler handler = new Handler();

    private final float handleWidth, handleHeight, touchZone, margin;
    private final float bubbleHeight, bubblePadding;

    private RecyclerView recyclerView;
    private Callback callback;
    private boolean dragging = false;
    private boolean shown = false;
    private float handleTop = 0;
    private String label = "";

    private final Runnable hideRunnable = new Runnable() {
        @Override
        public void run() {
            if (!dragging) {
                shown = false;
                invalidate();
            }
        }
    };

    public FastScrollerView(Context context) {
        this(context, null);
    }

    public FastScrollerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        float density = context.getResources().getDisplayMetrics().density;
        handleWidth = 8 * density;
        handleHeight = 56 * density;
        touchZone = 44 * density;
        margin = 4 * density;
        bubbleHeight = 36 * density;
        bubblePadding = 14 * density;
        handlePaint.setColor(0xFF888888);
        bubblePaint.setColor(0xE6333333);
        textPaint.setColor(0xFFFFFFFF);
        textPaint.setFakeBoldText(true);
        textPaint.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 15,
                context.getResources().getDisplayMetrics()));
    }

    public void setColor(int color) {
        handlePaint.setColor(color);
        invalidate();
    }

    public void attach(RecyclerView recyclerView, Callback callback) {
        this.recyclerView = recyclerView;
        this.callback = callback;
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView rv, int dx, int dy) {
                if (!dragging && dy != 0) {
                    followList();
                }
            }
        });
    }

    /**
     * Moves the handle to the place of the list that is on the screen, and shows it for a moment.
     */
    private void followList() {
        if (recyclerView == null || callback == null || !(recyclerView.getLayoutManager() instanceof LinearLayoutManager)) {
            return;
        }
        int count = callback.getItemCount();
        LinearLayoutManager lm = (LinearLayoutManager) recyclerView.getLayoutManager();
        int first = lm.findFirstVisibleItemPosition();
        int last = lm.findLastVisibleItemPosition();
        //A short list that fits in the screen doesn't need the handle
        if (count == 0 || first < 0 || (first == 0 && last >= count - 1)) {
            shown = false;
            invalidate();
            return;
        }
        float available = Math.max(1, getHeight() - handleHeight);
        float fraction;
        if (last >= count - 1) {
            fraction = 1f;
        } else {
            fraction = Math.min(1f, first / (float) Math.max(1, count - (last - first + 1)));
        }
        handleTop = fraction * available;
        label = callback.getLabel(Math.max(0, Math.min(count - 1, first)));
        show();
    }

    private void show() {
        shown = true;
        invalidate();
        handler.removeCallbacks(hideRunnable);
        handler.postDelayed(hideRunnable, HIDE_DELAY_MS);
    }

    @Override
    protected void onDetachedFromWindow() {
        handler.removeCallbacksAndMessages(null);
        super.onDetachedFromWindow();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (callback == null || recyclerView == null) {
            return false;
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                //Only the handle takes the touch; everything else goes to the list
                if (shown && event.getX() >= getWidth() - touchZone
                        && event.getY() >= handleTop - margin * 3
                        && event.getY() <= handleTop + handleHeight + margin * 3) {
                    dragging = true;
                    handler.removeCallbacks(hideRunnable);
                    if (getParent() != null) {
                        getParent().requestDisallowInterceptTouchEvent(true);
                    }
                    dragTo(event.getY());
                    return true;
                }
                return false;
            case MotionEvent.ACTION_MOVE:
                if (dragging) {
                    dragTo(event.getY());
                    return true;
                }
                return false;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (dragging) {
                    dragging = false;
                    show();
                    return true;
                }
                return false;
        }
        return false;
    }

    private void dragTo(float y) {
        int count = callback.getItemCount();
        if (count == 0) {
            return;
        }
        float available = Math.max(1, getHeight() - handleHeight);
        float top = Math.max(0, Math.min(available, y - handleHeight / 2));
        handleTop = top;
        int index = Math.round(top / available * (count - 1));
        index = Math.max(0, Math.min(count - 1, index));
        label = callback.getLabel(index);
        if (recyclerView.getLayoutManager() instanceof LinearLayoutManager) {
            ((LinearLayoutManager) recyclerView.getLayoutManager()).scrollToPositionWithOffset(index, 0);
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!shown) {
            return;
        }
        float right = getWidth() - margin;
        rect.set(right - handleWidth, handleTop, right, handleTop + handleHeight);
        canvas.drawRoundRect(rect, handleWidth / 2, handleWidth / 2, handlePaint);

        if (label != null && label.length() > 0) {
            float textWidth = textPaint.measureText(label);
            float bubbleWidth = textWidth + 2 * bubblePadding;
            float bubbleRight = rect.left - 2 * margin;
            float bubbleTop = handleTop + (handleHeight - bubbleHeight) / 2;
            rect.set(bubbleRight - bubbleWidth, bubbleTop, bubbleRight, bubbleTop + bubbleHeight);
            canvas.drawRoundRect(rect, bubbleHeight / 2, bubbleHeight / 2, bubblePaint);
            Paint.FontMetrics fm = textPaint.getFontMetrics();
            canvas.drawText(label, rect.left + bubblePadding,
                    rect.centerY() - (fm.ascent + fm.descent) / 2, textPaint);
        }
    }
}
