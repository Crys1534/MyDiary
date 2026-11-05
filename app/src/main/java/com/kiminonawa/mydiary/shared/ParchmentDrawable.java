package com.kiminonawa.mydiary.shared;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;

import java.util.Random;

/**
 * The page of the grimoire (Esoteric theme): old parchment with tea stains, burnt edges and a thin golden frame with
 * small ornaments in the corners. It is light with the light look of the app and dark (aged leather) with the dark look,
 * so the text colors of the diary are always readable. Everything is drawn with code.
 */
public class ParchmentDrawable extends Drawable {

    private final boolean night;
    private final float density;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private Bitmap cache;
    private int cacheW = -1, cacheH = -1;

    public ParchmentDrawable(float density, boolean night) {
        this.density = density;
        this.night = night;
    }

    private void build(int w, int h) {
        //A smaller picture is enough (it is soft); it is stretched when drawn
        float scale = Math.min(1f, 540f / Math.max(1, w));
        int bw = Math.max(32, Math.round(w * scale));
        int bh = Math.max(32, Math.round(h * scale));
        Bitmap bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        Random rnd = new Random(1534);
        int base = night ? 0xFF2A2018 : 0xFFEAD9B0;
        c.drawColor(base);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        //Cloudy blotches of old paper
        for (int i = 0; i < 38; i++) {
            float x = rnd.nextFloat() * bw, y = rnd.nextFloat() * bh;
            float r = (30 + rnd.nextFloat() * 120) * scale * density / 2.2f;
            int tint = night ? (rnd.nextBoolean() ? 0x16FFE0A0 : 0x22000000) : (rnd.nextBoolean() ? 0x26B8864A : 0x1AFFF4D8);
            p.setShader(new RadialGradient(x, y, Math.max(8f, r), tint, tint & 0x00FFFFFF, Shader.TileMode.CLAMP));
            c.drawCircle(x, y, Math.max(8f, r), p);
        }
        p.setShader(null);
        //Tea stains: soft discs with a darker ring
        for (int i = 0; i < 3; i++) {
            float x = bw * (0.15f + rnd.nextFloat() * 0.7f), y = bh * (0.1f + rnd.nextFloat() * 0.8f);
            float r = (28 + rnd.nextFloat() * 26) * scale * density;
            p.setShader(new RadialGradient(x, y, r, new int[]{0x00000000, night ? 0x10E0B070 : 0x14A06A2A, night ? 0x55E0B070 : 0x66935A20, 0x00000000},
                    new float[]{0f, 0.72f, 0.88f, 1f}, Shader.TileMode.CLAMP));
            c.drawCircle(x, y, r, p);
        }
        p.setShader(null);
        //Fibres and specks
        for (int i = 0; i < 900; i++) {
            p.setColor((night ? 0x14FFFFFF : 0x1A5A3A12) & 0x00FFFFFF | ((8 + rnd.nextInt(28)) << 24));
            float x = rnd.nextFloat() * bw, y = rnd.nextFloat() * bh;
            c.drawCircle(x, y, 0.6f + rnd.nextFloat() * 0.9f, p);
        }
        //Burnt edges: a dark brown that fades inside, a bit uneven
        float edge = 26f * scale * density;
        int burn = night ? 0xDD0A0604 : 0xCC3A1A06;
        int clear = burn & 0x00FFFFFF;
        p.setShader(new LinearGradient(0, 0, 0, edge, burn, clear, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, bw, edge, p);
        p.setShader(new LinearGradient(0, bh, 0, bh - edge, burn, clear, Shader.TileMode.CLAMP));
        c.drawRect(0, bh - edge, bw, bh, p);
        p.setShader(new LinearGradient(0, 0, edge, 0, burn, clear, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, edge, bh, p);
        p.setShader(new LinearGradient(bw, 0, bw - edge, 0, burn, clear, Shader.TileMode.CLAMP));
        c.drawRect(bw - edge, 0, bw, bh, p);
        for (int i = 0; i < 40; i++) {
            //little burn marks
            int side = rnd.nextInt(4);
            float x = side < 2 ? rnd.nextFloat() * bw : (side == 2 ? 0 : bw);
            float y = side >= 2 ? rnd.nextFloat() * bh : (side == 0 ? 0 : bh);
            float r = (6 + rnd.nextFloat() * 22) * scale * density;
            p.setShader(new RadialGradient(x, y, r, burn, clear, Shader.TileMode.CLAMP));
            c.drawCircle(x, y, r, p);
        }
        p.setShader(null);
        cache = bmp;
        cacheW = w;
        cacheH = h;
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        Rect b = getBounds();
        int w = b.width(), h = b.height();
        if (w <= 0 || h <= 0) {
            return;
        }
        if (cache == null || cacheW != w || cacheH != h) {
            build(w, h);
        }
        canvas.drawBitmap(cache, null, b, bitmapPaint);
        //Golden double frame with small diamonds in the corners
        paint.setStyle(Paint.Style.STROKE);
        int gold = night ? 0xAAD4AF37 : 0xAA9C7A22;
        paint.setColor(gold);
        float in1 = 10 * density, in2 = 14 * density;
        paint.setStrokeWidth(Math.max(1f, density * 1.2f));
        canvas.drawRect(b.left + in1, b.top + in1, b.right - in1, b.bottom - in1, paint);
        paint.setStrokeWidth(Math.max(1f, density * 0.6f));
        canvas.drawRect(b.left + in2, b.top + in2, b.right - in2, b.bottom - in2, paint);
        paint.setStyle(Paint.Style.FILL);
        float d = 4 * density;
        float[][] corners = {{b.left + in1, b.top + in1}, {b.right - in1, b.top + in1},
                {b.left + in1, b.bottom - in1}, {b.right - in1, b.bottom - in1}};
        for (float[] cpt : corners) {
            android.graphics.Path diamond = new android.graphics.Path();
            diamond.moveTo(cpt[0], cpt[1] - d);
            diamond.lineTo(cpt[0] + d, cpt[1]);
            diamond.lineTo(cpt[0], cpt[1] + d);
            diamond.lineTo(cpt[0] - d, cpt[1]);
            diamond.close();
            canvas.drawPath(diamond, paint);
        }
    }

    @Override
    public void setAlpha(int alpha) {
        bitmapPaint.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        bitmapPaint.setColorFilter(colorFilter);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.OPAQUE;
    }
}
