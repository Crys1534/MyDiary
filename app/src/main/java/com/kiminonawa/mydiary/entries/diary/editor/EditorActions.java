package com.kiminonawa.mydiary.entries.diary.editor;

import android.app.Activity;
import android.net.Uri;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.kiminonawa.mydiary.R;
import com.kiminonawa.mydiary.entries.diary.AudioLinkDialog;
import com.kiminonawa.mydiary.entries.diary.area.AreaPickerController;
import com.kiminonawa.mydiary.entries.diary.item.DiaryAudio;
import com.kiminonawa.mydiary.entries.diary.item.DiaryItemHelper;
import com.kiminonawa.mydiary.entries.diary.item.DiaryPhoto;
import com.kiminonawa.mydiary.entries.diary.item.DiaryText;
import com.kiminonawa.mydiary.entries.diary.item.DiaryTextTag;
import com.kiminonawa.mydiary.entries.diary.item.IDairyRow;
import com.kiminonawa.mydiary.entries.diary.item.RichText;
import com.kiminonawa.mydiary.shared.FileManager;

import java.io.File;

/**
 * What the buttons do while an entry is written: the bar of the editor (bold, italic, lists, checklist, audio) and the
 * buttons of the rows (delete a photo or an audio, choose the cover, choose what part of a photo is seen).
 * The page that writes a new entry and the window that edits an old one share it.
 */
public class EditorActions implements View.OnClickListener {

    public interface Host {
        Activity getEditorActivity();

        /**
         * The view with the cursor (the text box that is being written).
         */
        View getFocusView();

        /**
         * The folder where the photo files of the entry that is being written are.
         */
        File getPhotoFolder();

        /**
         * The photos or the cover changed: the cover has to be drawn again.
         */
        void onCoverChanged();
    }

    private static final String TAG = "EditorActions";

    private final Host host;
    private final DiaryItemHelper diaryItemHelper;
    private final LinearLayout itemContainer;
    private final AreaPickerController areaPicker;

    public EditorActions(Host host, DiaryItemHelper diaryItemHelper, LinearLayout itemContainer,
                         AreaPickerController areaPicker) {
        this.host = host;
        this.diaryItemHelper = diaryItemHelper;
        this.itemContainer = itemContainer;
        this.areaPicker = areaPicker;
    }

    /**
     * Shows the buttons of the editor bar (they are hidden when only reading) and listens to them.
     */
    public void bindBar(View rootView) {
        int[] ids = {R.id.IV_diary_bold, R.id.IV_diary_italic, R.id.IV_diary_list,
                R.id.IV_diary_checklist, R.id.IV_diary_audio};
        for (int id : ids) {
            View button = rootView.findViewById(id);
            button.setVisibility(View.VISIBLE);
            button.setOnClickListener(this);
        }
    }

    @Override
    public void onClick(View v) {
        Activity activity = host.getEditorActivity();
        switch (v.getId()) {
            case R.id.IV_diary_bold:
                if (!diaryItemHelper.toggleBoldAtFocus(host.getFocusView())) {
                    Toast.makeText(activity, activity.getString(R.string.toast_format_select_text), Toast.LENGTH_SHORT).show();
                }
                break;
            case R.id.IV_diary_italic:
                if (!diaryItemHelper.toggleItalicAtFocus(host.getFocusView())) {
                    Toast.makeText(activity, activity.getString(R.string.toast_format_select_text), Toast.LENGTH_SHORT).show();
                }
                break;
            case R.id.IV_diary_list:
                diaryItemHelper.showListMenu(activity, v, host.getFocusView());
                break;
            case R.id.IV_diary_checklist:
                diaryItemHelper.insertChecklistAtFocus(host.getFocusView());
                break;
            case R.id.IV_diary_audio:
                AudioLinkDialog.show(activity, new AudioLinkDialog.Callback() {
                    @Override
                    public void onAudioLink(String link) {
                        diaryItemHelper.insertAudioAtFocus(host.getFocusView(), link, EditorActions.this);
                    }
                });
                break;
            case R.id.IV_diary_audio_delete:
                int audioPosition = (int) v.getTag();
                if (diaryItemHelper.get(audioPosition) instanceof DiaryAudio) {
                    ((DiaryAudio) diaryItemHelper.get(audioPosition)).release();
                }
                removeRow(audioPosition);
                break;
            case R.id.IV_diary_photo_delete:
                removeRow((int) v.getTag());
                host.onCoverChanged();
                break;
            case R.id.IV_diary_photo_crop:
                IDairyRow row = diaryItemHelper.get((int) v.getTag());
                if (row instanceof DiaryPhoto) {
                    areaPicker.pickForBody((DiaryPhoto) row, host.getPhotoFolder());
                }
                break;
            case R.id.IV_diary_photo_thumbnail:
                diaryItemHelper.chooseThumbnailAt((int) v.getTag());
                host.onCoverChanged();
                Toast.makeText(activity, activity.getString(R.string.cover_thumbnail_chosen), Toast.LENGTH_SHORT).show();
                break;
            default:
                break;
        }
    }

    private void removeRow(int position) {
        diaryItemHelper.remove(position);
        itemContainer.removeViewAt(position);
        diaryItemHelper.mergerAdjacentText(position);
        diaryItemHelper.resortPosition();
    }

    /**
     * Adds a photo to the entry: at the cursor (the text is split in two) or at the end.
     *
     * @param fileName       the photo file (it is already in the folder)
     * @param folderAbsolute complete path of that folder
     */
    public void insertPhoto(String fileName, String folderAbsolute) {
        Activity activity = host.getEditorActivity();
        try {
            DiaryPhoto diaryPhoto = new DiaryPhoto(activity);
            diaryPhoto.setPhoto(Uri.parse(FileManager.FILE_HEADER + folderAbsolute + "/" + fileName), fileName);
            diaryPhoto.setDeleteClickListener(this);
            diaryPhoto.setThumbnailClickListener(this);
            diaryPhoto.setCropClickListener(this);
            DiaryTextTag tag = cutTextAtCursor();
            //Check edittext is focused
            if (tag != null) {
                //The text after the cursor goes to a new text box under the photo
                DiaryText diaryText = new DiaryText(activity);
                diaryText.setPosition(tag.getPositionTag());
                diaryText.setContent(tag.getNextEditTextStr());
                diaryItemHelper.createItem(diaryText, tag.getPositionTag() + 1);
                diaryText.getView().requestFocus();
                diaryPhoto.setPosition(tag.getPositionTag() + 1);
                diaryItemHelper.createItem(diaryPhoto, tag.getPositionTag() + 1);
            } else {
                diaryPhoto.setPosition(diaryItemHelper.getItemSize());
                diaryItemHelper.createItem(diaryPhoto);
                //A new text box under the photo
                DiaryText diaryText = new DiaryText(activity);
                diaryText.setPosition(diaryItemHelper.getItemSize());
                diaryItemHelper.createItem(diaryText);
                diaryText.getView().requestFocus();
            }
        } catch (Exception e) {
            Log.e(TAG, e.toString());
            Toast.makeText(activity, activity.getString(R.string.toast_photo_path_error), Toast.LENGTH_LONG).show();
        } finally {
            diaryItemHelper.resortPosition();
            host.onCoverChanged();
        }
    }

    /**
     * Takes the text after the cursor out of the text box that has the cursor (it will go below the new photo).
     *
     * @return the tag of that text box (with the cut text), or null when no text box has the cursor
     */
    private DiaryTextTag cutTextAtCursor() {
        View focusView = host.getFocusView();
        DiaryTextTag tag = null;
        if (focusView instanceof EditText && focusView.getTag() != null
                && focusView.getTag() instanceof DiaryTextTag) {
            EditText currentEditText = (EditText) focusView;
            tag = (DiaryTextTag) focusView.getTag();
            //Forget the text of a previous photo, or it would be added again
            tag.setNextEditTextStr(null);
            if (currentEditText.getText().toString().length() > 0) {
                int index = currentEditText.getSelectionStart();
                //Keep the bold of the text that goes to the next text box
                String nextEditTextStr = RichText.toMarkup(currentEditText.getText()
                        .subSequence(index, currentEditText.getText().length()));
                currentEditText.getText().delete(index, currentEditText.getText().length());
                tag.setNextEditTextStr(nextEditTextStr);
            }
        }
        return tag;
    }
}
