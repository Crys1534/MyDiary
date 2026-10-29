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
        if (isShownPhoto(diaryItem)) {
            nowPhotoCount++;
        }
        diaryItemList.add(diaryItem);
        itemContentLayout.addView(diaryItemList.get(diaryItemList.size() - 1).getView());
        setChanged();
        notifyObservers();
    }

    public void createItem(IDairyRow diaryItem, int position) {
        if (isShownPhoto(diaryItem)) {
            nowPhotoCount++;
        }
        diaryItemList.add(position, diaryItem);
        itemContentLayout.addView(diaryItem.getView(), position);
        setChanged();
        notifyObservers();
    }

    /**
     * A photo that is seen between the paragraphs (the photo that is only the cover is not counted: it is not an
     * attachment of the text).
     */
    private static boolean isShownPhoto(IDairyRow row) {
        return row instanceof DiaryPhoto && !((DiaryPhoto) row).isCoverOnly();
    }

    private static boolean isCoverOnlyPhoto(IDairyRow row) {
        return row instanceof DiaryPhoto && ((DiaryPhoto) row).isCoverOnly();
    }

    /**
     * How many rows the person sees and writes in (the photo that is only the cover has no place in the text).
     */
    public int getContentItemCount() {
        int count = 0;
        for (IDairyRow row : diaryItemList) {
            if (!isCoverOnlyPhoto(row)) {
                count++;
            }
        }
        return count;
    }

    /**
     * The last row that is seen in the entry, or null when there is none.
     */
    public IDairyRow getLastContentRow() {
        for (int i = diaryItemList.size() - 1; i >= 0; i--) {
            if (!isCoverOnlyPhoto(diaryItemList.get(i))) {
                return diaryItemList.get(i);
            }
        }
        return null;
    }

    /**
     * The photo that is only the cover (it was chosen from the cover, it is not in the text), or null.
     */
    public DiaryPhoto getCoverOnlyPhoto() {
        for (IDairyRow row : diaryItemList) {
            if (isCoverOnlyPhoto(row)) {
                return (DiaryPhoto) row;
            }
        }
        return null;
    }

    /**
     * The person chose a photo from the cover button: it becomes the cover and it is not shown in the text.
     * The photo that was the cover before (if it was the same kind) is taken out of the entry.
     *
     * @param photo a photo row with {@code PhotoArea.coverOnly} already set
     */
    public void setCoverOnlyPhoto(DiaryPhoto photo) {
        removeCoverOnlyRows();
        //First place: it is not seen, so it never gets between the paragraphs
        createItem(photo, 0);
        thumbnailFileName = photo.getContent();
        resortPosition();
        refreshThumbnailMarks();
    }

    /**
     * Takes away the photo that is only the cover. The cover goes back to the first photo of the text (if any).
     */
    public void removeCoverOnlyPhoto() {
        removeCoverOnlyRows();
        thumbnailFileName = "";
        resortPosition();
        refreshThumbnailMarks();
    }

    private void removeCoverOnlyRows() {
        for (int i = diaryItemList.size() - 1; i >= 0; i--) {
            if (isCoverOnlyPhoto(diaryItemList.get(i))) {
                removeRowAndView(i);
            }
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
     * The part of the cover photo that is seen on the cover (3:4), or null for the center.
     */
    public android.graphics.RectF getThumbnailCoverArea() {
        DiaryPhoto photo = getThumbnailPhoto();
        return photo == null ? null : photo.getArea().cover;
    }

    /**
     * The photo that is the cover now: the chosen one, or the first photo of the diary.
     *
     * @return the photo row, or null when the diary has no photos
     */
    public DiaryPhoto getThumbnailPhoto() {
        String chosen = getChosenThumbnail();
        DiaryPhoto first = null;
        DiaryPhoto coverOnly = null;
        for (IDairyRow row : diaryItemList) {
            if (row instanceof DiaryPhoto) {
                DiaryPhoto photo = (DiaryPhoto) row;
                if (photo.isCoverOnly()) {
                    if (coverOnly == null) {
                        coverOnly = photo;
                    }
                } else if (first == null) {
                    first = photo;
                }
                if (chosen.length() > 0 && chosen.equals(row.getContent())) {
                    return photo;
                }
            }
        }
        //Nothing chosen: the photo that was picked from the cover, else the first photo of the text
        return coverOnly != null ? coverOnly : first;
    }

    /**
     * The photo item with this file name, or null.
     */
    public DiaryPhoto findPhoto(String fileName) {
        if (fileName == null) {
            return null;
        }
        for (IDairyRow row : diaryItemList) {
            if (row instanceof DiaryPhoto && fileName.equals(row.getContent())) {
                return (DiaryPhoto) row;
            }
        }
        return null;
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
            //A photo of the text is the cover now: the photo that was picked only for the cover is not needed any more
            if (getCoverOnlyPhoto() != null && !thumbnailFileName.equals(getCoverOnlyPhoto().getContent())) {
                removeCoverOnlyRows();
                resortPosition();
            }
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

    /**
     * What has to be saved in the database for the item: for a photo, the part of it that is seen
     * (empty when nothing was chosen); other items have nothing.
     */
    public String getAreaTextAt(int position) {
        IDairyRow row = diaryItemList.get(position);
        return row instanceof DiaryPhoto ? ((DiaryPhoto) row).getArea().toString() : "";
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
        if (isShownPhoto(diaryItemList.get(position))) {
            nowPhotoCount--;
        }
        diaryItemList.remove(position);
        setChanged();
        notifyObservers();
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

    /**
     * The list button: bullets, numbered list or quote for the line(s) with the cursor of the box that has focus.
     *
     * @param mark ListFormat.BULLET / NUMBER / QUOTE, or 0 to take it away
     * @return false when no text box has the cursor
     */
    public boolean applyListAtFocus(View focusView, char mark) {
        if (!(focusView instanceof FormatEditText) || findRowByEditText(focusView) < 0) {
            return false;
        }
        return ((FormatEditText) focusView).applyListFormat(mark);
    }

    /**
     * Small menu over the list button: Bullets / Numbered list / Quote / Remove.
     */
    public void showListMenu(final Context context, View anchor, final View focusView) {
        if (!(focusView instanceof FormatEditText) || findRowByEditText(focusView) < 0) {
            android.widget.Toast.makeText(context, R.string.toast_list_tap_text, android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        android.widget.PopupMenu menu = new android.widget.PopupMenu(context, anchor);
        menu.getMenu().add(0, 1, 0, R.string.list_menu_bullets);
        menu.getMenu().add(0, 2, 1, R.string.list_menu_numbers);
        menu.getMenu().add(0, 3, 2, R.string.list_menu_quote);
        menu.getMenu().add(0, 4, 3, R.string.list_menu_none);
        menu.setOnMenuItemClickListener(new android.widget.PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(android.view.MenuItem item) {
                char mark;
                switch (item.getItemId()) {
                    case 1:
                        mark = ListFormat.BULLET;
                        break;
                    case 2:
                        mark = ListFormat.NUMBER;
                        break;
                    case 3:
                        mark = ListFormat.QUOTE;
                        break;
                    default:
                        mark = 0;
                        break;
                }
                applyListAtFocus(focusView, mark);
                focusView.requestFocus();
                return true;
            }
        });
        menu.show();
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
