package com.kiminonawa.mydiary.entries.calendar;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

/**
 * The little picture of a day in the month calendar: the photo of the entry (cut to fill the square, with round
 * corners) and a dark veil over it so the number of the day can be read.
 */
public class DayPhotoDrawable extends Drawable {

    private final Bitmap bitmap;
    private final BitmapShader shader;
    private final Paint photoPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint veilPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix matrix = new Matrix();
    private final RectF rect = new RectF();
    private final float inset;
    private final float radius;
    private final float density;

    public DayPhotoDrawable(Bitmap bitmap, float density) {
        this.bitmap = bitmap;
        this.density = density;
        this.shader = new BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        this.photoPaint.setShader(shader);
        this.veilPaint.setColor(0x59000000);
        this.inset = 2f * density;
        this.radius = 6f * density;
    }

    @Override
    public void draw(Canvas canvas) {
        rect.set(getBounds());
        rect.inset(inset, inset);
        if (rect.width() <= 0 || rect.height() <= 0) {
            return;
        }
        float scale = Math.max(rect.width() / bitmap.getWidth(), rect.height() / bitmap.getHeight());
        matrix.setScale(scale, scale);
        matrix.postTranslate(rect.left + (rect.width() - bitmap.getWidth() * scale) / 2f,
                rect.top + (rect.height() - bitmap.getHeight() * scale) / 2f);
        shader.setLocalMatrix(matrix);
        canvas.drawRoundRect(rect, radius, radius, photoPaint);
        canvas.drawRoundRect(rect, radius, radius, veilPaint);
    }

    @Override
    public void setAlpha(int alpha) {
        photoPaint.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        photoPaint.setColorFilter(colorFilter);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

    /**
     * MaterialCalendarView makes a copy of the background of every day with getConstantState().newDrawable(),
     * so this can not be null.
     */
    @Override
    public ConstantState getConstantState() {
        return new State(bitmap, density);
    }

    private static class State extends ConstantState {
        private final Bitmap bitmap;
        private final float density;

        State(Bitmap bitmap, float density) {
            this.bitmap = bitmap;
            this.density = density;
        }

        @Override
        public Drawable newDrawable() {
            return new DayPhotoDrawable(bitmap, density);
        }

        @Override
        public Drawable newDrawable(Resources res) {
            return new DayPhotoDrawable(bitmap, density);
        }

        @Override
        public int getChangingConfigurations() {
            return 0;
        }
    }
}
