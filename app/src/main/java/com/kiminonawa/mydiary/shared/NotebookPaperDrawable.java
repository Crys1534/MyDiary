package com.kiminonawa.mydiary.shared;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;

/**
 * The sheet of the notebook (Settings): paper texture (a small picture that is repeated), a red margin line on the
 * left and the holes of the rings or of a binder along the left edge.
 */
public class NotebookPaperDrawable extends Drawable {

    //Where the margin line is (from the left edge of the sheet), in dp
    public static final float MARGIN_X_DP = 32f;
    //The holes: x of the center, radius and the distance from one to the next one, in dp
    private static final float HOLE_X_DP = 13f;
    private static final float HOLE_RADIUS_DP = 5.5f;
    private static final float HOLE_FIRST_DP = 90f;
    private static final float HOLE_SPACING_DP = 170f;

    private final Paint paperPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint marginPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint holeShadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint holePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint holeEdgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density;

    public NotebookPaperDrawable(Context context, Bitmap tile, boolean night) {
        density = context.getResources().getDisplayMetrics().density;
        paperPaint.setShader(new BitmapShader(tile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT));

        marginPaint.setStyle(Paint.Style.STROKE);
        marginPaint.setStrokeWidth(Math.max(1f, density * 1.1f));
        marginPaint.setColor(night ? 0x99C96B6B : 0x99D9534F);

        //The hole shows what is behind the sheet: a darker color with a soft edge, so it looks cut out
        holeShadowPaint.setStyle(Paint.Style.FILL);
        holeShadowPaint.setColor(night ? 0x33000000 : 0x2A000000);
        holePaint.setStyle(Paint.Style.FILL);
        holePaint.setColor(night ? 0xFF101010 : 0xFF77736A);
        holeEdgePaint.setStyle(Paint.Style.STROKE);
        holeEdgePaint.setStrokeWidth(Math.max(1f, density * 0.8f));
        holeEdgePaint.setColor(night ? 0x55FFFFFF : 0x66FFFFFF);
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        Rect bounds = getBounds();
        canvas.drawRect(bounds, paperPaint);

        float marginX = bounds.left + MARGIN_X_DP * density;
        canvas.drawLine(marginX, bounds.top, marginX, bounds.bottom, marginPaint);

        float radius = HOLE_RADIUS_DP * density;
        float x = bounds.left + HOLE_X_DP * density;
        float spacing = HOLE_SPACING_DP * density;
        int guard = 0;
        for (float y = bounds.top + HOLE_FIRST_DP * density; y < bounds.bottom && guard++ < 400; y += spacing) {
            canvas.drawCircle(x, y + density * 1.2f, radius + density * 1.2f, holeShadowPaint);
            canvas.drawCircle(x, y, radius, holePaint);
            canvas.drawCircle(x, y, radius, holeEdgePaint);
        }
    }

    @Override
    public void setAlpha(int alpha) {
        paperPaint.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        paperPaint.setColorFilter(colorFilter);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.OPAQUE;
    }
}
