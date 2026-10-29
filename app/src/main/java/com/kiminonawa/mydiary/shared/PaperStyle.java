package com.kiminonawa.mydiary.shared;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.view.View;

import com.kiminonawa.mydiary.R;
import com.kiminonawa.mydiary.entries.diary.item.FormatEditText;

/**
 * The "notebook sheet" look of the entries (Settings): a paper texture behind the entry and a ruled line under every
 * line of text. It is only a way of seeing the entry, nothing is changed in what is saved.
 */
public final class PaperStyle {

    //Lines of the sheet: blue-gray pencil lines (light) / soft light lines (dark)
    private static final int LINE_COLOR_LIGHT = 0x80789BCB;
    private static final int LINE_COLOR_NIGHT = 0x66B4C3DC;
    //The lines are a bit more apart than in plain text, like the lines of a notebook
    private static final float LINE_SPACING = 1.3f;

    private PaperStyle() {
    }

    public static boolean isOn(Context context) {
        return SPFManager.isNotebookPaper(context);
    }

    /**
     * The paper (the same small picture repeated, light or dark according to the look of the app), or null if it can not be read.
     */
    public static Drawable background(Context context) {
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inScaled = false;
            Bitmap tile = BitmapFactory.decodeResource(context.getResources(), R.drawable.paper_tile, options);
            if (tile == null) {
                return null;
            }
            BitmapDrawable paper = new BitmapDrawable(context.getResources(), tile);
            paper.setTileModeXY(Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);
            return paper;
        } catch (Throwable e) {
            return null;
        }
    }

    /**
     * Puts the paper behind the whole page of the entry (the cover is on top of it) if the person has it turned on.
     * The part with the title, tags and text is made see-through so the paper is seen.
     */
    public static void applyToPage(Context context, View page, View content) {
        if (!isOn(context) || page == null) {
            return;
        }
        Drawable paper = background(context);
        if (paper != null) {
            page.setBackground(paper);
            if (content != null) {
                content.setBackgroundColor(Color.TRANSPARENT);
            }
        }
    }

    public static int lineColor(Context context) {
        return ColorTools.isNight(context) ? LINE_COLOR_NIGHT : LINE_COLOR_LIGHT;
    }

    /**
     * Ruled lines for a text box of the entry, only if the person has the notebook sheet turned on.
     */
    public static void apply(Context context, FormatEditText editText) {
        if (isOn(context)) {
            applyAlways(context, editText);
        }
    }

    public static void applyAlways(Context context, FormatEditText editText) {
        editText.setLineSpacing(0f, LINE_SPACING);
        editText.setRuled(true, lineColor(context));
    }
}
