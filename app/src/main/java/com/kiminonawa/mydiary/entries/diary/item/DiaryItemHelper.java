package com.kiminonawa.mydiary.entries.diary.item;

import android.content.Context;
import android.text.Editable;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;

import com.kiminonawa.mydiary.R;
import com.kiminonawa.mydiary.shared.ScreenHelper;
import com.kiminonawa.mydiary.shared.statusbar.ChinaPhoneHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Observable;

/**
 * Created by daxia on 2016/11/19.
 */

public class DiaryItemHelper extends Observable {

    //Original limit was 7 photos per diary; removed so a diary can hold unlimited photos.
    public final static int MAX_PHOTO_COUNT = Integer.MAX_VALUE;


    //For test to Public
    private List<IDairyRow> diaryItemList;
    private LinearLayout itemContentLayout;
    private int nowPhotoCount = 0;


    public DiaryItemHelper(LinearLayout itemContentLayout) {
        this.itemContentLayout = itemContentLayout;
        this.diaryItemList = new ArrayList<>();
    }

    /**
     * Make all item < 1 screen, so It should be computed show area.
     * The height & Width is fixed value for a device.
     */
    public static int getVisibleHeight(Context context) {
        int topbarHeight = context.getResources().getDimensionPixelOffset(R.dimen.top_bar_height);
        int imageHeight;
        if (ChinaPhoneHelper.getDeviceStatusBarType() == ChinaPhoneHelper.OTHER) {
            imageHeight = ScreenHelper.getScreenHeight(context)
                    - ScreenHelper.getStatusBarHeight(context)
                    //diary activity top bar  -( diary info + diary bottom bar + diary padding+ photo padding)
                    - topbarHeight - ScreenHelper.dpToPixel(context.getResources(), 120 + 40 + (2 * 5) + (2 * 5));
        } else {
            imageHeight = ScreenHelper.getScreenHeight(context)
                    //diary activity top bar  -( diary info + diary bottom bar + diary padding + photo padding)
                    - topbarHeight - ScreenHelper.dpToPixel(context.getResources(), 120 + 40 + (2 * 5) + (2 * 5));
        }
        return imageHeight;
    }

    public static int getVisibleWidth(Context context) {
        int imageWeight = ScreenHelper.getScreenWidth(context) -
                //(diary padding + photo padding)
                ScreenHelper.dpToPixel(context.getResources(), (2 * 5) + (2 * 5));
        return imageWeight;
    }

    public void initDiary() {
        //Remove old data
        itemContentLayout.removeAllViews();
        diaryItemList.clear();
        nowPhotoCount = 0;
        setChanged();
        notifyObservers();
    }

    //TODO 鬆綁list and add view , 再最後再實作一個一次新增
    public void createItem(IDairyRow diaryItem) {
        if (diaryItem instanceof DiaryPhoto) {
            nowPhotoCount++;
        }
        diaryItemList.add(diaryItem);
        itemContentLayout.addView(diaryItemList.get(diaryItemList.size() - 1).getView());
        if (diaryItemList.size() == 1) {
            setChanged();
            notifyObservers();
        }
    }

    public void createItem(IDairyRow diaryItem, int position) {
        if (diaryItem instanceof DiaryPhoto) {
            nowPhotoCount++;
        }
        diaryItemList.add(position, diaryItem);
        itemContentLayout.addView(diaryItem.getView(), position);
        if (diaryItemList.size() == 1) {
            setChanged();
            notifyObservers();
        }

    }


    public int getNowPhotoCount() {
        return nowPhotoCount;
    }

    public int getItemSize() {
        return diaryItemList.size();
    }

    public IDairyRow get(int position) {
        return diaryItemList.get(position);
    }

    public void remove(int position) {
        if (diaryItemList.get(position) instanceof DiaryPhoto) {
            nowPhotoCount--;
        }
        diaryItemList.remove(position);
        if (diaryItemList.size() == 0) {
            setChanged();
            notifyObservers();
        }
    }

    public void resortPosition() {
        for (int i = 0; i < diaryItemList.size(); i++) {
            diaryItemList.get(i).setPosition(i);
        }
    }

    /**
     * The B button: makes the selected text of the box with the cursor bold (or normal again).
     *
     * @return false when there was no text selected.
     */
    public boolean toggleBoldAtFocus(View focusView) {
        if (findRowByEditText(focusView) < 0) {
            return false;
        }
        EditText editText = (EditText) focusView;
        int start = editText.getSelectionStart();
        int end = editText.getSelectionEnd();
        if (start < 0 || end < 0 || start == end) {
            return false;
        }
        RichText.toggleBold(editText.getText(), start, end);
        return true;
    }

    /*
     * Checklist
     */

    /**
     * Which row owns this text box (the one that has the cursor)? -1 if none.
     */
    private int findRowByEditText(View editText) {
        if (editText == null) {
            return -1;
        }
        for (int i = 0; i < diaryItemList.size(); i++) {
            IDairyRow row = diaryItemList.get(i);
            if (row instanceof DiaryText && row.getView() == editText) {
                return i;
            }
            if (row instanceof DiaryChecklist && ((DiaryChecklist) row).getEditText() == editText) {
                return i;
            }
        }
        return -1;
    }

    private int indexOfRow(IDairyRow row) {
        return diaryItemList.indexOf(row);
    }

    private void removeRowAndView(int position) {
        remove(position);
        itemContentLayout.removeViewAt(position);
    }

    private void focusAndShowKeyboard(EditText editText, boolean cursorAtEnd) {
        editText.requestFocus();
        editText.setSelection(cursorAtEnd ? editText.getText().length() : 0);
        InputMethodManager imm = (InputMethodManager)
                editText.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    /**
     * The checklist button: puts a new checklist line where the cursor is.
     * The text after the cursor goes below the line.
     *
     * @param focusView the view that has the focus (the text box with the cursor), can be null
     */
    public void insertChecklistAtFocus(View focusView) {
        Context context = itemContentLayout.getContext();
        int rowIndex = findRowByEditText(focusView);
        DiaryChecklist item = new DiaryChecklist(context, this);
        item.setContent("0");

        if (rowIndex >= 0 && diaryItemList.get(rowIndex) instanceof DiaryText) {
            //Cut the text at the cursor: the second part goes after the new line
            EditText editText = (EditText) focusView;
            Editable text = editText.getText();
            int index = Math.max(0, Math.min(editText.getSelectionStart(), text.length()));
            String rest = RichText.toMarkup(text.subSequence(index, text.length()));
            text.delete(index, text.length());
            int insertAt = rowIndex + 1;
            if (text.length() == 0) {
                //The empty text box would stay as a blank line, the checklist takes its place
                removeRowAndView(rowIndex);
                insertAt = rowIndex;
            }
            createItem(item, insertAt);
            if (rest.length() > 0) {
                DiaryText restText = new DiaryText(context);
                restText.setContent(rest);
                createItem(restText, insertAt + 1);
            }
        } else if (rowIndex >= 0) {
            //The cursor is in a checklist line: a new line below it
            splitChecklistLine((DiaryChecklist) diaryItemList.get(rowIndex), rowIndex, item);
        } else {
            //No cursor: at the end
            createItem(item);
        }
        resortPosition();
        focusAndShowKeyboard(item.getEditText(), false);
    }

    /**
     * Cuts the text of the line at the cursor and puts the second part in newItem, below it.
     */
    private void splitChecklistLine(DiaryChecklist line, int lineIndex, DiaryChecklist newItem) {
        EditText editText = line.getEditText();
        Editable text = editText.getText();
        int index = Math.max(0, Math.min(editText.getSelectionStart(), text.length()));
        String rest = text.subSequence(index, text.length()).toString();
        text.delete(index, text.length());
        newItem.setContent("0" + rest);
        createItem(newItem, lineIndex + 1);
    }

    /**
     * Enter in a checklist line: a new line below. Enter in an empty line: the list ends.
     */
    public void onChecklistEnter(DiaryChecklist line) {
        int lineIndex = indexOfRow(line);
        if (lineIndex < 0) {
            return;
        }
        Context context = itemContentLayout.getContext();
        if (line.getEditText().getText().toString().trim().length() == 0) {
            //The line becomes normal text
            removeRowAndView(lineIndex);
            DiaryText text = new DiaryText(context);
            createItem(text, lineIndex);
            resortPosition();
            focusAndShowKeyboard((EditText) text.getView(), false);
        } else {
            DiaryChecklist next = new DiaryChecklist(context, this);
            splitChecklistLine(line, lineIndex, next);
            resortPosition();
            focusAndShowKeyboard(next.getEditText(), false);
        }
    }

    /**
     * Removes a checklist line (the X button, or backspace in an empty line).
     */
    public void removeChecklist(DiaryChecklist line, boolean moveCursorToPrevious) {
        int lineIndex = indexOfRow(line);
        if (lineIndex < 0) {
            return;
        }
        removeRowAndView(lineIndex);
        resortPosition();
        if (moveCursorToPrevious && lineIndex > 0) {
            IDairyRow previous = diaryItemList.get(lineIndex - 1);
            if (previous instanceof DiaryText) {
                focusAndShowKeyboard((EditText) previous.getView(), true);
            } else if (previous instanceof DiaryChecklist) {
                focusAndShowKeyboard(((DiaryChecklist) previous).getEditText(), true);
            }
        }
    }

    public void mergerAdjacentText(int position) {
        if (diaryItemList.size() > 0 && position < diaryItemList.size()
                && diaryItemList.get(position).getType() == IDairyRow.TYPE_TEXT) {
            if (position != 0 && diaryItemList.get(position - 1).getType() == IDairyRow.TYPE_TEXT) {
                //First Item
                String mergerStr = diaryItemList.get(position).getContent();
                ((DiaryText) diaryItemList.get(position - 1)).insertText(mergerStr);
                itemContentLayout.removeViewAt(position);
                diaryItemList.remove(position);
            }
        }
    }

}
