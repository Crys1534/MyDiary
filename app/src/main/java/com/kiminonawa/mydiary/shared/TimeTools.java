package com.kiminonawa.mydiary.shared;

import android.content.Context;

import java.text.DateFormatSymbols;
import java.util.Locale;

/**
 * The names of the months and of the days of the week, in the language of the app.
 * They come from Android itself (not from lists written by hand), with the first letter in capitals:
 * "Octubre", "Domingo".
 */
public class TimeTools {

    private String[] monthsFullName;
    private String[] daysFullName;
    //The language the names were made for (they are made again when the language changes)
    private Locale builtFor = null;

    private static TimeTools instance = null;

    public static synchronized TimeTools getInstance(Context context) {
        if (instance == null) {
            instance = new TimeTools();
        }
        return instance;
    }

    private TimeTools() {
        build();
    }

    private void build() {
        Locale locale = Locale.getDefault();
        DateFormatSymbols symbols = DateFormatSymbols.getInstance(locale);
        String[] months = symbols.getMonths();
        //Java gives 13 months (the last one is empty) and the days from 1 (Sunday) to 7
        monthsFullName = new String[12];
        for (int i = 0; i < 12; i++) {
            monthsFullName[i] = capitalize(months[i], locale);
        }
        String[] days = symbols.getWeekdays();
        daysFullName = new String[7];
        for (int i = 0; i < 7; i++) {
            daysFullName[i] = capitalize(days[i + 1], locale);
        }
        builtFor = locale;
    }

    private static String capitalize(String text, Locale locale) {
        if (text == null || text.length() == 0) {
            return "";
        }
        return text.substring(0, 1).toUpperCase(locale) + text.substring(1);
    }

    private synchronized void refreshIfLanguageChanged() {
        if (!Locale.getDefault().equals(builtFor)) {
            build();
        }
    }

    public String[] getMonthsFullName() {
        refreshIfLanguageChanged();
        return monthsFullName;
    }

    public String[] getDaysFullName() {
        refreshIfLanguageChanged();
        return daysFullName;
    }
}
