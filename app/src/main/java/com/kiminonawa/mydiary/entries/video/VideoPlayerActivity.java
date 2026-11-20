package com.kiminonawa.mydiary.entries.video;

import android.app.Activity;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.MediaController;
import android.widget.Toast;
import android.widget.VideoView;

import com.kiminonawa.mydiary.R;

import java.io.File;

/**
 * Plays a video of an entry on the whole screen (black background, the usual play/pause bar).
 */
public class VideoPlayerActivity extends Activity {

    public static final String EXTRA_PATH = "path";
    private VideoView videoView;
    private int resumeAt = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        String path = getIntent().getStringExtra(EXTRA_PATH);
        if (path == null || !new File(path).exists()) {
            Toast.makeText(this, R.string.video_error, Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        FrameLayout frame = new FrameLayout(this);
        frame.setBackgroundColor(Color.BLACK);
        videoView = new VideoView(this);
        //As big as the screen (the video keeps its shape)
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT, Gravity.CENTER);
        frame.addView(videoView, lp);
        frame.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        setContentView(frame);
        MediaController controller = new MediaController(this);
        controller.setAnchorView(videoView);
        videoView.setMediaController(controller);
        videoView.setVideoURI(Uri.fromFile(new File(path)));
        if (savedInstanceState != null) {
            resumeAt = savedInstanceState.getInt("at", 0);
        }
        videoView.setOnErrorListener(new android.media.MediaPlayer.OnErrorListener() {
            @Override
            public boolean onError(android.media.MediaPlayer mp, int what, int extra) {
                Toast.makeText(VideoPlayerActivity.this, R.string.video_cant_play, Toast.LENGTH_LONG).show();
                finish();
                return true;
            }
        });
        videoView.setOnPreparedListener(new android.media.MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(android.media.MediaPlayer mp) {
                if (resumeAt > 0) {
                    videoView.seekTo(resumeAt);
                }
                videoView.start();
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (videoView != null) {
            resumeAt = videoView.getCurrentPosition();
            videoView.pause();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("at", resumeAt);
    }
}
