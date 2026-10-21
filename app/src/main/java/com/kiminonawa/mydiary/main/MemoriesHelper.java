package com.kiminonawa.mydiary.main;

import android.content.Context;
import android.database.Cursor;

import com.kiminonawa.mydiary.db.DBManager;
import com.kiminonawa.mydiary.entries.diary.item.IDairyRow;

import java.util.Calendar;

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
     * The beginning of the first text of the diary.
     */
    private static String findSummary(DBManager dbManager, long diaryId) {
        String summary = "";
        Cursor content = dbManager.selectDiaryContentByDiaryId(diaryId);
        if (content != null) {
            for (int i = 0; i < content.getCount(); i++) {
                content.moveToPosition(i);
                if (content.getInt(1) == IDairyRow.TYPE_TEXT) {
                    String text = content.getString(3);
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
