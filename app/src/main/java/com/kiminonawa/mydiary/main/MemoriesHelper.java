package com.kiminonawa.mydiary.main;

import android.content.Context;
import android.database.Cursor;
import android.graphics.RectF;

import com.kiminonawa.mydiary.db.DBManager;
import com.kiminonawa.mydiary.entries.EntryPreview;

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
        //The part of that photo that is seen (fractions of the photo), null for the center
        public RectF thumbnailArea;
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
                    EntryPreview preview = EntryPreview.read(context, dbManager, memory.topicId, memory.diaryId,
                            memory.chosenThumbnail);
                    memory.summary = preview.firstTextSummary(MAX_SUMMARY_LENGTH);
                    memory.thumbnailPath = preview.thumbnailPath;
                    memory.thumbnailArea = preview.thumbnailArea;
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
                best.summary = EntryPreview.read(context, dbManager, best.topicId, best.diaryId, null)
                        .firstTextSummary(MAX_SUMMARY_LENGTH);
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
}
