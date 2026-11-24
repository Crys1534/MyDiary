package com.kiminonawa.mydiary.main;

import android.content.Context;
import android.database.Cursor;

import com.kiminonawa.mydiary.db.DBManager;
import com.kiminonawa.mydiary.db.DBStructure;
import com.kiminonawa.mydiary.entries.EntryPreview;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The photo on the cover of each notebook of the main screen: the photo of the newest entry of the diary that has
 * one (entries shown there by a shortcut count too; hidden entries do not).
 */
final class TopicCovers {

    private TopicCovers() {
    }

    private static class Row {
        long id, time, homeTopic;
        String chosenThumbnail;
    }

    static Map<Long, MainTopicAdapter.Cover> read(Context context, List<long[]> topics) {
        Map<Long, MainTopicAdapter.Cover> covers = new HashMap<>();
        DBManager dbManager = new DBManager(context);
        dbManager.opeDB();
        try {
            for (long[] topic : topics) {
                long topicId = topic[0];
                int type = (int) topic[1];
                //A photo the person chose for the cover
                java.io.File chosen = com.kiminonawa.mydiary.shared.TopicArt.file(context, type, topicId,
                        com.kiminonawa.mydiary.shared.TopicArt.COVER);
                if (chosen.isFile()) {
                    covers.put(topicId, new MainTopicAdapter.Cover(chosen.getAbsolutePath(), null));
                    continue;
                }
                if (type != com.kiminonawa.mydiary.main.topic.ITopic.TYPE_DIARY) {
                    continue;
                }
                MainTopicAdapter.Cover cover = readOne(context, dbManager, topicId);
                if (cover != null) {
                    covers.put(topicId, cover);
                }
            }
        } catch (RuntimeException e) {
            //Without photos the notebooks are only their color
        } finally {
            dbManager.closeDB();
        }
        return covers;
    }

    private static MainTopicAdapter.Cover readOne(Context context, DBManager dbManager, long topicId) {
        List<Row> rows = new ArrayList<>();
        Cursor c = dbManager.selectDiaryList(topicId);
        try {
            int topicColumn = c.getColumnIndex(DBStructure.DiaryEntry_V2.COLUMN_REF_TOPIC__ID);
            int thumbnailColumn = c.getColumnIndex(DBStructure.DiaryEntry_V2.COLUMN_THUMBNAIL);
            for (int i = 0; i < c.getCount(); i++) {
                c.moveToPosition(i);
                Row row = new Row();
                row.id = c.getLong(0);
                row.time = c.getLong(1);
                row.homeTopic = topicColumn >= 0 ? c.getLong(topicColumn) : topicId;
                row.chosenThumbnail = thumbnailColumn >= 0 ? c.getString(thumbnailColumn) : null;
                rows.add(row);
            }
        } finally {
            c.close();
        }
        //The newest first (the list puts the pinned ones first)
        Collections.sort(rows, new Comparator<Row>() {
            @Override
            public int compare(Row a, Row b) {
                return a.time == b.time ? 0 : (a.time > b.time ? -1 : 1);
            }
        });
        for (Row row : rows) {
            EntryPreview preview = EntryPreview.read(context, dbManager, row.homeTopic, row.id, row.chosenThumbnail);
            if (preview.thumbnailPath != null) {
                return new MainTopicAdapter.Cover(preview.thumbnailPath, preview.thumbnailArea);
            }
        }
        return null;
    }
}
