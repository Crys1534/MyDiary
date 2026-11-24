package com.kiminonawa.mydiary.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.util.Log;

import static com.kiminonawa.mydiary.db.DBStructure.ContactsEntry;
import static com.kiminonawa.mydiary.db.DBStructure.DiaryEntry_V2;
import static com.kiminonawa.mydiary.db.DBStructure.DiaryItemEntry_V2;
import static com.kiminonawa.mydiary.db.DBStructure.MemoEntry;
import static com.kiminonawa.mydiary.db.DBStructure.MemoOrderEntry;
import static com.kiminonawa.mydiary.db.DBStructure.TopicEntry;
import static com.kiminonawa.mydiary.db.DBStructure.TopicOrderEntry;

/**
 * Created by daxia on 2016/4/2.
 */
public class DBManager {


    //TODO add SQLiteException
    private Context context;
    private SQLiteDatabase db;
    private DBHelper mDBHelper;

    public DBManager(Context context) {
        this.context = context;
    }

    public DBManager(SQLiteDatabase db) {
        this.db = db;
    }

    /*
     * DB IO
     */

    public void opeDB() throws SQLiteException {
        mDBHelper = new DBHelper(context);
        // Gets the data repository in write mode
        this.db = mDBHelper.getWritableDatabase();
    }


    public void closeDB() {
        mDBHelper.close();
    }


    public void beginTransaction() {
        db.beginTransaction();
    }

    public void setTransactionSuccessful() {
        db.setTransactionSuccessful();
    }

    public void endTransaction() {
        db.endTransaction();
    }

    /*
     * Topic
     */

    public long insertTopic(String name, int type, int color) {
        return insertTopic(name, type, color, 0);
    }

    public long insertTopic(String name, int type, int color, int theme) {
        ContentValues values = this.createTopicCV(name, type, color);
        values.put(TopicEntry.COLUMN_THEME, theme);
        return db.insert(
                TopicEntry.TABLE_NAME,
                null,
                values);
    }

    /**
     * The look chosen for the category (0 = classic).
     */
    public int getTopicTheme(long topicId) {
        Cursor c = db.rawQuery("SELECT " + TopicEntry.COLUMN_THEME + " FROM " + TopicEntry.TABLE_NAME
                + " WHERE " + TopicEntry._ID + " = ?", new String[]{String.valueOf(topicId)});
        try {
            return c.moveToFirst() ? c.getInt(0) : 0;
        } finally {
            c.close();
        }
    }

    public long updateTopicTheme(long topicId, int theme) {
        ContentValues values = new ContentValues();
        values.put(TopicEntry.COLUMN_THEME, theme);
        return db.update(
                TopicEntry.TABLE_NAME,
                values,
                TopicEntry._ID + " = ?",
                new String[]{String.valueOf(topicId)});
    }

    public long insertTopicOrder(long topicId, long order) {
        ContentValues values = new ContentValues();
        values.put(TopicOrderEntry.COLUMN_ORDER, order);
        values.put(TopicOrderEntry.COLUMN_REF_TOPIC__ID, topicId);
        return db.insert(
                TopicOrderEntry.TABLE_NAME,
                null,
                values);
    }

    public long updateTopic(long topicId, String name, int color) {
        ContentValues values = new ContentValues();
        values.put(TopicEntry.COLUMN_NAME, name);
        values.put(TopicEntry.COLUMN_COLOR, color);
        return db.update(
                TopicEntry.TABLE_NAME,
                values,
                TopicEntry._ID + " = ?",
                new String[]{String.valueOf(topicId)});
    }

    /**
     * Select Topic & order for show in Topic list
     *
     * @return
     */
    public Cursor selectTopic() {
        Cursor c = db.rawQuery("SELECT * FROM " + TopicEntry.TABLE_NAME
                        + " LEFT OUTER JOIN " + TopicOrderEntry.TABLE_NAME
                        + " ON " + TopicEntry._ID + " = " + TopicOrderEntry.COLUMN_REF_TOPIC__ID
                        + " ORDER BY " + TopicOrderEntry.COLUMN_ORDER + " DESC "
                , null);
        if (c != null) {
            c.moveToFirst();
        }
        return c;
    }

    public long deleteAllCurrentTopicOrder() {
        return db.delete(
                TopicOrderEntry.TABLE_NAME,
                null, null);
    }

    public int getDiaryCountByTopicId(long topicId) {
        //Its own entries and the shortcuts of entries of other diaries
        Cursor cursor = db.rawQuery("SELECT COUNT (*) FROM " + DiaryEntry_V2.TABLE_NAME + " WHERE (" + DiaryEntry_V2.COLUMN_REF_TOPIC__ID + "=?"
                        + " OR " + DiaryEntry_V2._ID + " IN (SELECT " + DBStructure.ShortcutEntry.COLUMN_DIARY_ID + " FROM "
                + DBStructure.ShortcutEntry.TABLE_NAME + " WHERE " + DBStructure.ShortcutEntry.COLUMN_TOPIC_ID + " = ?)" + ")"
                        + com.kiminonawa.mydiary.shared.HiddenEntries.andNotHidden(""),
                new String[]{String.valueOf(topicId), String.valueOf(topicId)});
        int count = 0;
        if (null != cursor) {
            if (cursor.getCount() > 0) {
                cursor.moveToFirst();
                count = cursor.getInt(0);
            }
            cursor.close();
        }
        return count;
    }

    public int getMemoCountByTopicId(long topicId) {
        Cursor cursor = db.rawQuery("SELECT COUNT (*) FROM " + MemoEntry.TABLE_NAME + " WHERE " + MemoEntry.COLUMN_REF_TOPIC__ID + "=?",
                new String[]{String.valueOf(topicId)});
        int count = 0;
        if (null != cursor) {
            if (cursor.getCount() > 0) {
                cursor.moveToFirst();
                count = cursor.getInt(0);
            }
            cursor.close();
        }
        return count;
    }


    public int getContactsCountByTopicId(long topicId) {
        Cursor cursor = db.rawQuery("SELECT COUNT (*) FROM " + ContactsEntry.TABLE_NAME + " WHERE " + ContactsEntry.COLUMN_REF_TOPIC__ID + "=?",
                new String[]{String.valueOf(topicId)});
        int count = 0;
        if (null != cursor) {
            if (cursor.getCount() > 0) {
                cursor.moveToFirst();
                count = cursor.getInt(0);
            }
            cursor.close();
        }
        return count;
    }

    /**
     * The categories that are inside another one: topic id -> the id of the one they are inside of.
     */
    public java.util.Map<Long, Long> getTopicParents() {
        java.util.Map<Long, Long> parents = new java.util.HashMap<>();
        Cursor c = db.rawQuery("SELECT " + TopicEntry._ID + ", " + TopicEntry.COLUMN_PARENT + " FROM "
                + TopicEntry.TABLE_NAME + " WHERE " + TopicEntry.COLUMN_PARENT + " > 0", null);
        try {
            while (c.moveToNext()) {
                parents.put(c.getLong(0), c.getLong(1));
            }
        } finally {
            c.close();
        }
        return parents;
    }

    /**
     * Puts a category inside another one (parentId 0: back on the main screen).
     */
    public void setTopicParent(long topicId, long parentId) {
        ContentValues values = new ContentValues();
        values.put(TopicEntry.COLUMN_PARENT, parentId);
        db.update(TopicEntry.TABLE_NAME, values, TopicEntry._ID + " = ?", new String[]{String.valueOf(topicId)});
    }

    public long delTopic(long topicId) {
        //What was inside it goes back to the main screen
        ContentValues released = new ContentValues();
        released.put(TopicEntry.COLUMN_PARENT, 0);
        db.update(TopicEntry.TABLE_NAME, released, TopicEntry.COLUMN_PARENT + " = ?", new String[]{String.valueOf(topicId)});
        db.delete(DBStructure.ShortcutEntry.TABLE_NAME, DBStructure.ShortcutEntry.COLUMN_TOPIC_ID + " = ?", new String[]{String.valueOf(topicId)});
        return db.delete(
                TopicEntry.TABLE_NAME,
                TopicEntry._ID + " = ?"
                , new String[]{String.valueOf(topicId)});
    }

    private ContentValues createTopicCV(String name, int type, int color) {
        ContentValues values = new ContentValues();
        values.put(TopicEntry.COLUMN_NAME, name);
        values.put(TopicEntry.COLUMN_TYPE, type);
        values.put(TopicEntry.COLUMN_COLOR, color);
        return values;
    }


    /*
     * Diary
     */
    public long insertDiaryInfo(long time, String title,
                                int mood, int weather, boolean attachment,
                                long refTopicId, String locationName) {
        return insertDiaryInfo(time, title, mood, weather, attachment, refTopicId, locationName, "");
    }

    public long insertDiaryInfo(long time, String title,
                                int mood, int weather, boolean attachment,
                                long refTopicId, String locationName, String tags) {
        return insertDiaryInfo(time, title, mood, weather, attachment, refTopicId, locationName, tags, "");
    }

    public long insertDiaryInfo(long time, String title,
                                int mood, int weather, boolean attachment,
                                long refTopicId, String locationName, String tags, String thumbnail) {
        ContentValues values = this.createDiaryInfoCV(time, title,
                mood, weather, attachment, refTopicId, locationName);
        values.put(DiaryEntry_V2.COLUMN_TAGS, tags == null ? "" : tags);
        values.put(DiaryEntry_V2.COLUMN_THUMBNAIL, thumbnail == null ? "" : thumbnail);
        return db.insert(
                DiaryEntry_V2.TABLE_NAME,
                null,
                values);
    }

    public long insertDiaryContent(int type, int position, String content, long diaryId) {
        return insertDiaryContent(type, position, content, diaryId, "");
    }

    /**
     * @param area photos: the part of the photo that is seen (see PhotoArea), "" when nothing was chosen
     */
    public long insertDiaryContent(int type, int position, String content, long diaryId, String area) {
        return db.insert(
                DiaryItemEntry_V2.TABLE_NAME,
                null,
                this.createDiaryContentCV(type, position, content, diaryId, area));
    }

    /**
     * Changes only the tags of a diary (the "add tags" button of the cover).
     */
    public long updateDiaryTags(long diaryId, String tags) {
        ContentValues values = new ContentValues();
        values.put(DiaryEntry_V2.COLUMN_TAGS, tags == null ? "" : tags);
        return db.update(
                DiaryEntry_V2.TABLE_NAME,
                values,
                DiaryEntry_V2._ID + " = ?",
                new String[]{String.valueOf(diaryId)});
    }

    public long updateDiary(long diaryId, long time, String title,
                            int mood, int weather, String location, boolean attachment, String tags) {
        return updateDiary(diaryId, time, title, mood, weather, location, attachment, tags, "");
    }

    public long updateDiary(long diaryId, long time, String title,
                            int mood, int weather, String location, boolean attachment, String tags,
                            String thumbnail) {
        ContentValues values = new ContentValues();
        values.put(DiaryEntry_V2.COLUMN_THUMBNAIL, thumbnail == null ? "" : thumbnail);
        values.put(DiaryEntry_V2.COLUMN_TAGS, tags == null ? "" : tags);
        values.put(DiaryEntry_V2.COLUMN_TIME, time);
        values.put(DiaryEntry_V2.COLUMN_TITLE, title);
        values.put(DiaryEntry_V2.COLUMN_MOOD, mood);
        values.put(DiaryEntry_V2.COLUMN_WEATHER, weather);
        values.put(DiaryEntry_V2.COLUMN_LOCATION, location);
        values.put(DiaryEntry_V2.COLUMN_ATTACHMENT, attachment);

        return db.update(
                DiaryEntry_V2.TABLE_NAME,
                values,
                DiaryEntry_V2._ID + " = ?",
                new String[]{String.valueOf(diaryId)});
    }

    /**
     * Changes the saved content of one item of a diary (used when a checklist box is ticked).
     */
    public long updateDiaryItemContent(long diaryId, int position, String content) {
        ContentValues values = new ContentValues();
        values.put(DiaryItemEntry_V2.COLUMN_CONTENT, content);
        return db.update(
                DiaryItemEntry_V2.TABLE_NAME,
                values,
                DiaryItemEntry_V2.COLUMN_REF_DIARY__ID + " = ? AND " + DiaryItemEntry_V2.COLUMN_POSITION + " = ?",
                new String[]{String.valueOf(diaryId), String.valueOf(position)});
    }

    /**
     * Moves a diary to another diary topic (its lines stay with it).
     */
    /**
     * The other diaries where an entry is also shown (its shortcuts).
     */
    public java.util.List<Long> getShortcutTopics(long diaryId) {
        java.util.List<Long> list = new java.util.ArrayList<>();
        Cursor c = db.rawQuery("SELECT " + DBStructure.ShortcutEntry.COLUMN_TOPIC_ID + " FROM " + DBStructure.ShortcutEntry.TABLE_NAME
                + " WHERE " + DBStructure.ShortcutEntry.COLUMN_DIARY_ID + " = ?", new String[]{String.valueOf(diaryId)});
        try {
            while (c.moveToNext()) {
                list.add(c.getLong(0));
            }
        } finally {
            c.close();
        }
        return list;
    }

    /**
     * Shows an entry in exactly these other diaries (it replaces the shortcuts it had).
     */
    public void setShortcutTopics(long diaryId, java.util.List<Long> topicIds) {
        db.delete(DBStructure.ShortcutEntry.TABLE_NAME, DBStructure.ShortcutEntry.COLUMN_DIARY_ID + " = ?", new String[]{String.valueOf(diaryId)});
        long home = getDiaryTopicId(diaryId);
        for (Long topicId : topicIds) {
            if (topicId == null || topicId == home) {
                continue;
            }
            ContentValues values = new ContentValues();
            values.put(DBStructure.ShortcutEntry.COLUMN_DIARY_ID, diaryId);
            values.put(DBStructure.ShortcutEntry.COLUMN_TOPIC_ID, topicId);
            db.insert(DBStructure.ShortcutEntry.TABLE_NAME, null, values);
        }
    }

    /**
     * The diary an entry belongs to (where its photos are), -1 if it does not exist.
     */
    public long getDiaryTopicId(long diaryId) {
        Cursor c = db.rawQuery("SELECT " + DiaryEntry_V2.COLUMN_REF_TOPIC__ID + " FROM " + DiaryEntry_V2.TABLE_NAME
                + " WHERE " + DiaryEntry_V2._ID + " = ?", new String[]{String.valueOf(diaryId)});
        try {
            return c.moveToFirst() ? c.getLong(0) : -1;
        } finally {
            c.close();
        }
    }

    /**
     * All the entries that can be seen now (not hidden), for the main screen.
     */
    public int getVisibleDiaryCount() {
        Cursor c = db.rawQuery("SELECT COUNT(*) FROM " + DiaryEntry_V2.TABLE_NAME + " WHERE 1 = 1"
                + com.kiminonawa.mydiary.shared.HiddenEntries.andNotHidden(""), null);
        try {
            return c.moveToFirst() ? c.getInt(0) : 0;
        } finally {
            c.close();
        }
    }

    public long updateDiaryTopic(long diaryId, long newTopicId) {
        //Moved into a diary where it had a shortcut: it is there for real now
        db.delete(DBStructure.ShortcutEntry.TABLE_NAME, DBStructure.ShortcutEntry.COLUMN_DIARY_ID + " = ? AND " + DBStructure.ShortcutEntry.COLUMN_TOPIC_ID + " = ?",
                new String[]{String.valueOf(diaryId), String.valueOf(newTopicId)});
        ContentValues values = new ContentValues();
        values.put(DiaryEntry_V2.COLUMN_REF_TOPIC__ID, newTopicId);
        return db.update(
                DiaryEntry_V2.TABLE_NAME,
                values,
                DiaryEntry_V2._ID + " = ?",
                new String[]{String.valueOf(diaryId)});
    }

    /**
     * Makes a copy of a diary (all its info and all its lines) in a diary topic.
     *
     * @return the id of the new diary, or -1 when it could not be copied
     */
    public long copyDiary(long diaryId, long newTopicId) {
        Cursor info = db.query(DiaryEntry_V2.TABLE_NAME, null, DiaryEntry_V2._ID + " = ?",
                new String[]{String.valueOf(diaryId)}, null, null, null);
        if (info == null) {
            return -1;
        }
        ContentValues infoValues = new ContentValues();
        try {
            if (!info.moveToFirst()) {
                return -1;
            }
            android.database.DatabaseUtils.cursorRowToContentValues(info, infoValues);
        } finally {
            info.close();
        }
        infoValues.remove(DiaryEntry_V2._ID);
        infoValues.put(DiaryEntry_V2.COLUMN_REF_TOPIC__ID, newTopicId);
        long newDiaryId = db.insert(DiaryEntry_V2.TABLE_NAME, null, infoValues);
        if (newDiaryId < 0) {
            return -1;
        }
        Cursor items = db.query(DiaryItemEntry_V2.TABLE_NAME, null, DiaryItemEntry_V2.COLUMN_REF_DIARY__ID + " = ?",
                new String[]{String.valueOf(diaryId)}, null, null, DiaryItemEntry_V2.COLUMN_POSITION + " ASC");
        if (items != null) {
            try {
                while (items.moveToNext()) {
                    ContentValues itemValues = new ContentValues();
                    android.database.DatabaseUtils.cursorRowToContentValues(items, itemValues);
                    itemValues.remove(DiaryItemEntry_V2._ID);
                    itemValues.put(DiaryItemEntry_V2.COLUMN_REF_DIARY__ID, newDiaryId);
                    db.insert(DiaryItemEntry_V2.TABLE_NAME, null, itemValues);
                }
            } finally {
                items.close();
            }
        }
        return newDiaryId;
    }

    public long delDiary(long diaryId) {
        db.delete(DBStructure.ShortcutEntry.TABLE_NAME, DBStructure.ShortcutEntry.COLUMN_DIARY_ID + " = ?", new String[]{String.valueOf(diaryId)});
        return db.delete(
                DiaryEntry_V2.TABLE_NAME,
                DiaryEntry_V2._ID + " = ?"
                , new String[]{String.valueOf(diaryId)});
    }

    public long delAllDiaryInTopic(long topicId) {
        //The shortcuts of its entries, and the shortcuts that pointed to it
        db.execSQL("DELETE FROM " + DBStructure.ShortcutEntry.TABLE_NAME + " WHERE " + DBStructure.ShortcutEntry.COLUMN_TOPIC_ID + " = ? OR "
                + DBStructure.ShortcutEntry.COLUMN_DIARY_ID + " IN (SELECT " + DiaryEntry_V2._ID + " FROM " + DiaryEntry_V2.TABLE_NAME
                + " WHERE " + DiaryEntry_V2.COLUMN_REF_TOPIC__ID + " = ?)", new Object[]{topicId, topicId});
        return db.delete(
                DiaryEntry_V2.TABLE_NAME,
                DiaryEntry_V2.COLUMN_REF_TOPIC__ID + " = ?"
                , new String[]{String.valueOf(topicId)});
    }

    public long delAllDiaryItemByDiaryId(long diaryId) {
        return db.delete(
                DiaryItemEntry_V2.TABLE_NAME,
                DiaryItemEntry_V2.COLUMN_REF_DIARY__ID + " = ?"
                , new String[]{String.valueOf(diaryId)});
    }


    /**
     * The entries of a topic that can be seen now (hidden ones only after the secret of Settings), the pinned ones
     * first, then the newest first.
     */
    public Cursor selectDiaryList(long topicId) {
        return selectDiaryList(topicId, false);
    }

    /**
     * @param all true: also the hidden entries, and only by date (backup, deleting the topic)
     */
    public Cursor selectDiaryList(long topicId, boolean all) {
        //all: only its own entries; else also the shortcuts of entries of other diaries
        String where = all ? DiaryEntry_V2.COLUMN_REF_TOPIC__ID + " = ?"
                : "(" + DiaryEntry_V2.COLUMN_REF_TOPIC__ID + " = ?" + " OR " + DiaryEntry_V2._ID + " IN (SELECT " + DBStructure.ShortcutEntry.COLUMN_DIARY_ID + " FROM "
                + DBStructure.ShortcutEntry.TABLE_NAME + " WHERE " + DBStructure.ShortcutEntry.COLUMN_TOPIC_ID + " = ?)" + ")"
                + com.kiminonawa.mydiary.shared.HiddenEntries.andNotHidden("");
        String order = (all ? "" : "IFNULL(" + DiaryEntry_V2.COLUMN_PINNED + ", 0) DESC, ")
                + DiaryEntry_V2.COLUMN_TIME + " DESC , " + DiaryEntry_V2._ID + " DESC";
        String[] args = all ? new String[]{String.valueOf(topicId)}
                : new String[]{String.valueOf(topicId), String.valueOf(topicId)};
        Cursor c = db.query(DiaryEntry_V2.TABLE_NAME, null, where, args, null, null,
                order, null);
        if (c != null) {
            c.moveToFirst();
        }
        return c;
    }

    /**
     * All diaries of every diary topic (id, time, title, topic id, topic name), newest first.
     * Used to look for the memories of this day in past years.
     */
    public Cursor selectDiaryMemoryCandidates() {
        Cursor c = db.rawQuery("SELECT " + DiaryEntry_V2.TABLE_NAME + "." + DiaryEntry_V2._ID + ", "
                        + DiaryEntry_V2.COLUMN_TIME + ", "
                        + DiaryEntry_V2.COLUMN_TITLE + ", "
                        + DiaryEntry_V2.COLUMN_REF_TOPIC__ID + ", "
                        + TopicEntry.COLUMN_NAME + ", "
                        //5 tags, 6 weather, 7 mood, 8 has attachment, 9 chosen cover photo
                        + DiaryEntry_V2.COLUMN_TAGS + ", "
                        + DiaryEntry_V2.COLUMN_WEATHER + ", "
                        + DiaryEntry_V2.COLUMN_MOOD + ", "
                        + DiaryEntry_V2.COLUMN_ATTACHMENT + ", "
                        + DiaryEntry_V2.COLUMN_THUMBNAIL
                        + " FROM " + DiaryEntry_V2.TABLE_NAME
                        + " JOIN " + TopicEntry.TABLE_NAME
                        + " ON " + DiaryEntry_V2.COLUMN_REF_TOPIC__ID + " = " + TopicEntry.TABLE_NAME + "." + TopicEntry._ID
                        + " WHERE 1 = 1" + com.kiminonawa.mydiary.shared.HiddenEntries.andNotHidden("")
                        + " ORDER BY " + DiaryEntry_V2.COLUMN_TIME + " DESC",
                null);
        if (c != null) {
            c.moveToFirst();
        }
        return c;
    }

    /**
     * Every written line (text and checklist) of every diary, with the info of the diary and of its topic,
     * for the search of the main page. A diary without lines comes once with empty line columns.
     * Columns: 0 diary id, 1 time, 2 title, 3 topic id, 4 topic name, 5 tags, 6 line type, 7 line content.
     */
    public Cursor selectDiaryTextForSearch() {
        Cursor c = db.rawQuery("SELECT d." + DiaryEntry_V2._ID + ", d." + DiaryEntry_V2.COLUMN_TIME + ", d."
                        + DiaryEntry_V2.COLUMN_TITLE + ", d." + DiaryEntry_V2.COLUMN_REF_TOPIC__ID + ", t."
                        + TopicEntry.COLUMN_NAME + ", d." + DiaryEntry_V2.COLUMN_TAGS + ", i."
                        + DiaryItemEntry_V2.COLUMN_TYPE + ", i." + DiaryItemEntry_V2.COLUMN_CONTENT
                        //8 weather, 9 mood, 10 has attachment, 11 chosen cover photo (to draw the same card as the list)
                        + ", d." + DiaryEntry_V2.COLUMN_WEATHER + ", d." + DiaryEntry_V2.COLUMN_MOOD
                        + ", d." + DiaryEntry_V2.COLUMN_ATTACHMENT + ", d." + DiaryEntry_V2.COLUMN_THUMBNAIL
                        + " FROM " + DiaryEntry_V2.TABLE_NAME + " d"
                        + " JOIN " + TopicEntry.TABLE_NAME + " t ON d." + DiaryEntry_V2.COLUMN_REF_TOPIC__ID
                        + " = t." + TopicEntry._ID
                        + " LEFT JOIN " + DiaryItemEntry_V2.TABLE_NAME + " i ON i." + DiaryItemEntry_V2.COLUMN_REF_DIARY__ID
                        + " = d." + DiaryEntry_V2._ID + " AND i." + DiaryItemEntry_V2.COLUMN_TYPE + " IN (0, 3)"
                        + " WHERE 1 = 1" + com.kiminonawa.mydiary.shared.HiddenEntries.andNotHidden("d.")
                        + " ORDER BY d." + DiaryEntry_V2.COLUMN_TIME + " DESC, d." + DiaryEntry_V2._ID
                        + " DESC, i." + DiaryItemEntry_V2.COLUMN_POSITION + " ASC",
                null);
        if (c != null) {
            c.moveToFirst();
        }
        return c;
    }

    /**
     * Pins (or unpins) an entry at the top of the list of its topic.
     */
    public void setDiaryPinned(long diaryId, boolean pinned) {
        ContentValues values = new ContentValues();
        values.put(DiaryEntry_V2.COLUMN_PINNED, pinned ? 1 : 0);
        db.update(DiaryEntry_V2.TABLE_NAME, values, DiaryEntry_V2._ID + " = ?", new String[]{String.valueOf(diaryId)});
    }

    /**
     * Hides (or shows again) an entry.
     */
    public void setDiaryHidden(long diaryId, boolean hidden) {
        ContentValues values = new ContentValues();
        values.put(DiaryEntry_V2.COLUMN_HIDDEN, hidden ? 1 : 0);
        db.update(DiaryEntry_V2.TABLE_NAME, values, DiaryEntry_V2._ID + " = ?", new String[]{String.valueOf(diaryId)});
    }

    /**
     * @return {pinned, hidden} of an entry
     */
    public boolean[] getDiaryFlags(long diaryId) {
        boolean[] flags = new boolean[2];
        Cursor c = db.rawQuery("SELECT IFNULL(" + DiaryEntry_V2.COLUMN_PINNED + ", 0), IFNULL(" + DiaryEntry_V2.COLUMN_HIDDEN
                + ", 0) FROM " + DiaryEntry_V2.TABLE_NAME + " WHERE " + DiaryEntry_V2._ID + " = ?", new String[]{String.valueOf(diaryId)});
        try {
            if (c.moveToFirst()) {
                flags[0] = c.getInt(0) > 0;
                flags[1] = c.getInt(1) > 0;
            }
        } finally {
            c.close();
        }
        return flags;
    }

    public Cursor selectDiaryInfoByDiaryId(long diaryId) {
        Cursor c = db.query(DiaryEntry_V2.TABLE_NAME, null, DiaryEntry_V2._ID + " = ?", new String[]{String.valueOf(diaryId)},
                null, null, null);
        if (c != null) {
            c.moveToFirst();
        }
        return c;
    }

    public Cursor selectDiaryContentByDiaryId(long diaryId) {
        Cursor c = db.query(DiaryItemEntry_V2.TABLE_NAME, null, DiaryItemEntry_V2.COLUMN_REF_DIARY__ID + " = ?", new String[]{String.valueOf(diaryId)},
                null, null, DiaryItemEntry_V2.COLUMN_POSITION + " ASC", null);
        if (c != null) {
            c.moveToFirst();
        }
        return c;
    }


    private ContentValues createDiaryInfoCV(long time, String title,
                                            int mood, int weather, boolean attachment, long refTopicId,
                                            String locationName) {
        ContentValues values = new ContentValues();
        values.put(DiaryEntry_V2.COLUMN_TIME, time);
        values.put(DiaryEntry_V2.COLUMN_TITLE, title);
        values.put(DiaryEntry_V2.COLUMN_MOOD, mood);
        values.put(DiaryEntry_V2.COLUMN_WEATHER, weather);
        values.put(DiaryEntry_V2.COLUMN_ATTACHMENT, attachment);
        values.put(DiaryEntry_V2.COLUMN_REF_TOPIC__ID, refTopicId);
        values.put(DiaryEntry_V2.COLUMN_LOCATION, locationName);
        return values;
    }

    private ContentValues createDiaryContentCV(int type, int position, String content, long diaryId, String area) {
        ContentValues values = new ContentValues();
        values.put(DiaryItemEntry_V2.COLUMN_AREA, area == null ? "" : area);
        values.put(DiaryItemEntry_V2.COLUMN_TYPE, type);
        values.put(DiaryItemEntry_V2.COLUMN_POSITION, position);
        values.put(DiaryItemEntry_V2.COLUMN_CONTENT, content);
        values.put(DiaryItemEntry_V2.COLUMN_REF_DIARY__ID, diaryId);
        return values;
    }

    /*
     * MEMO
     */
    public long insertMemo(String content, boolean isChecked, long refTopicId) {
        return db.insert(
                MemoEntry.TABLE_NAME,
                null,
                this.createMemoCV(content, isChecked, refTopicId));
    }


    public long delMemo(long memoId) {
        return db.delete(
                MemoEntry.TABLE_NAME,
                MemoEntry._ID + " = ?"
                , new String[]{String.valueOf(memoId)});
    }

    public long delAllMemoInTopic(long topicId) {
        return db.delete(
                MemoEntry.TABLE_NAME,
                MemoEntry.COLUMN_REF_TOPIC__ID + " = ?"
                , new String[]{String.valueOf(topicId)});
    }

    /**
     * For select all memo and add order when database version update
     *
     * @param topicId
     * @return
     */
    public Cursor selectMemo(long topicId) {
        Cursor c = db.query(MemoEntry.TABLE_NAME, null, MemoEntry.COLUMN_REF_TOPIC__ID + " = ?", new String[]{String.valueOf(topicId)},
                null, null, null, null);
        if (c != null) {
            c.moveToFirst();
        }
        return c;
    }

    /**
     * Select memo & order for show in memoActivity
     *
     * @param topicId
     * @return
     */
    public Cursor selectMemoAndMemoOrder(long topicId) {
        Cursor c = db.rawQuery("SELECT * FROM " + MemoEntry.TABLE_NAME
                        + " LEFT OUTER JOIN " + MemoOrderEntry.TABLE_NAME
                        + " ON " + MemoEntry._ID + " = " + MemoOrderEntry.COLUMN_REF_MEMO__ID
                        + " WHERE " + MemoEntry.COLUMN_REF_TOPIC__ID + " = " + topicId
                        + " ORDER BY " + MemoOrderEntry.COLUMN_ORDER + " DESC "
                , null);
        if (c != null) {
            c.moveToFirst();
        }
        return c;
    }

    public long updateMemoChecked(long memoId, boolean isChecked) {
        ContentValues values = new ContentValues();
        values.put(MemoEntry.COLUMN_CHECKED, isChecked);
        return db.update(
                MemoEntry.TABLE_NAME,
                values,
                MemoEntry._ID + " = ?",
                new String[]{String.valueOf(memoId)});
    }

    public long updateMemoContent(long memoId, String memoContent) {
        ContentValues values = new ContentValues();
        values.put(MemoEntry.COLUMN_CONTENT, memoContent);
        return db.update(
                MemoEntry.TABLE_NAME,
                values,
                MemoEntry._ID + " = ?",
                new String[]{String.valueOf(memoId)});
    }

    public long insertMemoOrder(long topicId, long memoId, long order) {
        ContentValues values = new ContentValues();
        values.put(MemoOrderEntry.COLUMN_ORDER, order);
        values.put(MemoOrderEntry.COLUMN_REF_TOPIC__ID, topicId);
        values.put(MemoOrderEntry.COLUMN_REF_MEMO__ID, memoId);
        return db.insert(
                MemoOrderEntry.TABLE_NAME,
                null,
                values);
    }

    public long deleteMemoOrder(long memoId) {
        return db.delete(
                MemoOrderEntry.TABLE_NAME,
                MemoOrderEntry.COLUMN_REF_MEMO__ID + " = ?"
                , new String[]{String.valueOf(memoId)});
    }

    public long deleteAllCurrentMemoOrder(long topicId) {
        return db.delete(
                MemoOrderEntry.TABLE_NAME,
                MemoOrderEntry.COLUMN_REF_TOPIC__ID + " = ?"
                , new String[]{String.valueOf(topicId)});
    }


    private ContentValues createMemoCV(String content, boolean isChecked, long refTopicId) {
        ContentValues values = new ContentValues();
        values.put(MemoEntry.COLUMN_CONTENT, content);
        values.put(MemoEntry.COLUMN_CHECKED, isChecked);
        values.put(MemoEntry.COLUMN_REF_TOPIC__ID, refTopicId);
        return values;
    }

    /*
     * Contacts
     */

    public long insertContacts(String name, String phoneNumber, String photo, long refTopicId) {
        return db.insert(
                ContactsEntry.TABLE_NAME,
                null,
                this.createContactsCV(name, phoneNumber, photo, refTopicId));
    }

    public long updateContacts(long contactsId, String name, String phoneNumber, String photo) {
        ContentValues values = new ContentValues();
        values.put(ContactsEntry.COLUMN_NAME, name);
        values.put(ContactsEntry.COLUMN_PHONENUMBER, phoneNumber);
        values.put(ContactsEntry.COLUMN_PHOTO, photo);

        return db.update(
                ContactsEntry.TABLE_NAME,
                values,
                ContactsEntry._ID + " = ?",
                new String[]{String.valueOf(contactsId)});
    }


    public long delContacts(long contactsId) {
        return db.delete(
                ContactsEntry.TABLE_NAME,
                ContactsEntry._ID + " = ?"
                , new String[]{String.valueOf(contactsId)});
    }

    public long delAllContactsInTopic(long topicId) {
        return db.delete(
                ContactsEntry.TABLE_NAME,
                ContactsEntry.COLUMN_REF_TOPIC__ID + " = ?"
                , new String[]{String.valueOf(topicId)});
    }

    public Cursor selectContacts(long topicId) {
        Cursor c = db.query(ContactsEntry.TABLE_NAME, null, ContactsEntry.COLUMN_REF_TOPIC__ID + " = ?", new String[]{String.valueOf(topicId)}, null, null,
                ContactsEntry._ID + " DESC", null);
        if (c != null) {
            c.moveToFirst();
        }
        return c;
    }

    private ContentValues createContactsCV(String name, String phoneNumber, String photo, long refTopicId) {
        ContentValues values = new ContentValues();
        values.put(ContactsEntry.COLUMN_NAME, name);
        values.put(ContactsEntry.COLUMN_PHONENUMBER, phoneNumber);
        values.put(ContactsEntry.COLUMN_PHOTO, photo);
        values.put(ContactsEntry.COLUMN_REF_TOPIC__ID, refTopicId);
        return values;
    }

    /**
     * For version 4 onUpgrade
     */
    public Cursor selectAllV1Diary() {
        Cursor c = db.query(DBStructure.DiaryEntry.TABLE_NAME, null, null, null,
                null, null, null, null);
        if (c != null) {
            c.moveToFirst();
        }
        return c;
    }


    /*
     * Debug
     */

    //For Debug
    public void showCursor(Cursor cursor) {
        for (int i = 0; i < cursor.getCount(); i++) {
            StringBuilder sb = new StringBuilder();
            int columnsQty = cursor.getColumnCount();
            for (int idx = 0; idx < columnsQty; ++idx) {
                sb.append(" " + idx + " = ");
                sb.append(cursor.getString(idx));
                if (idx < columnsQty - 1)
                    sb.append(" ; ");
            }
            Log.e("test", String.format("Row: %d, Values: %s", cursor.getPosition(), sb.toString()));
            cursor.moveToNext();
        }
        //Revert Cursor
        cursor.moveToFirst();
    }

}
