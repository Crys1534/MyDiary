package com.kiminonawa.mydiary.main;

import android.content.Context;
import android.database.Cursor;

import com.kiminonawa.mydiary.db.DBManager;
import com.kiminonawa.mydiary.entries.diary.item.IDairyRow;
import com.kiminonawa.mydiary.entries.diary.item.RichText;
import com.kiminonawa.mydiary.shared.FileManager;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * Looks for a diary written on this same day (same month and day) in a past year:
 * "a year ago you wrote this".
 */
public class MemoriesHelper {

    private static final int MAX_SUMMARY_LENGTH = 140;

    public static class Memory {
        public long diaryId;
        public long topicId;
        public String topicName;
        public String title;
        public String summary;
        public long time;
        public int yearsAgo;
        public int year;
        //Like in the list of entries: tags (",school,trip,"), weather, mood, attachment and the small picture
        public String tags = "";
        public int weather;
        public int mood;
        public boolean hasAttachment;
        public String chosenThumbnail;
        //Complete path of the photo that is the cover of the entry, null when it has no photo
        public String thumbnailPath;
    }

    /**
     * Every diary written on this same day (month and day) in a past year, the most recent year first.
     *
     * @param withSummary also reads the beginning of the first text of each one (a bit slower)
     */
    public static List<Memory> findToday(Context context, boolean withSummary) {
        List<Memory> memories = new ArrayList<>();
        Calendar now = Calendar.getInstance();
        Calendar written = Calendar.getInstance();
        DBManager dbManager = new DBManager(context);
        try {
            dbManager.opeDB();
            Cursor cursor = dbManager.selectDiaryMemoryCandidates();
            if (cursor != null) {
                for (int i = 0; i < cursor.getCount(); i++) {
                    cursor.moveToPosition(i);
                    written.setTimeInMillis(cursor.getLong(1));
                    int yearsAgo = now.get(Calendar.YEAR) - written.get(Calendar.YEAR);
                    if (yearsAgo > 0
                            && written.get(Calendar.MONTH) == now.get(Calendar.MONTH)
                            && written.get(Calendar.DAY_OF_MONTH) == now.get(Calendar.DAY_OF_MONTH)) {
                        Memory memory = new Memory();
                        memory.diaryId = cursor.getLong(0);
                        memory.time = cursor.getLong(1);
                        memory.title = cursor.getString(2);
                        memory.topicId = cursor.getLong(3);
                        memory.topicName = cursor.getString(4);
                        memory.yearsAgo = yearsAgo;
                        memory.year = written.get(Calendar.YEAR);
                        memory.tags = cursor.getString(5) == null ? "" : cursor.getString(5);
                        memory.weather = cursor.getInt(6);
                        memory.mood = cursor.getInt(7);
                        memory.hasAttachment = cursor.getInt(8) > 0;
                        memory.chosenThumbnail = cursor.getString(9);
                        memories.add(memory);
                    }
                }
                cursor.close();
            }
            if (withSummary) {
                for (Memory memory : memories) {
                    memory.summary = findSummary(dbManager, memory.diaryId);
                    memory.thumbnailPath = findThumbnail(context, dbManager, memory);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                dbManager.closeDB();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return memories;
    }

    /**
     * @return the memory of today (the most recent year first), or null when there is none.
     */
    public static Memory findTodayMemory(Context context) {
        Calendar now = Calendar.getInstance();
        Calendar written = Calendar.getInstance();
        Memory best = null;

        DBManager dbManager = new DBManager(context);
        try {
            dbManager.opeDB();
            Cursor cursor = dbManager.selectDiaryMemoryCandidates();
            if (cursor != null) {
                for (int i = 0; i < cursor.getCount(); i++) {
                    written.setTimeInMillis(cursor.getLong(1));
                    int yearsAgo = now.get(Calendar.YEAR) - written.get(Calendar.YEAR);
                    if (yearsAgo > 0
                            && written.get(Calendar.MONTH) == now.get(Calendar.MONTH)
                            && written.get(Calendar.DAY_OF_MONTH) == now.get(Calendar.DAY_OF_MONTH)
                            && (best == null || yearsAgo < best.yearsAgo)) {
                        best = new Memory();
                        best.diaryId = cursor.getLong(0);
                        best.time = cursor.getLong(1);
                        best.title = cursor.getString(2);
                        best.topicId = cursor.getLong(3);
                        best.topicName = cursor.getString(4);
                        best.yearsAgo = yearsAgo;
                    }
                    cursor.moveToNext();
                }
                cursor.close();
            }
            if (best != null) {
                best.summary = findSummary(dbManager, best.diaryId);
            }
        } catch (Exception e) {
            e.printStackTrace();
            best = null;
        } finally {
            try {
                dbManager.closeDB();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return best;
    }

    /**
     * The photo that is the small picture of the entry: the one chosen as the cover, or else the first photo.
     */
    private static String findThumbnail(Context context, DBManager dbManager, Memory memory) {
        String first = null;
        String chosen = null;
        Cursor content = dbManager.selectDiaryContentByDiaryId(memory.diaryId);
        if (content != null) {
            for (int i = 0; i < content.getCount(); i++) {
                content.moveToPosition(i);
                if (content.getInt(1) == IDairyRow.TYPE_PHOTO) {
                    String file = content.getString(3);
                    if (first == null) {
                        first = file;
                    }
                    if (memory.chosenThumbnail != null && memory.chosenThumbnail.length() > 0
                            && memory.chosenThumbnail.equals(file)) {
                        chosen = file;
                        break;
                    }
                }
            }
            content.close();
        }
        String file = chosen != null ? chosen : first;
        if (file == null) {
            return null;
        }
        return new FileManager(context, memory.topicId, memory.diaryId).getDirAbsolutePath() + "/" + file;
    }

    /**
     * The beginning of the first text of the diary.
     */
    private static String findSummary(DBManager dbManager, long diaryId) {
        String summary = "";
        Cursor content = dbManager.selectDiaryContentByDiaryId(diaryId);
        if (content != null) {
            for (int i = 0; i < content.getCount(); i++) {
                content.moveToPosition(i);
                if (content.getInt(1) == IDairyRow.TYPE_TEXT) {
                    String text = RichText.strip(content.getString(3));
                    if (text != null && text.trim().length() > 0) {
                        text = text.trim();
                        summary = text.substring(0, Math.min(MAX_SUMMARY_LENGTH, text.length()));
                        if (text.length() > MAX_SUMMARY_LENGTH) {
                            summary += "…";
                        }
                        break;
                    }
                }
            }
            content.close();
        }
        return summary;
    }
}
