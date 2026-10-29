package com.kiminonawa.mydiary.entries.diary.editor;

import android.animation.ValueAnimator;
import android.graphics.Rect;
import android.os.Build;
import android.view.View;
import android.view.WindowInsets;
import android.view.ViewTreeObserver;
import android.view.animation.DecelerateInterpolator;
import android.widget.EditText;

import com.kiminonawa.mydiary.shared.ScreenHelper;
import com.kiminonawa.mydiary.shared.gui.AspectRatioRelativeLayout;

/**
 * Folds the cover away while the person is typing (the keyboard is open and a text box has the cursor), so the text
 * has all the space, and opens it again when the keyboard is closed.
 */
public final class CoverCollapser implements ViewTreeObserver.OnGlobalLayoutListener {

    //A keyboard is always taller than this (dp)
    private static final int MIN_KEYBOARD_DP = 120;
    private static final long ANIMATION_MS = 180;

    private final View rootView;
    private final AspectRatioRelativeLayout cover;
    private boolean keyboardVisible = false;
    private boolean started = false;
    private ValueAnimator animator;
    //Where the cover is going to (or is): 0 = open, 1 = folded
    private float animatorTarget = 0f;

    public CoverCollapser(View rootView, AspectRatioRelativeLayout cover) {
        this.rootView = rootView;
        this.cover = cover;
    }

    public void start() {
        if (!started) {
            started = true;
            rootView.getViewTreeObserver().addOnGlobalLayoutListener(this);
        }
    }

    public void stop() {
        if (started) {
            started = false;
            ViewTreeObserver observer = rootView.getViewTreeObserver();
            if (observer.isAlive()) {
                observer.removeOnGlobalLayoutListener(this);
            }
        }
        if (animator != null) {
            animator.cancel();
        }
        //Never leave the cover folded when the person comes back
        cover.setCollapse(0f);
        animatorTarget = 0f;
        keyboardVisible = false;
    }

    @Override
    public void onGlobalLayout() {
        keyboardVisible = isKeyboardVisible();
        View focus = rootView.findFocus();
        boolean typing = keyboardVisible && focus instanceof EditText;
        animateTo(typing ? 1f : 0f);
    }

    private boolean isKeyboardVisible() {
        if (Build.VERSION.SDK_INT >= 30) {
            //Android 11 and newer tell it directly (it also works inside a window that is not full screen)
            WindowInsets insets = rootView.getRootWindowInsets();
            if (insets != null) {
                return insets.isVisible(WindowInsets.Type.ime());
            }
        }
        View decor = rootView.getRootView();
        Rect visibleFrame = new Rect();
        decor.getWindowVisibleDisplayFrame(visibleFrame);
        int hidden = decor.getHeight() - visibleFrame.bottom;
        return hidden > ScreenHelper.dpToPixel(rootView.getResources(), MIN_KEYBOARD_DP);
    }

    /**
     * Folds (1) or opens (0) the cover with a short animation.
     */
    private void animateTo(float target) {
        if (target == animatorTarget) {
            //Already there, or already going there
            return;
        }
        animatorTarget = target;
        if (animator != null) {
            animator.cancel();
        }
        animator = ValueAnimator.ofFloat(cover.getCollapse(), target);
        animator.setDuration(ANIMATION_MS);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                cover.setCollapse((Float) animation.getAnimatedValue());
            }
        });
        animator.start();
    }
}
