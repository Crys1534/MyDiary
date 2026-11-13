package com.kiminonawa.mydiary.shared;

import android.content.Context;
import android.media.MediaPlayer;

import com.kiminonawa.mydiary.R;

/**
 * Esoteric theme: calm music in the background while the category is open (a loop). It follows the sound switch and
 * the volume of Settings.
 */
public final class EsotericMusic {

    private static MediaPlayer player;

    private EsotericMusic() {
    }

    public static void start(Context context) {
        stop();
        if (!SPFManager.isSoundOn(context)) {
            return;
        }
        try {
            player = MediaPlayer.create(context.getApplicationContext(), R.raw.eso_amb_music);
            if (player == null) {
                return;
            }
            float vol = SPFManager.getSoundVolume(context) / 100f * 0.6f;
            player.setVolume(vol, vol);
            player.setLooping(true);
            player.start();
        } catch (Exception e) {
            player = null;
        }
    }

    public static void stop() {
        if (player != null) {
            try {
                player.stop();
                player.release();
            } catch (Exception ignored) {
            }
            player = null;
        }
    }
}
