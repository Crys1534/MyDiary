package com.kiminonawa.mydiary.entries.diary;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;

import androidx.core.graphics.ColorUtils;

import com.kiminonawa.mydiary.R;
import com.kiminonawa.mydiary.shared.ScreenHelper;
import com.kiminonawa.mydiary.shared.Sfx;
import com.kiminonawa.mydiary.shared.ThemeManager;
import com.kiminonawa.mydiary.shared.gui.FlowLayout;

import java.util.ArrayList;
import java.util.List;

/**
 * Where the tags are written: every tag is a small chip, and a text box is after the last chip. A tag is finished when
 * a space, a comma or Enter is typed (the word becomes a chip); a chip is taken away by touching it, or with the
 * delete key in the empty text box.
 */
public class TagEditorView extends FlowLayout {

    private final List<String> tags = new ArrayList<>();
    private final EditText input;
    private final int chipColor;
    private final int chipTextColor;
    //While the text box is changed by this class, the watcher has nothing to do
    private boolean updating = false;

    public TagEditorView(Context context, String savedTags) {
        super(context);
        int padding = ScreenHelper.dpToPixel(context.getResources(), 4);
        setPadding(padding, padding, padding, padding);

        chipColor = ThemeManager.getInstance().getThemeMainColor(context);
        chipTextColor = ColorUtils.calculateLuminance(chipColor) > 0.55 ? 0xFF222222 : 0xFFFFFFFF;

        input = new EditText(context);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setSingleLine(true);
        input.setHint(R.string.tags_input_hint);
        input.setImeOptions(EditorInfo.IME_ACTION_DONE);
        input.setMinWidth(ScreenHelper.dpToPixel(context.getResources(), 110));
        input.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                //Done / Enter: the word that is being written is finished (the window stays open)
                finishWord(true);
                return true;
            }
        });
        input.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (keyCode == KeyEvent.KEYCODE_DEL && event.getAction() == KeyEvent.ACTION_DOWN
                        && input.getText().length() == 0 && !tags.isEmpty()) {
                    //Nothing to delete in the text box: the last tag goes
                    removeTag(tags.size() - 1);
                    return true;
                }
                return false;
            }
        });
        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (!updating) {
                    takeFinishedWords(s);
                }
            }
        });
        setStretchLast(true);
        addView(input);
        for (String tag : TagHelper.toList(savedTags)) {
            addTag(tag, false);
        }
    }

    public EditText getInput() {
        return input;
    }

    /**
     * The tags as the database keeps them (",school,trip,"), including the word that is still being written.
     */
    public String getSavedTags() {
        StringBuilder text = new StringBuilder();
        for (String tag : tags) {
            text.append(tag).append(' ');
        }
        text.append(input.getText());
        return TagHelper.normalize(text.toString());
    }

    /**
     * Words that end with a space, a comma, a semicolon or a new line become chips; the rest stays in the box.
     */
    private void takeFinishedWords(Editable s) {
        String text = s.toString();
        int lastSeparator = -1;
        for (int i = text.length() - 1; i >= 0; i--) {
            char c = text.charAt(i);
            if (c == ' ' || c == ',' || c == ';' || c == '\n') {
                lastSeparator = i;
                break;
            }
        }
        if (lastSeparator < 0) {
            return;
        }
        String finished = text.substring(0, lastSeparator + 1);
        String rest = text.substring(lastSeparator + 1);
        updating = true;
        input.setText(rest);
        input.setSelection(rest.length());
        updating = false;
        for (String part : finished.split("[\\s,;]+")) {
            addTag(part, true);
        }
    }

    /**
     * The word that is being written is finished.
     *
     * @param keepFocus put the cursor back in the text box
     */
    private void finishWord(boolean keepFocus) {
        String word = input.getText().toString();
        if (word.trim().length() > 0) {
            updating = true;
            input.setText("");
            updating = false;
            for (String part : word.split("[\\s,;]+")) {
                addTag(part, true);
            }
        }
        if (keepFocus) {
            input.requestFocus();
        }
    }

    private void addTag(String raw, boolean withSound) {
        String tag = TagHelper.cleanTag(raw == null ? "" : raw);
        if (tag.length() == 0 || tags.contains(tag)) {
            return;
        }
        tags.add(tag);
        //The chips are before the text box
        addView(makeChip(tag), getChildCount() - 1);
        if (withSound) {
            Sfx.play(getContext(), Sfx.Sound.PEN_TICK);
        }
    }

    private void removeTag(int index) {
        if (index < 0 || index >= tags.size()) {
            return;
        }
        tags.remove(index);
        removeViewAt(index);
        Sfx.play(getContext(), Sfx.Sound.RUSTLE);
    }

    private View makeChip(final String tag) {
        TextView chip = new TextView(getContext());
        chip.setText(tag + "  ×");
        chip.setTextColor(chipTextColor);
        chip.setTextSize(15);
        chip.setGravity(Gravity.CENTER);
        int horizontal = ScreenHelper.dpToPixel(getResources(), 12);
        int vertical = ScreenHelper.dpToPixel(getResources(), 6);
        chip.setPadding(horizontal, vertical, horizontal, vertical);
        GradientDrawable background = new GradientDrawable();
        background.setColor(chipColor);
        background.setCornerRadius(ScreenHelper.dpToPixel(getResources(), 16));
        chip.setBackground(background);
        chip.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                removeTag(tags.indexOf(tag));
            }
        });
        return chip;
    }
}
