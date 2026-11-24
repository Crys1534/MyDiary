package com.kiminonawa.mydiary.shared;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.io.File;

/**
 * The pictures a person chooses for a category: the photo on the cover of its notebook (main screen) and its banner
 * (on top of the category). They are saved in the folder of the category, so the backups take them too.
 */
public final class TopicArt {

    public static final String COVER = "topic_cover";
    public static final String BANNER = "topic_banner";

    private TopicArt() {
    }

    public static File file(Context context, int topicType, long topicId, String name) {
        return new File(new FileManager(context, topicType, topicId).getDir(), name);
    }

    public static boolean has(Context context, int topicType, long topicId, String name) {
        return file(context, topicType, topicId, name).isFile();
    }

    /**
     * The banner of the category on top of its screen (taller, with a shadow so its name can be read).
     *
     * @return false when the category has no banner
     */
    public static boolean applyBanner(Context context, View topbar, TextView title, int topicType, long topicId,
                                      int heightDp) {
        File banner = file(context, topicType, topicId, BANNER);
        if (!banner.isFile()) {
            return false;
        }
        Bitmap bitmap = decode(banner, 1400);
        if (bitmap == null) {
            return false;
        }
        GradientDrawable shade = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x40000000, 0x10000000, 0xB0000000});
        topbar.setBackground(new LayerDrawable(new Drawable[]{new CenterCropDrawable(bitmap), shade}));
        ViewGroup.LayoutParams params = topbar.getLayoutParams();
        if (params != null) {
            params.height = (int) (heightDp * context.getResources().getDisplayMetrics().density);
            topbar.setLayoutParams(params);
        }
        if (title != null) {
            title.setTextColor(0xFFFFFFFF);
            title.setShadowLayer(4, 1, 1, 0xCC000000);
        }
        return true;
    }

    /**
     * A picture not bigger than it needs to be (less memory).
     */
    public static Bitmap decode(File file, int maxSide) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
        int sample = 1;
        while (bounds.outWidth / (sample * 2) >= maxSide || bounds.outHeight / (sample * 2) >= maxSide) {
            sample *= 2;
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        try {
            return BitmapFactory.decodeFile(file.getAbsolutePath(), options);
        } catch (OutOfMemoryError e) {
            return null;
        }
    }

    /**
     * A picture that fills its place without being stretched (the sides that do not fit are cut).
     */
    public static class CenterCropDrawable extends Drawable {
        private final Bitmap bitmap;
        private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
        private final Matrix matrix = new Matrix();

        public CenterCropDrawable(Bitmap bitmap) {
            this.bitmap = bitmap;
        }

        @Override
        protected void onBoundsChange(Rect bounds) {
            super.onBoundsChange(bounds);
            float scale = Math.max(bounds.width() / (float) bitmap.getWidth(),
                    bounds.height() / (float) bitmap.getHeight());
            matrix.setScale(scale, scale);
            matrix.postTranslate(bounds.left + (bounds.width() - bitmap.getWidth() * scale) / 2f,
                    bounds.top + (bounds.height() - bitmap.getHeight() * scale) / 2f);
        }

        @Override
        public void draw(Canvas canvas) {
            canvas.save();
            canvas.clipRect(getBounds());
            canvas.drawBitmap(bitmap, matrix, paint);
            canvas.restore();
        }

        @Override
        public void setAlpha(int alpha) {
            paint.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(ColorFilter colorFilter) {
            paint.setColorFilter(colorFilter);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }
}
