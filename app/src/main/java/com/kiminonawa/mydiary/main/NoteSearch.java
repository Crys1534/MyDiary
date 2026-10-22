package com.kiminonawa.mydiary.main;

import android.content.Context;
import android.database.Cursor;

import com.kiminonawa.mydiary.db.DBManager;
import com.kiminonawa.mydiary.entries.diary.item.IDairyRow;
import com.kiminonawa.mydiary.entries.diary.item.RichText;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Looks for a word in the title or in the text of the diaries of ALL the topics.
 * It ignores capital letters and accents ("cancion" finds "Canción").
 * The work is slow when there are many diaries: call it away from the screen thread.
 */
public class NoteSearch {

    private static final int MAX_RESULTS = 200;
    private static final int SNIPPET_BEFORE = 25;
    private static final int SNIPPET_AFTER = 70;

    public static class Result {
        public long diaryId;
        public long topicId;
        public String topicName;
        public String title;
        //A piece of the text around the word (empty when the word is only in the title or the tags)
        public String snippet;
        public long time;
        //What the card of the list shows: tags (",school,trip,"), weather, mood, attachment and the small picture
        public String tags = "";
        public int weather;
        public int mood;
        public boolean hasAttachment;
        public String chosenThumbnail;
        public String thumbnailPath;
    }

    public static String fold(String text) {
        if (text == null) {
            return "";
        }
        return Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT);
    }

    public static List<Result> search(Context context, String rawQuery) {
        List<Result> results = new ArrayList<>();
        String query = fold(rawQuery == null ? "" : rawQuery.trim());
        if (query.length() == 0) {
            return results;
        }
        DBManager dbManager = new DBManager(context);
        Cursor cursor = null;
        try {
            dbManager.opeDB();
            cursor = dbManager.selectDiaryTextForSearch();
            if (cursor == null) {
                return results;
            }
            long currentId = Long.MIN_VALUE;
            Result current = null;
            StringBuilder text = new StringBuilder();
            String currentTags = "";
            for (int i = 0; i < cursor.getCount(); i++) {
                cursor.moveToPosition(i);
                long diaryId = cursor.getLong(0);
                if (diaryId != currentId) {
                    finish(results, current, text, currentTags, query);
                    if (results.size() >= MAX_RESULTS) {
                        current = null;
                        break;
                    }
                    currentId = diaryId;
                    current = new Result();
                    current.diaryId = diaryId;
                    current.time = cursor.getLong(1);
                    current.title = cursor.getString(2) == null ? "" : cursor.getString(2);
                    current.topicId = cursor.getLong(3);
                    current.topicName = cursor.getString(4);
                    currentTags = cursor.getString(5) == null ? "" : cursor.getString(5);
                    current.tags = currentTags;
                    current.weather = cursor.getInt(8);
                    current.mood = cursor.getInt(9);
                    current.hasAttachment = cursor.getInt(10) > 0;
                    current.chosenThumbnail = cursor.getString(11);
                    text.setLength(0);
                }
                String content = cursor.getString(7);
                if (content != null) {
                    if (cursor.getInt(6) == IDairyRow.TYPE_CHECKLIST && content.length() > 0) {
                        //The first character of a checklist line says if it is done
                        content = content.substring(1);
                    }
                    if (text.length() > 0) {
                        text.append(' ');
                    }
                    text.append(RichText.strip(content));
                }
            }
            if (results.size() < MAX_RESULTS) {
                finish(results, current, text, currentTags, query);
            }
            //The small picture of every result (like in the list of entries)
            for (Result result : results) {
                result.thumbnailPath = MemoriesHelper.findThumbnail(context, dbManager, result.topicId,
                        result.diaryId, result.chosenThumbnail);
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
            dbManager.closeDB();
        }
        return results;
    }

    private static void finish(List<Result> results, Result current, StringBuilder text,
                               String tags, String foldedQuery) {
        if (current == null) {
            return;
        }
        boolean inTitle = fold(current.title).contains(foldedQuery);
        boolean inTags = fold(tags).contains(foldedQuery);
        String plain = text.toString().replace('\n', ' ');
        // fold() keeps the length of every character, so the positions are the same in both texts
        String foldedPlain = fold(plain);
        int index = foldedPlain.indexOf(foldedQuery);
        if (!inTitle && !inTags && index < 0) {
            return;
        }
        if (index >= 0 && plain.length() == foldedPlain.length()) {
            int from = Math.max(0, index - SNIPPET_BEFORE);
            int to = Math.min(plain.length(), index + foldedQuery.length() + SNIPPET_AFTER);
            current.snippet = (from > 0 ? "…" : "") + plain.substring(from, to).trim()
                    + (to < plain.length() ? "…" : "");
        } else if (index >= 0) {
            current.snippet = plain.substring(0, Math.min(plain.length(), SNIPPET_BEFORE + SNIPPET_AFTER)).trim();
        } else {
            current.snippet = "";
        }
        results.add(current);
    }
}
