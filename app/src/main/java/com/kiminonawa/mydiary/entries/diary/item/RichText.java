package com.kiminonawa.mydiary.entries.diary.item;

import android.graphics.Typeface;
import android.text.Editable;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.StyleSpan;

/**
 * Bold text inside a diary.
 * <p>
 * In the database the text is saved as plain text with two invisible marks around the bold part
 * (private-use characters that never appear in normal writing), so old diaries keep working:
 * "hello \uE000world\uE001" = hello <b>world</b>
 */
public class RichText {

    public static final char BOLD_START = '\uE000';
    public static final char BOLD_END = '\uE001';

    /**
     * Saved text -> text with real bold spans, to show in an EditText.
     */
    public static Spannable toSpannable(String markup) {
        if (markup == null) {
            markup = "";
        }
        SpannableStringBuilder out = new SpannableStringBuilder();
        int boldStart = -1;
        for (int i = 0; i < markup.length(); i++) {
            char c = markup.charAt(i);
            if (c == BOLD_START) {
                if (boldStart < 0) {
                    boldStart = out.length();
                }
            } else if (c == BOLD_END) {
                if (boldStart >= 0) {
                    setBold(out, boldStart, out.length());
                    boldStart = -1;
                }
            } else {
                out.append(c);
            }
        }
        //A mark that was never closed: bold until the end
        if (boldStart >= 0) {
            setBold(out, boldStart, out.length());
        }
        return out;
    }

    /**
     * Text with bold spans (what the EditText has) -> text to save.
     */
    public static String toMarkup(CharSequence text) {
        if (text == null) {
            return "";
        }
        int length = text.length();
        boolean[] bold = new boolean[length];
        if (text instanceof Spanned) {
            Spanned spanned = (Spanned) text;
            for (StyleSpan span : spanned.getSpans(0, length, StyleSpan.class)) {
                if (isBoldStyle(span)) {
                    int start = Math.max(0, spanned.getSpanStart(span));
                    int end = Math.min(length, spanned.getSpanEnd(span));
                    for (int i = start; i < end; i++) {
                        bold[i] = true;
                    }
                }
            }
        }
        StringBuilder sb = new StringBuilder();
        boolean inBold = false;
        for (int i = 0; i < length; i++) {
            char c = text.charAt(i);
            //Never let the text itself break the marks
            if (c == BOLD_START || c == BOLD_END) {
                continue;
            }
            if (bold[i] && !inBold) {
                sb.append(BOLD_START);
                inBold = true;
            } else if (!bold[i] && inBold) {
                sb.append(BOLD_END);
                inBold = false;
            }
            sb.append(c);
        }
        if (inBold) {
            sb.append(BOLD_END);
        }
        return sb.toString();
    }

    /**
     * Saved text -> plain text without the marks (for summaries in lists).
     */
    public static String strip(String markup) {
        if (markup == null) {
            return "";
        }
        return markup.replace(String.valueOf(BOLD_START), "").replace(String.valueOf(BOLD_END), "");
    }

    /**
     * Makes the selected part bold, or normal again if all of it was already bold.
     */
    public static void toggleBold(Editable text, int start, int end) {
        if (start > end) {
            int temp = start;
            start = end;
            end = temp;
        }
        start = Math.max(0, start);
        end = Math.min(text.length(), end);
        if (start >= end) {
            return;
        }
        boolean allBold = true;
        for (int i = start; i < end && allBold; i++) {
            allBold = isBoldAt(text, i);
        }
        //Take the bold off the selected part (keeping the bold outside of it)
        for (StyleSpan span : text.getSpans(start, end, StyleSpan.class)) {
            if (!isBoldStyle(span)) {
                continue;
            }
            int spanStart = text.getSpanStart(span);
            int spanEnd = text.getSpanEnd(span);
            text.removeSpan(span);
            if (spanStart < start) {
                setBold(text, spanStart, start);
            }
            if (spanEnd > end) {
                setBold(text, end, spanEnd);
            }
        }
        if (!allBold) {
            setBold(text, start, end);
        }
    }

    private static boolean isBoldAt(Spanned text, int index) {
        for (StyleSpan span : text.getSpans(index, index + 1, StyleSpan.class)) {
            if (isBoldStyle(span)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isBoldStyle(StyleSpan span) {
        return span.getStyle() == Typeface.BOLD || span.getStyle() == Typeface.BOLD_ITALIC;
    }

    private static void setBold(Spannable text, int start, int end) {
        if (end > start) {
            text.setSpan(new StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }
}
