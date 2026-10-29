package com.kiminonawa.mydiary.entries.diary.area;

import android.graphics.RectF;

import java.util.Locale;

/**
 * The part of a photo that the person wants to see ("visible area"). The photo file is never changed:
 * only these rectangles are saved. A rectangle is written in fractions of the photo (0 to 1), so it keeps
 * working whatever the size of the file is:
 * left, top, right, bottom = 0.10, 0.00, 0.90, 1.00 means "from 10% to 90% of the width, all the height".
 * <p>
 * A photo has two areas, because the same photo is shown in two shapes: inside the entry (wide, 4:3) and
 * as the cover of the entry (tall, 3:4). No area (null) means "the center", like before.
 * <p>
 * Text form (what is saved in the database and in the backups): {@code b=l,t,r,b;c=l,t,r,b;o=1}
 * (b = body of the entry, c = cover, o=1 = the photo is only the cover: it is not shown inside the entry).
 * An empty text means that nothing was chosen.
 */
public final class PhotoArea {

    public static final PhotoArea NONE = new PhotoArea(null, null, false);

    /**
     * The area inside the entry (4:3), or null for the center.
     */
    public final RectF body;
    /**
     * The area for the cover (3:4), or null for the center.
     */
    public final RectF cover;
    /**
     * The photo is only the cover of the entry (chosen from the cover, without putting it in the text): it is saved
     * with the entry like any photo, but it is not shown between the paragraphs.
     */
    public final boolean coverOnly;

    private PhotoArea(RectF body, RectF cover, boolean coverOnly) {
        this.body = body;
        this.cover = cover;
        this.coverOnly = coverOnly;
    }

    public static PhotoArea of(RectF body, RectF cover) {
        return of(body, cover, false);
    }

    public static PhotoArea of(RectF body, RectF cover, boolean coverOnly) {
        if (body == null && cover == null && !coverOnly) {
            return NONE;
        }
        return new PhotoArea(body == null ? null : new RectF(body), cover == null ? null : new RectF(cover), coverOnly);
    }

    public PhotoArea withBody(RectF newBody) {
        return of(newBody, cover, coverOnly);
    }

    public PhotoArea withCover(RectF newCover) {
        return of(body, newCover, coverOnly);
    }

    public PhotoArea withCoverOnly(boolean newCoverOnly) {
        return of(body, cover, newCoverOnly);
    }

    public boolean isEmpty() {
        return body == null && cover == null && !coverOnly;
    }

    /**
     * @param text what {@link #toString()} wrote (null or empty = nothing chosen). Wrong text is ignored.
     */
    public static PhotoArea parse(String text) {
        if (text == null || text.trim().length() == 0) {
            return NONE;
        }
        RectF body = null;
        RectF cover = null;
        boolean coverOnly = false;
        for (String part : text.split(";")) {
            int equals = part.indexOf('=');
            if (equals < 0) {
                continue;
            }
            String key = part.substring(0, equals).trim();
            if ("o".equals(key)) {
                coverOnly = "1".equals(part.substring(equals + 1).trim());
                continue;
            }
            RectF rect = parseRect(part.substring(equals + 1));
            if ("b".equals(key)) {
                body = rect;
            } else if ("c".equals(key)) {
                cover = rect;
            }
        }
        return of(body, cover, coverOnly);
    }

    /**
     * @return the rectangle, or null when the text is not a valid area
     */
    public static RectF parseRect(String text) {
        if (text == null) {
            return null;
        }
        String[] numbers = text.trim().split(",");
        if (numbers.length != 4) {
            return null;
        }
        try {
            float left = Float.parseFloat(numbers[0].trim());
            float top = Float.parseFloat(numbers[1].trim());
            float right = Float.parseFloat(numbers[2].trim());
            float bottom = Float.parseFloat(numbers[3].trim());
            return isValid(left, top, right, bottom) ? new RectF(left, top, right, bottom) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static boolean isValid(float left, float top, float right, float bottom) {
        //Inside the photo, and not smaller than 5% (a tiny area would be a blurry spot)
        return left >= 0f && top >= 0f && right <= 1.0001f && bottom <= 1.0001f
                && right - left >= 0.05f && bottom - top >= 0.05f;
    }

    public static String formatRect(RectF rect) {
        return String.format(Locale.US, "%.4f,%.4f,%.4f,%.4f", rect.left, rect.top, rect.right, rect.bottom);
    }

    @Override
    public String toString() {
        StringBuilder text = new StringBuilder();
        if (body != null) {
            text.append("b=").append(formatRect(body));
        }
        if (cover != null) {
            if (text.length() > 0) {
                text.append(';');
            }
            text.append("c=").append(formatRect(cover));
        }
        if (coverOnly) {
            if (text.length() > 0) {
                text.append(';');
            }
            text.append("o=1");
        }
        return text.toString();
    }
}
