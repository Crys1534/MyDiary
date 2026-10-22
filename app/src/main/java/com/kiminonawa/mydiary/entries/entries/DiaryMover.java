package com.kiminonawa.mydiary.entries.entries;

import android.content.Context;
import android.database.Cursor;

import com.kiminonawa.mydiary.db.DBManager;
import com.kiminonawa.mydiary.main.topic.ITopic;
import com.kiminonawa.mydiary.shared.FileManager;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Moves or copies a diary (an entry) to another diary (topic), with its photos.
 * The photos of a diary live in their own folder: diary/topicId/diaryId/
 */
public class DiaryMover {

    public static class TopicOption {
        public final long id;
        public final String name;

        TopicOption(long id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    /**
     * All the diaries (topics of type diary) of the app, in the order of the main page.
     */
    public static List<TopicOption> diaryTopics(Context context) {
        List<TopicOption> list = new ArrayList<>();
        DBManager dbManager = new DBManager(context);
        try {
            dbManager.opeDB();
            Cursor cursor = dbManager.selectTopic();
            if (cursor != null) {
                for (int i = 0; i < cursor.getCount(); i++) {
                    cursor.moveToPosition(i);
                    if (cursor.getInt(2) == ITopic.TYPE_DIARY) {
                        list.add(new TopicOption(cursor.getLong(0), cursor.getString(1)));
                    }
                }
                cursor.close();
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
        return list;
    }

    /**
     * @return true when the entry is now in the other diary
     */
    public static boolean move(Context context, long diaryId, long fromTopicId, long toTopicId) {
        if (fromTopicId == toTopicId) {
            return true;
        }
        DBManager dbManager = new DBManager(context);
        try {
            dbManager.opeDB();
            //The photos first: if this fails nothing was changed in the database yet
            File from = new FileManager(context, fromTopicId, diaryId).getDir();
            File to = new FileManager(context, toTopicId, diaryId).getDir();
            if (!moveFiles(from, to)) {
                return false;
            }
            dbManager.updateDiaryTopic(diaryId, toTopicId);
            deleteEmptyDir(from);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        } finally {
            try {
                dbManager.closeDB();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * @return the id of the new entry, or -1 when it could not be copied
     */
    public static long copy(Context context, long diaryId, long fromTopicId, long toTopicId) {
        DBManager dbManager = new DBManager(context);
        long newDiaryId = -1;
        try {
            dbManager.opeDB();
            dbManager.beginTransaction();
            try {
                newDiaryId = dbManager.copyDiary(diaryId, toTopicId);
                if (newDiaryId >= 0) {
                    dbManager.setTransactionSuccessful();
                }
            } finally {
                dbManager.endTransaction();
            }
            if (newDiaryId >= 0) {
                File from = new FileManager(context, fromTopicId, diaryId).getDir();
                File to = new FileManager(context, toTopicId, newDiaryId).getDir();
                copyFiles(from, to);
            }
        } catch (Exception e) {
            e.printStackTrace();
            newDiaryId = -1;
        } finally {
            try {
                dbManager.closeDB();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return newDiaryId;
    }

    private static boolean moveFiles(File from, File to) {
        File[] files = from.listFiles();
        if (files == null) {
            return true;   //a diary without photos
        }
        if (!to.exists() && !to.mkdirs()) {
            return false;
        }
        for (File file : files) {
            if (!file.isFile()) {
                continue;
            }
            File target = new File(to, file.getName());
            if (!file.renameTo(target)) {
                try {
                    copyFile(file, target);
                    //noinspection ResultOfMethodCallIgnored
                    file.delete();
                } catch (IOException e) {
                    e.printStackTrace();
                    return false;
                }
            }
        }
        return true;
    }

    private static void copyFiles(File from, File to) throws IOException {
        File[] files = from.listFiles();
        if (files == null) {
            return;
        }
        if (!to.exists() && !to.mkdirs()) {
            throw new IOException("can not create " + to);
        }
        for (File file : files) {
            if (file.isFile()) {
                copyFile(file, new File(to, file.getName()));
            }
        }
    }

    private static void copyFile(File from, File to) throws IOException {
        InputStream in = null;
        OutputStream out = null;
        try {
            in = new FileInputStream(from);
            out = new FileOutputStream(to);
            byte[] buffer = new byte[16 * 1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        } finally {
            try {
                if (in != null) {
                    in.close();
                }
            } catch (IOException ignored) {
            }
            if (out != null) {
                out.close();
            }
        }
    }

    private static void deleteEmptyDir(File dir) {
        String[] left = dir.list();
        if (left != null && left.length == 0) {
            //noinspection ResultOfMethodCallIgnored
            dir.delete();
        }
    }
}
