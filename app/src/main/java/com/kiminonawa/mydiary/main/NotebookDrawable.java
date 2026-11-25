package com.kiminonawa.mydiary.main;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

import java.util.Random;

/**
 * The cover of a notebook: its color with a soft linen texture (fine crossed threads), rounded corners and a darker
 * spine on the left.
 */
public class NotebookDrawable extends Drawable {

    //The linen, made once (it is the same for every notebook: only the color under it changes)
    private static Bitmap linen;
    private static float linenDensity;

    private final int color;
    private final float small, big;
    private final int spineWidth;
    private final float lineWidth;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint texture = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint spine = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint spineEdge = new Paint();
    private final Path cover = new Path();
    private final Path spinePath = new Path();

    private NotebookDrawable(int color, float density, float scale) {
        this.color = color;
        small = 6 * density * scale;
        big = 12 * density * scale;
        spineWidth = (int) (10 * density * scale);
        lineWidth = Math.max(1f, density * 0.6f);
        fill.setColor(color);
        texture.setShader(new BitmapShader(linen(density), Shader.TileMode.REPEAT, Shader.TileMode.REPEAT));
        spine.setColor(0x38000000);
        spineEdge.setColor(0x14FFFFFF);
    }

    public static NotebookDrawable of(int color, float density, float scale) {
        return new NotebookDrawable(color, density, scale);
    }

    /**
     * The threads: light ones across, dark ones down, every 3dp, and a little grain so it does not look printed.
     */
    private static synchronized Bitmap linen(float density) {
        if (linen != null && linenDensity == density) {
            return linen;
        }
        int period = Math.max(2, Math.round(3 * density));
        int thread = Math.max(1, Math.round(density));
        int size = period * 24;
        int[] pixels = new int[size * size];
        Random random = new Random(7);
        for (int y = 0; y < size; y++) {
            boolean across = (y % period) < thread;
            for (int x = 0; x < size; x++) {
                boolean down = (x % period) < thread;
                //The grain: a bit darker here and there
                float black = random.nextFloat() * 0.09f;
                float white = across ? 0.07f : 0f;
                if (down) {
                    black += 0.10f;
                }
                //Mix: white over (black over the cover)
                float a = black + white * (1 - black);
                int gray = a <= 0 ? 0 : Math.round(255 * white / a);
                int alpha = Math.round(a * 255);
                pixels[y * size + x] = (alpha << 24) | (gray << 16) | (gray << 8) | gray;
            }
        }
        linen = Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888);
        linenDensity = density;
        return linen;
    }

    @Override
    protected void onBoundsChange(Rect bounds) {
        super.onBoundsChange(bounds);
        RectF all = new RectF(bounds);
        cover.reset();
        cover.addRoundRect(all, new float[]{small, small, big, big, big, big, small, small}, Path.Direction.CW);
        spinePath.reset();
        spinePath.addRoundRect(new RectF(bounds.left, bounds.top, bounds.left + spineWidth, bounds.bottom),
                new float[]{small, small, 0, 0, 0, 0, small, small}, Path.Direction.CW);
    }

    @Override
    public void draw(Canvas canvas) {
        Rect b = getBounds();
        canvas.drawPath(cover, fill);
        canvas.drawPath(cover, texture);
        canvas.drawPath(spinePath, spine);
        canvas.drawRect(b.left + spineWidth, b.top, b.left + spineWidth + lineWidth, b.bottom, spineEdge);
    }

    @Override
    public void setAlpha(int alpha) {
        fill.setAlpha(alpha);
        texture.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        fill.setColorFilter(colorFilter);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

    @Override
    public void getOutline(android.graphics.Outline outline) {
        //The shadow of the notebook follows its round corners
        Rect b = getBounds();
        outline.setRoundRect(b, small);
    }
}
