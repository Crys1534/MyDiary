package com.kiminonawa.mydiary.entries.diary.item;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.Editable;
import android.text.Layout;
import android.text.Spanned;
import android.text.style.LeadingMarginSpan;
import android.text.style.ReplacementSpan;

import java.util.ArrayList;

/**
 * Bullets, numbered lists and quotes inside a diary text.
 * <p>
 * In the text, a list line starts with one invisible mark (a private-use character, like the bold / italic marks of
 * {@link RichText}), so old diaries keep working and the backup does not change:
 * <pre>
 *   BULLET  first thing        -> "• first thing"
 *   NUMBER  first step         -> "1. first step"   (the number is counted when it is drawn)
 *   QUOTE   words of someone   -> "| words of someone"
 * </pre>
 * The mark is the truth. What the person sees (the dot, the number, the bar and the space on the left, also in
 * the next lines of a long item) is drawn by little spans that are made again from the marks after every change.
 */
public final class ListFormat {

    public static final char BULLET = '';
    public static final char NUMBER = '';
    public static final char QUOTE = '';

    private ListFormat() {
    }

    public static boolean isMark(char c) {
        return c == BULLET || c == NUMBER || c == QUOTE;
    }

    /**
     * Start of the line (paragraph) where the index is.
     */
    public static int lineStart(CharSequence text, int index) {
        int i = Math.max(0, Math.min(index, text.length()));
        while (i > 0 && text.charAt(i - 1) != '\n') {
            i--;
        }
        return i;
    }

    private static boolean atLineStart(CharSequence text, int index) {
        return index == 0 || text.charAt(index - 1) == '\n';
    }

    /**
     * Is the cursor right before the mark of a list line? (it has to be after it)
     */
    public static boolean isBeforeMark(CharSequence text, int index) {
        return index >= 0 && index < text.length() && isMark(text.charAt(index)) && atLineStart(text, index);
    }

    /*
     * Spans (only to draw)
     */

    /**
     * The mark itself is not seen and has no width.
     */
    private static class HiddenMark extends ReplacementSpan {
        @Override
        public int getSize(Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt fm) {
            return 0;
        }

        @Override
        public void draw(Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom, Paint paint) {
        }
    }

    /**
     * The space on the left of the whole item, and the dot / number / bar in it.
     */
    private static class Margin implements LeadingMarginSpan {
        private final char type;
        private final float density;

        Margin(char type, float density) {
            this.type = type;
            this.density = density;
        }

        @Override
        public int getLeadingMargin(boolean first) {
            return Math.round((type == QUOTE ? 18f : 28f) * density);
        }

        @Override
        public void drawLeadingMargin(Canvas c, Paint p, int x, int dir, int top, int baseline, int bottom,
                                      CharSequence text, int start, int end, boolean first, Layout layout) {
            Paint.Style oldStyle = p.getStyle();
            if (type == QUOTE) {
                //A bar along every line of the quote
                int oldColor = p.getColor();
                p.setStyle(Paint.Style.FILL);
                p.setColor(0xFF90A4AE);
                float left = x + dir * 4f * density;
                float right = x + dir * 7f * density;
                c.drawRect(Math.min(left, right), top, Math.max(left, right), bottom, p);
                p.setColor(oldColor);
            } else if (first && type == BULLET) {
                p.setStyle(Paint.Style.FILL);
                float radius = Math.max(2f * density, p.getTextSize() * 0.1f);
                c.drawCircle(x + dir * 10f * density, baseline - p.getTextSize() * 0.3f, radius, p);
            } else if (first && type == NUMBER) {
                p.setStyle(Paint.Style.FILL);
                String label = numberOf(text, start) + ".";
                float width = p.measureText(label);
                float textX = dir > 0 ? x + 4f * density : x - 4f * density - width;
                c.drawText(label, textX, baseline, p);
            }
            p.setStyle(oldStyle);
        }
    }

    /**
     * Position of the item inside its numbered list: 1 for the first, 2 for the next one, ...
     */
    private static int numberOf(CharSequence text, int paragraphStart) {
        int number = 1;
        int start = paragraphStart;
        while (start > 0) {
            int previousEnd = start - 1;   //the new-line before this paragraph
            int previousStart = lineStart(text, previousEnd);
            if (previousStart < previousEnd && text.charAt(previousStart) == NUMBER) {
                number++;
                start = previousStart;
            } else {
                break;
            }
        }
        return number;
    }

    /**
     * Makes the spans again from the marks. Marks that are not at the start of a line (they came from pasting or
     * from writing before them) are taken away.
     *
     * @return true when some stray mark was deleted
     */
    public static boolean refresh(Editable text, float density) {
        boolean anyMark = false;
        for (int i = 0; i < text.length() && !anyMark; i++) {
            anyMark = isMark(text.charAt(i));
        }
        if (!anyMark && text.getSpans(0, text.length(), Margin.class).length == 0) {
            return false;   //no list in this text: nothing to draw
        }
        boolean changedText = false;
        //1. marks that are in the middle of a line are removed
        for (int i = text.length() - 1; i >= 0; i--) {
            if (isMark(text.charAt(i)) && !atLineStart(text, i)) {
                text.delete(i, i + 1);
                changedText = true;
            }
        }
        //2. old spans out
        for (Margin span : text.getSpans(0, text.length(), Margin.class)) {
            text.removeSpan(span);
        }
        for (HiddenMark span : text.getSpans(0, text.length(), HiddenMark.class)) {
            text.removeSpan(span);
        }
        //3. new spans for every list line
        int length = text.length();
        int i = 0;
        while (i < length) {
            int newLine = indexOfNewLine(text, i);
            int end = newLine < 0 ? length : newLine + 1;
            char first = text.charAt(i);
            if (isMark(first)) {
                text.setSpan(new HiddenMark(), i, i + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                text.setSpan(new Margin(first, density), i, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            i = end;
        }
        return changedText;
    }

    private static int indexOfNewLine(CharSequence text, int from) {
        for (int i = from; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                return i;
            }
        }
        return -1;
    }

    /*
     * Changes of the text
     */

    /**
     * Puts (or takes away) the mark in the lines that have the cursor / the selection.
     *
     * @param mark BULLET, NUMBER, QUOTE; or 0 to take the list / quote away. When all the lines already have this
     *             mark, it is taken away (the same button works as an on / off switch).
     */
    public static void apply(Editable text, int selectionStart, int selectionEnd, char mark) {
        int a = Math.max(0, Math.min(Math.min(selectionStart, selectionEnd), text.length()));
        int b = Math.max(0, Math.min(Math.max(selectionStart, selectionEnd), text.length()));
        //A selection that ends right after a new-line does not include the next line
        int last = (b > a && text.charAt(b - 1) == '\n') ? b - 1 : b;

        ArrayList<Integer> starts = new ArrayList<>();
        int paragraph = lineStart(text, a);
        while (true) {
            starts.add(paragraph);
            int newLine = indexOfNewLine(text, paragraph);
            if (newLine < 0 || newLine >= last) {
                break;
            }
            paragraph = newLine + 1;
        }

        boolean allHaveIt = mark != 0;
        for (int start : starts) {
            if (start >= text.length() || text.charAt(start) != mark) {
                allHaveIt = false;
                break;
            }
        }
        //From the last line to the first, so the places of the other lines do not move
        for (int i = starts.size() - 1; i >= 0; i--) {
            int start = starts.get(i);
            char current = start < text.length() && isMark(text.charAt(start)) ? text.charAt(start) : 0;
            if (mark == 0 || allHaveIt) {
                if (current != 0) {
                    text.delete(start, start + 1);
                }
            } else if (current == 0) {
                text.insert(start, String.valueOf(mark));
            } else if (current != mark) {
                text.replace(start, start + 1, String.valueOf(mark));
            }
        }
    }

    /**
     * The person just pressed Enter (the new-line is at newLineIndex). In a list, the next line gets the same mark,
     * or, when the line was empty, the list ends.
     *
     * @return the place for the cursor, or -1 when nothing was changed
     */
    public static int continueListAfterEnter(Editable text, int newLineIndex) {
        if (newLineIndex < 0 || newLineIndex >= text.length() || text.charAt(newLineIndex) != '\n') {
            return -1;
        }
        int start = lineStart(text, newLineIndex);
        if (start >= newLineIndex || !isMark(text.charAt(start))) {
            return -1;
        }
        char mark = text.charAt(start);
        if (newLineIndex == start + 1) {
            //Only the mark and nothing written: Enter ends the list (the mark and the new line go away)
            text.delete(start, newLineIndex + 1);
            return start;
        }
        text.insert(newLineIndex + 1, String.valueOf(mark));
        return newLineIndex + 2;
    }
}
