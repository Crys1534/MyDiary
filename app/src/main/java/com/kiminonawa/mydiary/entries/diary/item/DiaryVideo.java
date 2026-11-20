package com.kiminonawa.mydiary.entries.diary.item;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.kiminonawa.mydiary.R;
import com.kiminonawa.mydiary.entries.video.VideoPlayerActivity;
import com.kiminonawa.mydiary.shared.ScreenHelper;

import java.io.File;
import java.util.Locale;

/**
 * A video inside a diary: a picture of the video (its first second), a play button and how long it lasts. Touch it to
 * watch it on the whole screen. The content is the name of the video file, which is in the folder of the entry (with
 * the photos).
 */
public class DiaryVideo implements IDairyRow {

    private final Context context;
    private final View root;
    private final ImageView IV_frame, IV_delete;
    private final TextView TV_duration;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private String fileName = "";
    private File folder;
    private int position;
    private int loadToken = 0;

    public DiaryVideo(Context context) {
        this.context = context;
        root = LayoutInflater.from(context).inflate(R.layout.layout_diary_video, null, false);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, ScreenHelper.dpToPixel(context.getResources(), 210));
        int margin = ScreenHelper.dpToPixel(context.getResources(), 6);
        params.topMargin = margin;
        params.bottomMargin = margin;
        root.setLayoutParams(params);
        IV_frame = (ImageView) root.findViewById(R.id.IV_diary_video_frame);
        IV_delete = (ImageView) root.findViewById(R.id.IV_diary_video_delete);
        TV_duration = (TextView) root.findViewById(R.id.TV_diary_video_duration);
        root.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                play();
            }
        });
        setEditMode(false);
    }

    public void setFolder(File folder) {
        this.folder = folder;
        load();
    }

    public void setDeleteClickListener(View.OnClickListener listener) {
        IV_delete.setOnClickListener(listener);
    }

    private File file() {
        return folder == null || fileName.length() == 0 ? null : new File(folder, fileName);
    }

    private void play() {
        File f = file();
        if (f == null || !f.exists()) {
            Toast.makeText(context, R.string.video_error, Toast.LENGTH_LONG).show();
            return;
        }
        Intent intent = new Intent(context, VideoPlayerActivity.class);
        intent.putExtra(VideoPlayerActivity.EXTRA_PATH, f.getAbsolutePath());
        context.startActivity(intent);
    }

    /**
     * The picture and the length are read from the file in the background.
     */
    private void load() {
        final File f = file();
        final int token = ++loadToken;
        IV_frame.setImageDrawable(null);
        TV_duration.setVisibility(View.GONE);
        if (f == null || !f.exists()) {
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                Bitmap frame = null;
                long ms = -1;
                MediaMetadataRetriever retriever = new MediaMetadataRetriever();
                try {
                    retriever.setDataSource(f.getAbsolutePath());
                    String d = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                    if (d != null) {
                        ms = Long.parseLong(d);
                    }
                    frame = retriever.getFrameAtTime(Math.min(1000000L, Math.max(0, ms * 500)),
                            MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
                    if (frame != null && frame.getWidth() > 960) {
                        int h = frame.getHeight() * 960 / frame.getWidth();
                        Bitmap small = Bitmap.createScaledBitmap(frame, 960, Math.max(1, h), true);
                        if (small != frame) {
                            frame.recycle();
                        }
                        frame = small;
                    }
                } catch (Exception ignored) {
                } finally {
                    try {
                        retriever.release();
                    } catch (Exception ignored) {
                    }
                }
                final Bitmap shown = frame;
                final long length = ms;
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (token != loadToken) {
                            return;
                        }
                        if (shown != null) {
                            IV_frame.setImageBitmap(shown);
                        }
                        if (length > 0) {
                            long s = length / 1000;
                            TV_duration.setText(String.format(Locale.US, "%d:%02d", s / 60, s % 60));
                            TV_duration.setVisibility(View.VISIBLE);
                        }
                    }
                });
            }
        }).start();
    }

    @Override
    public void setContent(String content) {
        fileName = content == null ? "" : content.trim();
        load();
    }

    @Override
    public String getContent() {
        return fileName;
    }

    @Override
    public int getType() {
        return TYPE_VIDEO;
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
