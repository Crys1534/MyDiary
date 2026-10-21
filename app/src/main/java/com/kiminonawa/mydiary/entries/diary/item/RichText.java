package com.kiminonawa.mydiary.entries.diary.item;

import android.graphics.Typeface;
import android.text.Editable;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.StyleSpan;

/**
 * Bold and italic text inside a diary.
 * <p>
 * In the database the text is saved as plain text with invisible marks around the formatted part
 * (private-use characters that never appear in normal writing), so old diaries keep working:
 * "hello world" = hello <b>world</b>
 * "hello world" = hello <i>world</i>
 * Both can be used together, even crossing each other.
 */
public class RichText {

    public static final char BOLD_START = '';
    public static final char BOLD_END = '';
    public static final char ITALIC_START = '';
    public static final char ITALIC_END = '';

    /**
     * Saved text -> text with real bold / italic spans, to show in an EditText.
     */
    public static Spannable toSpannable(String markup) {
        if (markup == null) {
            markup = "";
        }
        SpannableStringBuilder out = new SpannableStringBuilder();
        int boldStart = -1;
        int italicStart = -1;
        for (int i = 0; i < markup.length(); i++) {
            char c = markup.charAt(i);
            if (c == BOLD_START) {
                if (boldStart < 0) {
                    boldStart = out.length();
                }
            } else if (c == BOLD_END) {
                if (boldStart >= 0) {
                    setStyle(out, boldStart, out.length(), Typeface.BOLD);
                    boldStart = -1;
                }
            } else if (c == ITALIC_START) {
                if (italicStart < 0) {
                    italicStart = out.length();
                }
            } else if (c == ITALIC_END) {
                if (italicStart >= 0) {
                    setStyle(out, italicStart, out.length(), Typeface.ITALIC);
                    italicStart = -1;
                }
            } else {
                out.append(c);
            }
        }
        //A mark that was never closed: the format goes until the end
        if (boldStart >= 0) {
            setStyle(out, boldStart, out.length(), Typeface.BOLD);
        }
        if (italicStart >= 0) {
            setStyle(out, italicStart, out.length(), Typeface.ITALIC);
        }
        return out;
    }

    /**
     * Text with bold / italic spans (what the EditText has) -> text to save.
     */
    public static String toMarkup(CharSequence text) {
        if (text == null) {
            return "";
        }
        int length = text.length();
        boolean[] bold = new boolean[length];
        boolean[] italic = new boolean[length];
        if (text instanceof Spanned) {
            Spanned spanned = (Spanned) text;
            for (StyleSpan span : spanned.getSpans(0, length, StyleSpan.class)) {
                int start = Math.max(0, spanned.getSpanStart(span));
                int end = Math.min(length, spanned.getSpanEnd(span));
                boolean isBold = hasStyle(span, Typeface.BOLD);
                boolean isItalic = hasStyle(span, Typeface.ITALIC);
                for (int i = start; i < end; i++) {
                    if (isBold) {
                        bold[i] = true;
                    }
                    if (isItalic) {
                        italic[i] = true;
                    }
                }
            }
        }
        StringBuilder sb = new StringBuilder();
        boolean inBold = false;
        boolean inItalic = false;
        for (int i = 0; i < length; i++) {
            char c = text.charAt(i);
            //Never let the text itself break the marks
            if (isMark(c)) {
                continue;
            }
            if (bold[i] && !inBold) {
                sb.append(BOLD_START);
                inBold = true;
            } else if (!bold[i] && inBold) {
                sb.append(BOLD_END);
                inBold = false;
            }
            if (italic[i] && !inItalic) {
                sb.append(ITALIC_START);
                inItalic = true;
            } else if (!italic[i] && inItalic) {
                sb.append(ITALIC_END);
                inItalic = false;
            }
            sb.append(c);
        }
        if (inBold) {
            sb.append(BOLD_END);
        }
        if (inItalic) {
            sb.append(ITALIC_END);
        }
        return sb.toString();
    }

    private static boolean isMark(char c) {
        return c == BOLD_START || c == BOLD_END || c == ITALIC_START || c == ITALIC_END;
    }

    /**
     * Saved text -> plain text without the marks (for summaries in lists and for the search).
     */
    public static String strip(String markup) {
        if (markup == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(markup.length());
        for (int i = 0; i < markup.length(); i++) {
            char c = markup.charAt(i);
            if (!isMark(c)) {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * Makes the selected part bold, or normal again if all of it was already bold.
     */
    public static void toggleBold(Editable text, int start, int end) {
        toggleStyle(text, start, end, Typeface.BOLD);
    }

    /**
     * Makes the selected part italic, or normal again if all of it was already italic.
     */
    public static void toggleItalic(Editable text, int start, int end) {
        toggleStyle(text, start, end, Typeface.ITALIC);
    }

    /**
     * @param style Typeface.BOLD or Typeface.ITALIC. The other style of the text is never touched,
     *              so bold and italic can be combined.
     */
    private static void toggleStyle(Editable text, int start, int end, int style) {
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
        splitCombinedSpans(text);
        boolean allHaveIt = true;
        for (int i = start; i < end && allHaveIt; i++) {
            allHaveIt = hasStyleAt(text, i, style);
        }
        //Take the style off the selected part (keeping it outside of the selection)
        for (StyleSpan span : text.getSpans(start, end, StyleSpan.class)) {
            if (span.getStyle() != style) {
                continue;
            }
            int spanStart = text.getSpanStart(span);
            int spanEnd = text.getSpanEnd(span);
            text.removeSpan(span);
            if (spanStart < start) {
                setStyle(text, spanStart, start, style);
            }
            if (spanEnd > end) {
                setStyle(text, end, spanEnd, style);
            }
        }
        if (!allHaveIt) {
            setStyle(text, start, end, style);
        }
    }

    /**
     * A span that is bold AND italic at once (it can come from the keyboard or from pasting)
     * is changed into two spans, one for each style.
     */
    private static void splitCombinedSpans(Editable text) {
        for (StyleSpan span : text.getSpans(0, text.length(), StyleSpan.class)) {
            if (span.getStyle() == Typeface.BOLD_ITALIC) {
                int spanStart = text.getSpanStart(span);
                int spanEnd = text.getSpanEnd(span);
                text.removeSpan(span);
                setStyle(text, spanStart, spanEnd, Typeface.BOLD);
                setStyle(text, spanStart, spanEnd, Typeface.ITALIC);
            }
        }
    }

    private static boolean hasStyleAt(Spanned text, int index, int style) {
        for (StyleSpan span : text.getSpans(index, index + 1, StyleSpan.class)) {
            if (hasStyle(span, style)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasStyle(StyleSpan span, int style) {
        return span.getStyle() == style || span.getStyle() == Typeface.BOLD_ITALIC;
    }

    private static void setStyle(Spannable text, int start, int end, int style) {
        if (end > start) {
            text.setSpan(new StyleSpan(style), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }
}
