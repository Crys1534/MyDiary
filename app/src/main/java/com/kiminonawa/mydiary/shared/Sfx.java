package com.kiminonawa.mydiary.shared;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.os.SystemClock;
import android.util.Log;
import android.util.SparseIntArray;

import androidx.annotation.RawRes;

import com.kiminonawa.mydiary.R;

/**
 * The small, quiet sounds of the diary: a page of a notebook, a pen, a stamp, paper in a hand...
 * <p>
 * The sounds are made by tools/make_sounds.py (they are not taken from anywhere). They are played with the sound
 * "of the system" (the same volume as the touch sounds of the phone), so they follow the silent mode of the phone,
 * and they can be turned off or made quieter in Settings.
 */
public final class Sfx {

    public enum Sound {
        /**
         * A page of a notebook is turned (the Diary tab, to begin a new entry)
         */
        PAGE_FLIP(R.raw.sfx_page_flip),
        /**
         * A softer page (changing tab, opening an entry)
         */
        PAGE_SOFT(R.raw.sfx_page_soft),
        RUSTLE(R.raw.sfx_rustle),
        SLIDE(R.raw.sfx_slide),
        NOTEBOOK_OPEN(R.raw.sfx_notebook_open),
        NOTEBOOK_CLOSE(R.raw.sfx_notebook_close),
        PEN_CLICK(R.raw.sfx_pen_click),
        PEN_TICK(R.raw.sfx_pen_tick),
        PENCIL_TAP(R.raw.sfx_pencil_tap),
        CHECK(R.raw.sfx_check),
        PEN_WRITE(R.raw.sfx_pen_write),
        SAVE(R.raw.sfx_save),
        CRUMPLE(R.raw.sfx_crumple),
        STAR(R.raw.sfx_star),
        PHOTO(R.raw.sfx_photo),
        POP(R.raw.sfx_pop),
        TAB(R.raw.sfx_tab),
        LOCK(R.raw.sfx_lock),
        CLOSE(R.raw.sfx_close);

        final int rawId;

        Sound(@RawRes int rawId) {
            this.rawId = rawId;
        }
    }

    private static final String TAG = "Sfx";
    //The same sound is not played again before this time (a fast double tap would sound like a machine gun)
    private static final long MIN_GAP_MS = 70;

    private static SoundPool pool;
    //raw resource id -> sound id of the pool
    private static final SparseIntArray soundIds = new SparseIntArray();
    private static final SparseIntArray loaded = new SparseIntArray();
    private static final long[] lastPlayed = new long[Sound.values().length];

    private Sfx() {
    }

    /**
     * Loads all the sounds (they are small). Call it once when the app starts.
     */
    public static synchronized void init(Context context) {
        if (pool != null) {
            return;
        }
        try {
            AudioAttributes attributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();
            pool = new SoundPool.Builder().setMaxStreams(4).setAudioAttributes(attributes).build();
            pool.setOnLoadCompleteListener(new SoundPool.OnLoadCompleteListener() {
                @Override
                public void onLoadComplete(SoundPool soundPool, int sampleId, int status) {
                    if (status == 0) {
                        loaded.put(sampleId, 1);
                    } else {
                        Log.e(TAG, "sound " + sampleId + " did not load: " + status);
                    }
                }
            });
            Context app = context.getApplicationContext();
            for (Sound sound : Sound.values()) {
                soundIds.put(sound.rawId, pool.load(app, sound.rawId, 1));
            }
        } catch (Exception e) {
            //A diary without sounds is still a diary
            Log.e(TAG, "sounds could not be loaded", e);
            pool = null;
        }
    }

    /**
     * Plays a sound, when sounds are on in Settings.
     */
    public static void play(Context context, Sound sound) {
        if (context == null || sound == null) {
            return;
        }
        try {
            if (pool == null) {
                init(context);
            }
            if (pool == null || !SPFManager.isSoundOn(context)) {
                return;
            }
            long now = SystemClock.elapsedRealtime();
            if (now - lastPlayed[sound.ordinal()] < MIN_GAP_MS) {
                return;
            }
            int id = soundIds.get(sound.rawId, 0);
            if (id == 0 || loaded.get(id, 0) == 0) {
                return;
            }
            lastPlayed[sound.ordinal()] = now;
            float volume = SPFManager.getSoundVolume(context) / 100f;
            int stream = pool.play(id, volume, volume, 1, 0, 1f);
            Log.i(TAG, "played " + sound + " volume=" + volume + " stream=" + stream);
        } catch (Exception e) {
            Log.e(TAG, "sound could not be played", e);
        }
    }
}
