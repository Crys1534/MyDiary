package com.kiminonawa.mydiary.shared;

import android.content.Context;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;

/**
 * A very short, soft vibration (for example when an entry is saved). It can be turned off in Settings.
 */
public final class Haptics {

    private Haptics() {
    }

    /**
     * The entry was saved.
     */
    @SuppressWarnings("deprecation")
    public static void saved(Context context) {
        if (context == null || !SPFManager.isVibrationOn(context)) {
            return;
        }
        try {
            Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator == null || !vibrator.hasVibrator()) {
                return;
            }
            if (Build.VERSION.SDK_INT >= 29) {
                //The phone's own soft "click"
                vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK));
            } else if (Build.VERSION.SDK_INT >= 26) {
                vibrator.vibrate(VibrationEffect.createOneShot(30, 80));
            } else {
                vibrator.vibrate(30);
            }
        } catch (Exception ignored) {
            //No vibration is not a problem
        }
    }
}
