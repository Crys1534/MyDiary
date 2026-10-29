package com.kiminonawa.mydiary.entries;

import android.content.Context;
import android.database.Cursor;
import android.graphics.RectF;

import com.kiminonawa.mydiary.R;
import com.kiminonawa.mydiary.db.DBManager;
import com.kiminonawa.mydiary.db.DBStructure;
import com.kiminonawa.mydiary.entries.diary.area.PhotoArea;
import com.kiminonawa.mydiary.entries.diary.item.IDairyRow;
import com.kiminonawa.mydiary.entries.diary.item.RichText;
import com.kiminonawa.mydiary.shared.FileManager;

/**
 * What the lists show about an entry besides its title: the small picture (and the part of the photo that is seen)
 * and a piece of the text. It is read from the items of the entry in one single pass.
 * The list of entries, "A day like today" and the search all use it.
 */
public final class EntryPreview {

    /**
     * Complete path of the photo that is the small picture (the one chosen as the cover, or else the first photo);
     * null when the entry has no photo.
     */
    public String thumbnailPath;
    /**
     * The part of the cover photo that is seen (fractions of the photo), null for the center.
     */
    public RectF thumbnailArea;
    /**
     * The entry has at least one item.
     */
    public boolean hasItems;

    private int firstType = -1;
    private String firstContent = "";
    private String firstText = "";

    private EntryPreview() {
    }

    /**
     * @param chosenThumbnail the file name of the photo that the person chose as the cover ("" or null: none)
     */
    public static EntryPreview read(Context context, DBManager dbManager, long topicId, long diaryId,
                                    String chosenThumbnail) {
        EntryPreview preview = new EntryPreview();
        String firstPhoto = null;
        RectF firstPhotoArea = null;
        String chosenPhoto = null;
        RectF chosenPhotoArea = null;
        //The photo that was chosen from the cover (it is not shown inside the entry)
        String coverOnlyPhoto = null;
        RectF coverOnlyArea = null;
        Cursor content = dbManager.selectDiaryContentByDiaryId(diaryId);
        if (content != null) {
            int areaColumn = content.getColumnIndex(DBStructure.DiaryItemEntry_V2.COLUMN_AREA);
            boolean firstSeen = false;
            for (int i = 0; i < content.getCount(); i++) {
                content.moveToPosition(i);
                int type = content.getInt(1);
                String text = content.getString(3);
                PhotoArea photoArea = type == IDairyRow.TYPE_PHOTO && areaColumn >= 0
                        ? PhotoArea.parse(content.getString(areaColumn)) : PhotoArea.NONE;
                preview.hasItems = true;
                //The first thing that is seen in the entry (a photo that is only the cover is not part of the text)
                if (!firstSeen && !photoArea.coverOnly) {
                    firstSeen = true;
                    preview.firstType = type;
                    preview.firstContent = text == null ? "" : text;
                }
                if (type == IDairyRow.TYPE_PHOTO) {
                    RectF cover = photoArea.cover;
                    if (photoArea.coverOnly) {
                        if (coverOnlyPhoto == null) {
                            coverOnlyPhoto = text;
                            coverOnlyArea = cover;
                        }
                    } else if (firstPhoto == null) {
                        firstPhoto = text;
                        firstPhotoArea = cover;
                    }
                    if (chosenPhoto == null && chosenThumbnail != null && chosenThumbnail.length() > 0
                            && chosenThumbnail.equals(text)) {
                        chosenPhoto = text;
                        chosenPhotoArea = cover;
                    }
                } else if (type == IDairyRow.TYPE_TEXT && preview.firstText.length() == 0) {
                    String plain = RichText.strip(text);
                    if (plain != null && plain.trim().length() > 0) {
                        preview.firstText = plain.trim();
                    }
                }
            }
            content.close();
        }
        //The chosen photo, else the photo picked from the cover, else the first photo of the text
        String file = chosenPhoto != null ? chosenPhoto : (coverOnlyPhoto != null ? coverOnlyPhoto : firstPhoto);
        if (file != null) {
            preview.thumbnailPath = new FileManager(context, topicId, diaryId).getDirAbsolutePath() + "/" + file;
            preview.thumbnailArea = chosenPhoto != null ? chosenPhotoArea
                    : (coverOnlyPhoto != null ? coverOnlyArea : firstPhotoArea);
        }
        return preview;
    }

    /**
     * The line under the date in the list of entries: the beginning of the first item
     * ("Photo", the first words of the text, or the first line of a checklist).
     */
    public String listSummary(Context context, int maxLength) {
        if (firstType == IDairyRow.TYPE_PHOTO) {
            return context.getString(R.string.entries_summary_photo);
        }
        if (firstType == IDairyRow.TYPE_TEXT) {
            String plain = RichText.strip(firstContent);
            return plain.substring(0, Math.min(maxLength, plain.length()));
        }
        if (firstType == IDairyRow.TYPE_LINK && firstContent.length() > 0) {
            return com.kiminonawa.mydiary.shared.LinkPreview.domainOf(firstContent);
        }
        if (firstType == IDairyRow.TYPE_CHECKLIST && firstContent.length() > 0) {
            //Checklist line: the first character is "1" (done) or "0" (not done)
            String lineText = RichText.strip(firstContent.substring(1));
            return (firstContent.charAt(0) == '1' ? "[x] " : "[ ] ")
                    + lineText.substring(0, Math.min(maxLength, lineText.length()));
        }
        return "";
    }

    /**
     * The beginning of the first text of the entry (skipping photos), for "A day like today".
     */
    public String firstTextSummary(int maxLength) {
        if (firstText.length() <= maxLength) {
            return firstText;
        }
        return firstText.substring(0, maxLength) + "…";
    }
}
