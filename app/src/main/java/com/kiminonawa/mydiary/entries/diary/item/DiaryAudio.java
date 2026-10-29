package com.kiminonawa.mydiary.entries.diary.item;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.kiminonawa.mydiary.R;
import com.kiminonawa.mydiary.shared.AudioDownloader;
import com.kiminonawa.mydiary.shared.AudioLink;
import com.kiminonawa.mydiary.shared.ScreenHelper;
import com.kiminonawa.mydiary.shared.ThemeManager;
import com.kiminonawa.mydiary.shared.WaveformExtractor;
import com.kiminonawa.mydiary.shared.gui.WaveformView;

import java.io.File;
import java.util.Locale;

/**
 * An audio inside a diary: a link (for example of Google Drive) with a play / pause button and the waves of the sound.
 * Only one audio plays at a time. The audio is not part of the diary: only the link is saved. When it is played
 * the first time it is downloaded to the temporary memory of the app (with its percentage) and played from there.
 */
public class DiaryAudio implements IDairyRow {

    //The audio that is playing now (only one at a time)
    private static DiaryAudio current = null;

    private final Context context;
    private final View root;
    private final ImageView IV_play, IV_open, IV_delete;
    private final WaveformView WV_wave;
    private final TextView TV_time;
    private final Handler handler = new Handler(Looper.getMainLooper());

    /**
     * The content of an audio that was taken from the phone starts with this: "local:" + the file name inside the folder
     * of the entry + "|" + the name it had on the phone. Any other content is a link.
     */
    public static final String LOCAL_PREFIX = "local:";

    private String link = "";
    //Audio of the phone: the file is in the folder of the entry (like the photos)
    private File localFolder = null;
    private String localFileName = null;
    private String localDisplayName = "";
    private int position;
    private MediaPlayer player = null;
    private boolean prepared = false;
    private boolean preparing = false;
    private boolean userSeeking = false;
    private AudioDownloader downloader = null;
    private int waveToken = 0;

    private final Runnable progressUpdater = new Runnable() {
        @Override
        public void run() {
            updateProgress();
            if (player != null && prepared && isPlaying()) {
                handler.postDelayed(this, 400);
            }
        }
    };

    public DiaryAudio(Context context) {
        this.context = context;
        root = LayoutInflater.from(context).inflate(R.layout.layout_diary_audio, null, false);
        android.widget.LinearLayout.LayoutParams params = new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
        int margin = ScreenHelper.dpToPixel(context.getResources(), 6);
        params.topMargin = margin;
        params.bottomMargin = margin;
        root.setLayoutParams(params);
        IV_play = (ImageView) root.findViewById(R.id.IV_diary_audio_play);
        IV_open = (ImageView) root.findViewById(R.id.IV_diary_audio_open);
        IV_delete = (ImageView) root.findViewById(R.id.IV_diary_audio_delete);
        WV_wave = (WaveformView) root.findViewById(R.id.WV_diary_audio_wave);
        TV_time = (TextView) root.findViewById(R.id.TV_diary_audio_time);

        int color = ThemeManager.getInstance().getThemeMainColor(context);
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(color);
        IV_play.setBackground(circle);
        IV_open.setColorFilter(color);
        WV_wave.setPlayedColor(color);

        IV_play.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                togglePlay();
            }
        });
        IV_open.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openOutside();
            }
        });
        WV_wave.setOnSeekListener(new WaveformView.OnSeekListener() {
            @Override
            public void onSeekStart() {
                userSeeking = true;
            }

            @Override
            public void onSeekChange(float fraction) {
                if (player != null && prepared) {
                    int duration = player.getDuration();
                    TV_time.setText(timeText((int) (duration * fraction), duration));
                }
            }

            @Override
            public void onSeekEnd(float fraction) {
                userSeeking = false;
                if (player != null && prepared) {
                    player.seekTo((int) (player.getDuration() * fraction));
                }
            }

            @Override
            public void onSeekCancel() {
                userSeeking = false;
            }
        });
        setEditMode(false);
    }

    public static String makeLocalContent(String fileName, String displayName) {
        return LOCAL_PREFIX + fileName + "|" + (displayName == null ? "" : displayName);
    }

    /**
     * @return the name of the file in the folder of the entry, or null when the content is a link
     */
    public static String localFileName(String content) {
        if (content == null || !content.startsWith(LOCAL_PREFIX)) {
            return null;
        }
        String rest = content.substring(LOCAL_PREFIX.length());
        int bar = rest.indexOf('|');
        String name = bar >= 0 ? rest.substring(0, bar) : rest;
        return name.length() == 0 ? null : name;
    }

    private static String localDisplayName(String content) {
        int bar = content.indexOf('|');
        return bar >= 0 ? content.substring(bar + 1) : "";
    }

    /**
     * The folder where the file of an audio of the phone is (the folder of the photos of the entry).
     */
    public void setLocalFolder(File folder) {
        this.localFolder = folder;
        if (isLocal()) {
            loadWave();
        }
    }

    private boolean isLocal() {
        return localFileName != null;
    }

    private File localFile() {
        return (localFolder == null || localFileName == null) ? null : new File(localFolder, localFileName);
    }

    /**
     * Edit mode: the delete button.
     */
    public void setDeleteClickListener(View.OnClickListener listener) {
        IV_delete.setOnClickListener(listener);
    }

    private boolean isPlaying() {
        try {
            return player != null && prepared && player.isPlaying();
        } catch (IllegalStateException e) {
            return false;
        }
    }

    private void togglePlay() {
        if (isPlaying()) {
            player.pause();
            IV_play.setImageResource(R.drawable.ic_play_arrow_24dp);
            return;
        }
        if (preparing) {
            return;
        }
        if (player != null && prepared) {
            startPlaying();
            return;
        }
        prepare();
    }

    private void prepare() {
        if (isLocal()) {
            File file = localFile();
            if (file == null || !file.exists()) {
                TV_time.setText(R.string.audio_error_short);
                Toast.makeText(context, R.string.audio_file_missing, Toast.LENGTH_LONG).show();
                return;
            }
            release();
            if (!WV_wave.hasPeaks()) {
                loadWave();
            }
            play(file);
            return;
        }
        if (!AudioLink.isValid(link)) {
            Toast.makeText(context, R.string.audio_error_link, Toast.LENGTH_LONG).show();
            return;
        }
        release();
        preparing = true;
        TV_time.setText(R.string.audio_loading);
        downloader = new AudioDownloader();
        downloader.start(context, AudioLink.toPlayableUrl(link), new AudioDownloader.Listener() {
            @Override
            public void onProgress(int percent, long bytes) {
                if (percent >= 0) {
                    TV_time.setText(context.getString(R.string.audio_downloading, percent));
                } else {
                    TV_time.setText(context.getString(R.string.audio_downloading_mb,
                            String.format(Locale.US, "%.1f", bytes / (1024f * 1024f))));
                }
            }

            @Override
            public void onDone(File file) {
                if (!WV_wave.hasPeaks()) {
                    loadWave();
                }
                play(file);
            }

            @Override
            public void onError(int reason) {
                release();
                int message;
                switch (reason) {
                    case AudioDownloader.ERROR_NOT_SHARED:
                        message = R.string.audio_error_not_shared;
                        break;
                    case AudioDownloader.ERROR_PAGE:
                        message = R.string.audio_error_page;
                        break;
                    case AudioDownloader.ERROR_TOO_BIG:
                        message = R.string.audio_error_big;
                        break;
                    case AudioDownloader.ERROR_NOT_HTTPS:
                        message = R.string.audio_error_link;
                        break;
                    default:
                        message = R.string.audio_error_network;
                        break;
                }
                TV_time.setText(R.string.audio_error_short);
                Toast.makeText(context, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    /**
     * Plays the audio that is already on the phone.
     */
    private void play(final File file) {
        player = new MediaPlayer();
        preparing = true;
        try {
            player.setAudioAttributes(new AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build());
            player.setDataSource(file.getAbsolutePath());
            player.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer mp) {
                    preparing = false;
                    prepared = true;
                    WV_wave.setSeekEnabled(true);
                    startPlaying();
                }
            });
            player.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mp) {
                    IV_play.setImageResource(R.drawable.ic_play_arrow_24dp);
                    WV_wave.setProgress(0);
                    TV_time.setText(timeText(0, mp.getDuration()));
                    try {
                        mp.seekTo(0);
                    } catch (IllegalStateException ignored) {
                    }
                }
            });
            player.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                @Override
                public boolean onError(MediaPlayer mp, int what, int extra) {
                    //A downloaded file that can't be played is not kept: the next try downloads it again
                    //(the audio of the phone is part of the entry: it is never deleted here)
                    if (!isLocal()) {
                        //noinspection ResultOfMethodCallIgnored
                        file.delete();
                    }
                    release();
                    TV_time.setText(R.string.audio_error_short);
                    Toast.makeText(context, R.string.audio_error_format, Toast.LENGTH_LONG).show();
                    return true;
                }
            });
            player.prepareAsync();
        } catch (Exception e) {
            if (!isLocal()) {
                //noinspection ResultOfMethodCallIgnored
                file.delete();
            }
            release();
            TV_time.setText(R.string.audio_error_short);
            Toast.makeText(context, R.string.audio_error_format, Toast.LENGTH_LONG).show();
        }
    }

    private void startPlaying() {
        if (current != null && current != this) {
            current.pause();
        }
        current = this;
        try {
            player.start();
        } catch (IllegalStateException e) {
            release();
            return;
        }
        IV_play.setImageResource(R.drawable.ic_pause_24dp);
        handler.removeCallbacks(progressUpdater);
        handler.post(progressUpdater);
    }

    /**
     * Stops the sound but keeps the place (another audio started, or the screen goes away).
     */
    public void pause() {
        if (isPlaying()) {
            try {
                player.pause();
            } catch (IllegalStateException ignored) {
            }
        }
        IV_play.setImageResource(R.drawable.ic_play_arrow_24dp);
    }

    private void updateProgress() {
        if (player == null || !prepared) {
            return;
        }
        try {
            int duration = player.getDuration();
            int now = player.getCurrentPosition();
            if (duration > 0 && !userSeeking) {
                WV_wave.setProgress((float) now / duration);
                TV_time.setText(timeText(now, duration));
            }
        } catch (IllegalStateException ignored) {
        }
    }

    private static String timeText(int nowMs, int durationMs) {
        return clock(nowMs) + " / " + clock(durationMs);
    }

    private static String clock(int ms) {
        int total = Math.max(0, ms / 1000);
        return String.format(Locale.US, "%d:%02d", total / 60, total % 60);
    }

    /**
     * Opens the link in the browser / Google Drive app (when it can't be played inside the diary).
     */
    private void openOutside() {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(link.trim()));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(context, R.string.audio_error_link, Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Frees the player (call it when the diary page goes away).
     */
    public void release() {
        handler.removeCallbacks(progressUpdater);
        if (downloader != null) {
            downloader.cancel();
            downloader = null;
        }
        if (player != null) {
            try {
                player.release();
            } catch (Exception ignored) {
            }
            player = null;
        }
        prepared = false;
        preparing = false;
        if (current == this) {
            current = null;
        }
        IV_play.setImageResource(R.drawable.ic_play_arrow_24dp);
        WV_wave.setSeekEnabled(false);
        WV_wave.setProgress(0);
    }

    /**
     * Shows the waves of the sound when the audio is already on the phone (otherwise the flat dots stay until it is
     * downloaded the first time it is played).
     */
    private void loadWave() {
        File file;
        if (isLocal()) {
            file = localFile();
            if (file == null || !file.exists()) {
                return;
            }
        } else {
            if (!AudioLink.isValid(link)) {
                return;
            }
            file = AudioDownloader.cacheFileFor(context, AudioLink.toPlayableUrl(link));
        }
        final int token = ++waveToken;
        WaveformExtractor.loadAsync(file, new WaveformExtractor.Callback() {
            @Override
            public void onWave(float[] peaks) {
                if (token == waveToken) {
                    WV_wave.setPeaks(peaks);
                }
            }
        });
    }

    @Override
    public void setContent(String content) {
        link = content == null ? "" : content.trim();
        localFileName = localFileName(link);
        localDisplayName = isLocal() ? localDisplayName(link) : "";
        IV_open.setVisibility(isLocal() ? View.GONE : View.VISIBLE);
        if (isLocal() && localDisplayName.length() > 0) {
            TV_time.setText(localDisplayName);
        } else {
            TV_time.setText(R.string.audio_tap_to_play);
        }
        WV_wave.setPeaks(null);
        loadWave();
    }

    @Override
    public String getContent() {
        return link;
    }

    @Override
    public int getType() {
        return TYPE_AUDIO;
    }

    @Override
    public View getView() {
        return root;
    }

    @Override
    public void setEditMode(boolean isEditMode) {
        IV_delete.setVisibility(isEditMode ? View.VISIBLE : View.GONE);
    }

    @Override
    public void setPosition(int position) {
        this.position = position;
        IV_delete.setTag(position);
    }

    @Override
    public int getPosition() {
        return position;
    }
}
