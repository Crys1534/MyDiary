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
    //File name of the photo that the person chose as the cover ("" = nothing chosen: the first photo)
    private String thumbnailFileName = "";


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
        thumbnailFileName = "";
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


    /**
     * The photo that the person chose as the cover (the file name saved in the database).
     */
    public void setThumbnailFileName(String fileName) {
        this.thumbnailFileName = fileName == null ? "" : fileName;
    }

    /**
     * What has to be saved as the chosen cover: the chosen file if it is still in the diary, else "".
     */
    public String getChosenThumbnail() {
        if (thumbnailFileName.length() > 0) {
            for (IDairyRow row : diaryItemList) {
                if (row instanceof DiaryPhoto && thumbnailFileName.equals(row.getContent())) {
                    return thumbnailFileName;
                }
            }
        }
        return "";
    }

    /**
     * The photo that is the cover now: the chosen one, or the first photo of the diary.
     *
     * @return the photo row, or null when the diary has no photos
     */
    public DiaryPhoto getThumbnailPhoto() {
        String chosen = getChosenThumbnail();
        DiaryPhoto first = null;
        for (IDairyRow row : diaryItemList) {
            if (row instanceof DiaryPhoto) {
                if (first == null) {
                    first = (DiaryPhoto) row;
                }
                if (chosen.length() > 0 && chosen.equals(row.getContent())) {
                    return (DiaryPhoto) row;
                }
            }
        }
        return first;
    }

    /**
     * Address of the photo that is the cover now, or null when the diary has no photos.
     */
    public String getThumbnailUriString() {
        DiaryPhoto photo = getThumbnailPhoto();
        return photo == null ? null : photo.getPhotoUriString();
    }

    /**
     * The star of a photo was pressed: that photo is the cover now.
     */
    public void chooseThumbnailAt(int position) {
        if (position >= 0 && position < diaryItemList.size() && diaryItemList.get(position) instanceof DiaryPhoto) {
            thumbnailFileName = diaryItemList.get(position).getContent();
        }
        refreshThumbnailMarks();
    }

    /**
     * Fills the star of the photo that is the cover (the others are empty).
     */
    public void refreshThumbnailMarks() {
        DiaryPhoto current = getThumbnailPhoto();
        for (IDairyRow row : diaryItemList) {
            if (row instanceof DiaryPhoto) {
                ((DiaryPhoto) row).setThumbnailSelected(row == current);
            }
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
        return toggleStyleAtFocus(focusView, false);
    }

    /**
     * The I button: same as the B button, but italic (it can be combined with bold).
     */
    public boolean toggleItalicAtFocus(View focusView) {
        return toggleStyleAtFocus(focusView, true);
    }

    private boolean toggleStyleAtFocus(View focusView, boolean italic) {
        if (findRowByEditText(focusView) < 0 || !(focusView instanceof FormatEditText)) {
            return false;
        }
        FormatEditText editText = (FormatEditText) focusView;
        //The selected text, or the text that was selected before the keyboard was closed
        int[] range = editText.getFormatRange();
        if (range == null) {
            return false;
        }
        if (italic) {
            RichText.toggleItalic(editText.getText(), range[0], range[1]);
        } else {
            RichText.toggleBold(editText.getText(), range[0], range[1]);
        }
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

    /**
     * The audio button: puts an audio (a link) where the cursor is.
     * The text after the cursor goes below the audio. After the audio there is always a text box.
     *
     * @param focusView the view that has the focus (the text box with the cursor), can be null
     */
    public DiaryAudio insertAudioAtFocus(View focusView, String link, View.OnClickListener deleteListener) {
        Context context = itemContentLayout.getContext();
        int rowIndex = findRowByEditText(focusView);
        DiaryAudio audio = new DiaryAudio(context);
        audio.setContent(link);
        audio.setEditMode(true);
        audio.setDeleteClickListener(deleteListener);
        String rest = "";
        int insertAt;

        if (rowIndex >= 0 && diaryItemList.get(rowIndex) instanceof DiaryText) {
            EditText editText = (EditText) focusView;
            Editable text = editText.getText();
            int index = Math.max(0, Math.min(editText.getSelectionStart(), text.length()));
            rest = RichText.toMarkup(text.subSequence(index, text.length()));
            text.delete(index, text.length());
            insertAt = rowIndex + 1;
            if (text.length() == 0) {
                //The empty text box would stay as a blank line, the audio takes its place
                removeRowAndView(rowIndex);
                insertAt = rowIndex;
            }
        } else if (rowIndex >= 0) {
            //In a checklist line: the audio goes below it
            insertAt = rowIndex + 1;
        } else {
            insertAt = diaryItemList.size();
        }
        createItem(audio, insertAt);
        DiaryText after = new DiaryText(context);
        after.setContent(rest);
        createItem(after, insertAt + 1);
        resortPosition();
        return audio;
    }

    /**
     * Stops every audio and frees the players (the page goes away).
     */
    public void releaseAudio() {
        for (IDairyRow row : diaryItemList) {
            if (row instanceof DiaryAudio) {
                ((DiaryAudio) row).release();
            }
        }
    }

    /**
     * Pauses every audio (the screen goes to the background).
     */
    public void pauseAudio() {
        for (IDairyRow row : diaryItemList) {
            if (row instanceof DiaryAudio) {
                ((DiaryAudio) row).pause();
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
