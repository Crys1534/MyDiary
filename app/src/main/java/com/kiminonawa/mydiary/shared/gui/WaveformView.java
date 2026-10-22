package com.kiminonawa.mydiary.shared.gui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

/**
 * The "waves" of an audio: little vertical bars as high as the sound is loud. The part already played has the
 * color of the theme. When it is allowed, touching it moves the audio to that place.
 */
public class WaveformView extends View {

    public interface OnSeekListener {
        void onSeekStart();

        void onSeekChange(float fraction);

        void onSeekEnd(float fraction);

        void onSeekCancel();
    }

    private final Paint playedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint restPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float barWidth;
    private final float gap;
    private final float minBar;

    private float[] peaks = null;
    private float progress = 0f;
    private boolean seekEnabled = false;
    private OnSeekListener seekListener = null;

    public WaveformView(Context context) {
        this(context, null);
    }

    public WaveformView(Context context, AttributeSet attrs) {
        super(context, attrs);
        float density = context.getResources().getDisplayMetrics().density;
        barWidth = 3f * density;
        gap = 2f * density;
        minBar = 3f * density;
        playedPaint.setColor(0xFF607D8B);
        restPaint.setColor(0xFFC3CAD0);
    }

    /**
     * The color of the part that was already played.
     */
    public void setPlayedColor(int color) {
        playedPaint.setColor(color);
        invalidate();
    }

    /**
     * The numbers (0 to 1) of the waves, or null to show flat little dots (not known yet).
     */
    public void setPeaks(float[] peaks) {
        this.peaks = peaks;
        invalidate();
    }

    public boolean hasPeaks() {
        return peaks != null;
    }

    /**
     * 0 to 1
     */
    public void setProgress(float progress) {
        this.progress = Math.max(0f, Math.min(1f, progress));
        invalidate();
    }

    public void setSeekEnabled(boolean enabled) {
        seekEnabled = enabled;
    }

    public void setOnSeekListener(OnSeekListener listener) {
        seekListener = listener;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }
        int count = Math.max(1, (int) ((w + gap) / (barWidth + gap)));
        float used = count * barWidth + (count - 1) * gap;
        float left = (w - used) / 2f;
        float radius = barWidth / 2f;
        for (int i = 0; i < count; i++) {
            float value = 0f;
            if (peaks != null && peaks.length > 0) {
                int from = (int) ((long) i * peaks.length / count);
                int to = Math.max(from + 1, (int) ((long) (i + 1) * peaks.length / count));
                to = Math.min(to, peaks.length);
                from = Math.min(from, peaks.length - 1);
                for (int j = from; j < to; j++) {
                    if (peaks[j] > value) {
                        value = peaks[j];
                    }
                }
            }
            float barHeight = Math.max(minBar, value * h);
            float x = left + i * (barWidth + gap);
            float top = (h - barHeight) / 2f;
            boolean played = (i + 0.5f) / count <= progress;
            canvas.drawRoundRect(x, top, x + barWidth, top + barHeight, radius, radius, played ? playedPaint : restPaint);
        }
    }

    private float fractionOf(MotionEvent event) {
        int w = getWidth();
        return w <= 0 ? 0f : Math.max(0f, Math.min(1f, event.getX() / w));
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!seekEnabled || seekListener == null) {
            return super.onTouchEvent(event);
        }
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(true);
                }
                seekListener.onSeekStart();
                setProgress(fractionOf(event));
                seekListener.onSeekChange(progress);
                return true;
            case MotionEvent.ACTION_MOVE:
                setProgress(fractionOf(event));
                seekListener.onSeekChange(progress);
                return true;
            case MotionEvent.ACTION_UP:
                setProgress(fractionOf(event));
                seekListener.onSeekEnd(progress);
                return true;
            case MotionEvent.ACTION_CANCEL:
                seekListener.onSeekCancel();
                return true;
            default:
                return super.onTouchEvent(event);
        }
    }
}
