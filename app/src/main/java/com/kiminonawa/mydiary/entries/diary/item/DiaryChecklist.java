package com.kiminonawa.mydiary.entries.diary.item;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Paint;
import androidx.appcompat.widget.AppCompatCheckBox;
import android.text.InputType;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.kiminonawa.mydiary.shared.ScreenHelper;

/**
 * One line of a checklist: a box to tick and the text.
 * Saved as "1" (done) or "0" (not done) followed by the text.
 * <p>
 * Enter makes a new line below, Enter on an empty line ends the list,
 * and the X on the right removes the line.
 */
public class DiaryChecklist implements IDairyRow {

    /**
     * Used in the viewer (not editing) to save the box right away.
     */
    public interface ToggleListener {
        void onToggled(DiaryChecklist item);
    }

    private final LinearLayout rowLayout;
    private final AppCompatCheckBox checkBox;
    private final EditText editText;
    private final ImageView deleteButton;
    private final DiaryItemHelper helper;

    private int position;
    private boolean isLoading = false;
    private ToggleListener toggleListener;

    public DiaryChecklist(Context context, DiaryItemHelper diaryItemHelper) {
        this.helper = diaryItemHelper;
        int dp2 = ScreenHelper.dpToPixel(context.getResources(), 2);
        int dp32 = ScreenHelper.dpToPixel(context.getResources(), 32);

        rowLayout = new LinearLayout(context);
        rowLayout.setOrientation(LinearLayout.HORIZONTAL);
        rowLayout.setGravity(Gravity.CENTER_VERTICAL);
        rowLayout.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        checkBox = new AppCompatCheckBox(context);
        //The cursor has to go to the text, not to the box
        checkBox.setFocusable(false);
        checkBox.setFocusableInTouchMode(false);
        rowLayout.addView(checkBox, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        editText = new FormatEditText(context);
        editText.setTextColor(Color.BLACK);
        editText.setBackgroundColor(Color.TRANSPARENT);
        editText.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
        editText.setPadding(dp2, dp2, dp2, dp2);
        //Enter = next line of the list (the text still wraps in several lines)
        editText.setRawInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        editText.setImeOptions(EditorInfo.IME_ACTION_NEXT);
        editText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (event != null && event.getAction() != KeyEvent.ACTION_DOWN) {
                    return true;
                }
                helper.onChecklistEnter(DiaryChecklist.this);
                return true;
            }
        });
        //Backspace on an empty line removes it
        editText.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (keyCode == KeyEvent.KEYCODE_DEL && event.getAction() == KeyEvent.ACTION_DOWN
                        && editText.getText().length() == 0) {
                    helper.removeChecklist(DiaryChecklist.this, true);
                    return true;
                }
                return false;
            }
        });
        LinearLayout.LayoutParams editParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        rowLayout.addView(editText, editParams);

        deleteButton = new ImageView(context);
        deleteButton.setImageResource(android.R.drawable.ic_menu_close_clear_cancel);
        deleteButton.setColorFilter(Color.GRAY);
        deleteButton.setPadding(dp2 * 3, dp2 * 3, dp2 * 3, dp2 * 3);
        deleteButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                helper.removeChecklist(DiaryChecklist.this, false);
            }
        });
        rowLayout.addView(deleteButton, new LinearLayout.LayoutParams(dp32, dp32));

        checkBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                updateLook();
                if (!isLoading && toggleListener != null) {
                    toggleListener.onToggled(DiaryChecklist.this);
                }
            }
        });
        //Default is editable
        setEditMode(true);
    }

    /**
     * Done lines are gray and crossed out.
     */
    private void updateLook() {
        if (checkBox.isChecked()) {
            editText.setPaintFlags(editText.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            editText.setTextColor(Color.GRAY);
        } else {
            editText.setPaintFlags(editText.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
            editText.setTextColor(Color.BLACK);
        }
    }

    public void setToggleListener(ToggleListener toggleListener) {
        this.toggleListener = toggleListener;
    }

    public EditText getEditText() {
        return editText;
    }

    @Override
    public void setContent(String content) {
        isLoading = true;
        boolean checked = content != null && content.startsWith("1");
        String text = content == null || content.length() == 0 ? "" : content.substring(1);
        checkBox.setChecked(checked);
        editText.setText(text);
        updateLook();
        isLoading = false;
    }

    @Override
    public String getContent() {
        return (checkBox.isChecked() ? "1" : "0") + editText.getText().toString();
    }

    @Override
    public int getType() {
        return TYPE_CHECKLIST;
    }

    @Override
    public View getView() {
        return rowLayout;
    }

    @Override
    public void setEditMode(boolean isEditMode) {
        editText.setFocusable(isEditMode);
        editText.setFocusableInTouchMode(isEditMode);
        editText.setClickable(isEditMode);
        editText.setEnabled(isEditMode);
        deleteButton.setVisibility(isEditMode ? View.VISIBLE : View.GONE);
        //The box always works: in the viewer it is saved right away (see ToggleListener)
        checkBox.setEnabled(true);
    }

    @Override
    public void setPosition(int position) {
        this.position = position;
        //The photo button and the helper look for this tag on the text that has the cursor
        if (editText.getTag() != null && editText.getTag() instanceof DiaryTextTag) {
            ((DiaryTextTag) editText.getTag()).setPositionTag(position);
        } else {
            editText.setTag(new DiaryTextTag(position));
        }
    }

    @Override
    public int getPosition() {
        return position;
    }
}
