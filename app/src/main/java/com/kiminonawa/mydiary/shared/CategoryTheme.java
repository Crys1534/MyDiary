package com.kiminonawa.mydiary.shared;

import android.content.Context;

import com.kiminonawa.mydiary.db.DBManager;

/**
 * The look (theme) that the person chose for a category. It is the base for the themes that will come: every theme has
 * a number that is saved with the category (0 = classic, the look the app always had). The screens of a category ask
 * here which look to use.
 * <p>
 * To add a theme: give it a number here, a name in the list "category_theme_names" (same order as the numbers) and use
 * {@link #current()} where the look has to change.
 */
public final class CategoryTheme {

    public static final int CLASSIC = 0;
    //The entries are written on a sheet of a ruled notebook (even when the sheet is off in Settings)
    public static final int NOTEBOOK = 1;

    //The theme of the category that is open now (set when the category is opened)
    private static int current = CLASSIC;

    private CategoryTheme() {
    }

    public static int current() {
        return current;
    }

    /**
     * The category was opened: its look is the one that the screens use now.
     */
    public static void useForTopic(Context context, long topicId) {
        current = read(context, topicId);
    }

    /**
     * The category was closed.
     */
    public static void reset() {
        current = CLASSIC;
    }

    public static int read(Context context, long topicId) {
        DBManager dbManager = new DBManager(context);
        try {
            dbManager.opeDB();
            return dbManager.getTopicTheme(topicId);
        } catch (Exception e) {
            return CLASSIC;
        } finally {
            dbManager.closeDB();
        }
    }

    public static void save(Context context, long topicId, int theme) {
        DBManager dbManager = new DBManager(context);
        try {
            dbManager.opeDB();
            dbManager.updateTopicTheme(topicId, theme);
        } finally {
            dbManager.closeDB();
        }
    }
}
