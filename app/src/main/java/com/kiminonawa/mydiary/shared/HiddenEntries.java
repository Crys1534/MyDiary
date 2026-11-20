package com.kiminonawa.mydiary.shared;

import com.kiminonawa.mydiary.db.DBStructure;

/**
 * Hidden entries: they are not in the lists, the calendar, the counts or the search. They are seen only after touching
 * the word "System" (Settings) 10 times; 10 more times hide them again. It is not saved: when the app is closed they
 * are hidden again.
 */
public final class HiddenEntries {

    public static final int TAPS = 10;
    private static boolean revealed = false;

    private HiddenEntries() {
    }

    public static boolean isRevealed() {
        return revealed;
    }

    public static boolean toggle() {
        revealed = !revealed;
        return revealed;
    }

    /**
     * A piece of SQL for the WHERE part: " AND (not hidden)", or nothing when the hidden entries can be seen.
     *
     * @param alias "" or the name of the table and a dot ("d.")
     */
    public static String andNotHidden(String alias) {
        if (revealed) {
            return "";
        }
        return " AND IFNULL(" + alias + DBStructure.DiaryEntry_V2.COLUMN_HIDDEN + ", 0) = 0";
    }
}
