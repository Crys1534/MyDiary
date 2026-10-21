package androidx.core.animation;

import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.view.View;

/**
 * The list library (advrecyclerview 0.10) was made for the old support library and asks for this
 * class, which the new AndroidX does not have any more. Without it the app closed as soon as the
 * list animated an item. Only the function that the library really calls is here.
 */
public final class AnimatorCompatHelper {

    private static TimeInterpolator sDefaultInterpolator;

    private AnimatorCompatHelper() {
    }

    public static void clearInterpolator(View view) {
        if (sDefaultInterpolator == null) {
            sDefaultInterpolator = new ValueAnimator().getInterpolator();
        }
        view.animate().setInterpolator(sDefaultInterpolator);
    }
}
