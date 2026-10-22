package com.kiminonawa.mydiary.entries.diary.editor;

import android.app.Activity;

import com.kiminonawa.mydiary.entries.diary.area.PhotoArea;
import com.kiminonawa.mydiary.entries.diary.item.DiaryAudio;
import com.kiminonawa.mydiary.entries.diary.item.DiaryChecklist;
import com.kiminonawa.mydiary.entries.diary.item.DiaryItemHelper;
import com.kiminonawa.mydiary.entries.diary.item.DiaryPhoto;
import com.kiminonawa.mydiary.entries.diary.item.DiaryText;
import com.kiminonawa.mydiary.entries.diary.item.IDairyRow;
import com.kiminonawa.mydiary.shared.FileManager;

/**
 * Makes the rows of an entry (text, photo, audio, checklist) from what the database, the backup or the saved draft
 * has. The window that reads/edits an entry and the page that writes a new one use the same code.
 */
public final class DiaryItemFactory {

    private DiaryItemFactory() {
    }

    /**
     * @param type        one of the types of {@link IDairyRow}
     * @param content     what was saved (for a photo: the file name)
     * @param area        photos: the part that is seen (see PhotoArea text), null or "" for the center
     * @param photoFolder complete path of the folder where the photo files of the entry are
     * @param editMode    true when the person can change the entry
     * @param actions     the buttons of the rows (delete, choose the cover, choose what part is seen)
     * @return the row, or null when the type is not known (it is skipped)
     */
    public static IDairyRow create(Activity activity, DiaryItemHelper helper, int type, String content, String area,
                                   String photoFolder, boolean editMode, EditorActions actions) {
        IDairyRow row;
        switch (type) {
            case IDairyRow.TYPE_PHOTO:
                DiaryPhoto photo = new DiaryPhoto(activity);
                //For get the right file name (also when only reading: the cover is found by it)
                photo.setPhotoFileName(content);
                photo.setArea(PhotoArea.parse(area));
                photo.setEditMode(editMode);
                if (editMode) {
                    photo.setDeleteClickListener(actions);
                    photo.setThumbnailClickListener(actions);
                    photo.setCropClickListener(actions);
                }
                photo.setContent(FileManager.FILE_HEADER + photoFolder + "/" + content);
                return photo;
            case IDairyRow.TYPE_TEXT:
                row = new DiaryText(activity);
                row.setEditMode(editMode);
                break;
            case IDairyRow.TYPE_AUDIO:
                DiaryAudio audio = new DiaryAudio(activity);
                audio.setEditMode(editMode);
                if (editMode) {
                    audio.setDeleteClickListener(actions);
                }
                row = audio;
                break;
            case IDairyRow.TYPE_CHECKLIST:
                row = new DiaryChecklist(activity, helper);
                row.setEditMode(editMode);
                break;
            default:
                return null;
        }
        row.setContent(content);
        return row;
    }
}
