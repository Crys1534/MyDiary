package com.kiminonawa.mydiary.entries.diary.editor;

import android.content.ClipData;
import android.graphics.Canvas;
import android.graphics.Point;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.DragEvent;
import android.view.View;
import android.view.ViewParent;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import com.kiminonawa.mydiary.entries.diary.item.DiaryItemHelper;
import com.kiminonawa.mydiary.shared.ScreenHelper;
import com.kiminonawa.mydiary.shared.ThemeManager;
import com.kiminonawa.mydiary.shared.gui.DiaryPhotoLayout;

/**
 * Changes the place of a photo of the entry: the person keeps a finger pressed on the photo and drags it to another
 * place of the entry. A colored line shows where it will go, and the page scrolls by itself near the top and the
 * bottom of the screen.
 */
public class PhotoDragController implements View.OnLongClickListener, View.OnDragListener {

    public interface Listener {
        /**
         * The photo was dropped in a new place.
         */
        void onPhotoMoved();
    }

    private final LinearLayout container;
    private final DiaryItemHelper helper;
    private final Listener listener;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private View dragged = null;
    private ScrollView scroll = null;
    private ColorDrawable marker = null;
    private float lastY = 0f;
    private boolean autoScrolling = false;

    private final Runnable autoScroller = new Runnable() {
        @Override
        public void run() {
            if (scroll == null || dragged == null) {
                autoScrolling = false;
                return;
            }
            int edge = ScreenHelper.dpToPixel(scroll.getResources(), 90);
            int step = ScreenHelper.dpToPixel(scroll.getResources(), 9);
            int dy = 0;
            if (lastY < edge) {
                dy = -step;
            } else if (lastY > scroll.getHeight() - edge) {
                dy = step;
            }
            if (dy == 0) {
                autoScrolling = false;
                return;
            }
            scroll.scrollBy(0, dy);
            showMarker(insertIndexFor(lastY));
            handler.postDelayed(this, 16);
        }
    };

    public PhotoDragController(LinearLayout container, DiaryItemHelper helper, Listener listener) {
        this.container = container;
        this.helper = helper;
        this.listener = listener;
    }

    @Override
    public boolean onLongClick(View v) {
        if (!(v instanceof DiaryPhotoLayout) || helper.getItemSize() < 2) {
            return false;
        }
        //The page that scrolls: it listens to the drag (it also has to scroll by itself)
        ViewParent parent = container.getParent();
        while (parent != null && !(parent instanceof ScrollView)) {
            parent = parent.getParent();
        }
        scroll = parent == null ? null : (ScrollView) parent;
        if (scroll == null) {
            return false;
        }
        scroll.setOnDragListener(this);
        ClipData clip = ClipData.newPlainText("photo", "photo");
        View.DragShadowBuilder shadow = new SmallShadow(v, 0.5f);
        if (Build.VERSION.SDK_INT >= 24) {
            return v.startDragAndDrop(clip, shadow, v, 0);
        }
        //noinspection deprecation
        return v.startDrag(clip, shadow, v, 0);
    }

    @Override
    public boolean onDrag(View view, DragEvent event) {
        Object state = event.getLocalState();
        if (!(state instanceof DiaryPhotoLayout)) {
            return false;
        }
        switch (event.getAction()) {
            case DragEvent.ACTION_DRAG_STARTED:
                dragged = (View) state;
                dragged.setAlpha(0.3f);
                return true;
            case DragEvent.ACTION_DRAG_LOCATION:
                lastY = event.getY();
                showMarker(insertIndexFor(lastY));
                if (!autoScrolling) {
                    autoScrolling = true;
                    handler.post(autoScroller);
                }
                return true;
            case DragEvent.ACTION_DROP:
                lastY = event.getY();
                int from = container.indexOfChild(dragged);
                int to = insertIndexFor(lastY);
                boolean moved = from >= 0 && helper.movePhotoRow(from, to);
                if (moved) {
                    listener.onPhotoMoved();
                }
                return true;
            case DragEvent.ACTION_DRAG_ENDED:
                handler.removeCallbacks(autoScroller);
                autoScrolling = false;
                hideMarker();
                if (dragged != null) {
                    dragged.setAlpha(1f);
                }
                dragged = null;
                if (scroll != null) {
                    scroll.setOnDragListener(null);
                }
                return true;
            default:
                //Entered / exited the page
                return true;
        }
    }

    /**
     * Where the photo would go if it was dropped at this height of the page: before which row (the number of rows when
     * it goes to the end).
     *
     * @param yInScroll height in the page that scrolls (from its top edge, as the drag events give it)
     */
    private int insertIndexFor(float yInScroll) {
        int[] scrollLocation = new int[2];
        scroll.getLocationInWindow(scrollLocation);
        float yWindow = scrollLocation[1] + yInScroll;
        int[] childLocation = new int[2];
        for (int i = 0; i < container.getChildCount(); i++) {
            View child = container.getChildAt(i);
            if (child.getVisibility() == View.GONE) {
                continue;
            }
            child.getLocationInWindow(childLocation);
            if (yWindow < childLocation[1] + child.getHeight() / 2f) {
                return i;
            }
        }
        return container.getChildCount();
    }

    private void showMarker(int index) {
        hideMarker();
        if (container.getChildCount() == 0) {
            return;
        }
        int y;
        if (index >= container.getChildCount()) {
            y = container.getChildAt(container.getChildCount() - 1).getBottom();
        } else {
            y = container.getChildAt(index).getTop();
        }
        int half = ScreenHelper.dpToPixel(container.getResources(), 2);
        marker = new ColorDrawable(ThemeManager.getInstance().getThemeMainColor(container.getContext()));
        marker.setBounds(0, y - half, container.getWidth(), y + half);
        container.getOverlay().add(marker);
    }

    private void hideMarker() {
        if (marker != null) {
            container.getOverlay().remove(marker);
            marker = null;
        }
    }

    /**
     * The picture that follows the finger: the photo, smaller.
     */
    private static class SmallShadow extends View.DragShadowBuilder {
        private final float scale;

        SmallShadow(View view, float scale) {
            super(view);
            this.scale = scale;
        }

        @Override
        public void onProvideShadowMetrics(Point outShadowSize, Point outShadowTouchPoint) {
            View view = getView();
            int width = Math.max(1, Math.round(view.getWidth() * scale));
            int height = Math.max(1, Math.round(view.getHeight() * scale));
            outShadowSize.set(width, height);
            outShadowTouchPoint.set(width / 2, height / 2);
        }

        @Override
        public void onDrawShadow(Canvas canvas) {
            canvas.scale(scale, scale);
            getView().draw(canvas);
        }
    }
}
