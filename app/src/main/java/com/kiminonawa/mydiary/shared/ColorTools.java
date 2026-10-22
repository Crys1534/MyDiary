package com.kiminonawa.mydiary.shared;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.os.Build;
import androidx.annotation.ColorRes;
import androidx.core.graphics.ColorUtils;

/**
 * Created by daxia on 2016/10/24.
 */

public class ColorTools {

    public static int getColor(Context context, @ColorRes int color) {

        int returnColor;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            returnColor = context.getResources().getColor(color, null);
        } else {
            returnColor = context.getResources().getColor(color);

        }
        return returnColor;
    }

    public static ColorStateList getColorStateList(Context context, @ColorRes int resId) {

        ColorStateList colorStateList;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            colorStateList = context.getResources().getColorStateList(resId, null);
        } else {
            colorStateList = context.getResources().getColorStateList(resId);

        }
        return colorStateList;
    }

    /**
     * @return true when the screens are drawn in dark mode right now
     */
    public static boolean isNight(Context context) {
        int mode = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return mode == Configuration.UI_MODE_NIGHT_YES;
    }

    /**
     * A color that was chosen for a white page (for example black for the name of a diary) can't be seen on a dark
     * page. In dark mode the dark colors are made lighter; in light mode nothing changes.
     */
    public static int readableOnSurface(Context context, int color) {
        if (!isNight(context)) {
            return color;
        }
        if (ColorUtils.calculateLuminance(color) >= 0.45) {
            return color;
        }
        //The darker it is, the more it is moved to white (black becomes a soft light gray)
        float amount = 0.80f - (float) ColorUtils.calculateLuminance(color);
        return ColorUtils.blendARGB(color, 0xFFECECEF, Math.min(0.88f, Math.max(0.45f, amount)));
    }
}
