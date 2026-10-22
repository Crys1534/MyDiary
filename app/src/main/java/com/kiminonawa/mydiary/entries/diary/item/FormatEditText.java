package com.kiminonawa.mydiary.entries.diary.item;

import android.content.Context;
import android.text.Editable;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.BackgroundColorSpan;
import android.util.AttributeSet;
import android.view.MotionEvent;

import androidx.appcompat.widget.AppCompatEditText;

/**
 * A text box that remembers the text that was selected for the formats (bold, italic).
 * <p>
 * When the person closes the keyboard, Android drops the selection (the text is not selected any
 * more). Here the last selected part is remembered and painted with a light color, so it stays
 * visible and the B / I buttons can still be used on it. The color goes away when the person
 * touches the text, writes something or moves to another box.
 */
public class FormatEditText extends AppCompatEditText {

    private static final int HIGHLIGHT_COLOR = 0x663F9BD6;

    private boolean ready = false;
    private int rememberedStart = -1;
    private int rememberedEnd = -1;
    private boolean touchedSince = false;
    private boolean changedSince = false;
    private BackgroundColorSpan highlight = null;
    //Bullets / numbers / quotes (see ListFormat)
    private boolean busy = false;
    private int pendingEnter = -1;

    public FormatEditText(Context context) {
        super(context);
        init();
    }

    public FormatEditText(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                //Written before the selection changes: this is typing, not the keyboard closing
                changedSince = true;
                forget();
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (busy) {
                    return;
                }
                //A single new line was written: the person pressed Enter
                pendingEnter = (before == 0 && count == 1 && start < s.length() && s.charAt(start) == '\n') ? start : -1;
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (busy) {
                    return;
                }
                busy = true;
                try {
                    if (pendingEnter >= 0) {
                        int cursor = ListFormat.continueListAfterEnter(s, pendingEnter);
                        if (cursor >= 0) {
                            setSelection(Math.min(cursor, s.length()));
                        }
                    }
                    pendingEnter = -1;
                    ListFormat.refresh(s, getResources().getDisplayMetrics().density);
                } finally {
                    busy = false;
                }
            }
        });
        ready = true;
    }

    @Override
    protected void onSelectionChanged(int selStart, int selEnd) {
        super.onSelectionChanged(selStart, selEnd);
        if (!ready) {
            return;
        }
        if (selStart != selEnd) {
            //A new selection
            clearHighlight();
            rememberedStart = Math.min(selStart, selEnd);
            rememberedEnd = Math.max(selStart, selEnd);
            touchedSince = false;
            changedSince = false;
        } else if (rememberedStart >= 0 && !touchedSince && !changedSince && highlight == null) {
            //The selection disappeared without the person touching or writing: the keyboard was closed
            showHighlight();
        }
        //The cursor can not be before the invisible mark of a list line
        if (!busy && selStart == selEnd && getText() != null && ListFormat.isBeforeMark(getText(), selStart)) {
            setSelection(selStart + 1);
        }
    }

    /**
     * Bullets / numbered list / quote for the lines that have the cursor or the selection.
     *
     * @param mark ListFormat.BULLET, NUMBER, QUOTE; or 0 to take it away. The same button again takes it away.
     */
    public boolean applyListFormat(char mark) {
        Editable text = getText();
        if (text == null) {
            return false;
        }
        int start = getSelectionStart();
        int end = getSelectionEnd();
        if (start < 0 || end < 0) {
            start = end = text.length();
        }
        busy = true;
        try {
            ListFormat.apply(text, start, end, mark);
            ListFormat.refresh(text, getResources().getDisplayMetrics().density);
        } finally {
            busy = false;
        }
        int cursor = getSelectionEnd();
        if (cursor >= 0 && ListFormat.isBeforeMark(text, cursor)) {
            setSelection(cursor + 1);
        }
        return true;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            touchedSince = true;
            forget();
        }
        return super.onTouchEvent(event);
    }

    @Override
    protected void onFocusChanged(boolean focused, int direction, android.graphics.Rect previouslyFocusedRect) {
        super.onFocusChanged(focused, direction, previouslyFocusedRect);
        if (ready && !focused) {
            forget();
        }
    }

    private void showHighlight() {
        Editable text = getText();
        if (text == null) {
            return;
        }
        int start = Math.max(0, Math.min(rememberedStart, text.length()));
        int end = Math.max(0, Math.min(rememberedEnd, text.length()));
        if (start >= end) {
            forget();
            return;
        }
        highlight = new BackgroundColorSpan(HIGHLIGHT_COLOR);
        text.setSpan(highlight, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private void clearHighlight() {
        if (highlight != null) {
            Editable text = getText();
            if (text != null) {
                text.removeSpan(highlight);
            }
            highlight = null;
        }
    }

    /**
     * Forgets the remembered part (and takes the color away).
     */
    private void forget() {
        clearHighlight();
        rememberedStart = -1;
        rememberedEnd = -1;
    }

    /**
     * The part of the text for the format buttons: the selected text, or the remembered one
     * (painted with the light color). Null when there is nothing.
     */
    public int[] getFormatRange() {
        int start = getSelectionStart();
        int end = getSelectionEnd();
        if (start >= 0 && end >= 0 && start != end) {
            return new int[]{Math.min(start, end), Math.max(start, end)};
        }
        if (highlight != null && getText() != null) {
            int spanStart = getText().getSpanStart(highlight);
            int spanEnd = getText().getSpanEnd(highlight);
            if (spanStart >= 0 && spanEnd > spanStart) {
                return new int[]{spanStart, spanEnd};
            }
        }
        return null;
    }
}
