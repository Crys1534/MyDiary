package com.kiminonawa.mydiary.entries.diary.editor;

import android.app.DatePickerDialog;
import android.content.Context;
import android.view.View;
import android.widget.DatePicker;
import android.widget.TextView;

import androidx.fragment.app.FragmentManager;

import com.kiminonawa.mydiary.R;
import com.kiminonawa.mydiary.entries.diary.DiaryCover;
import com.kiminonawa.mydiary.entries.diary.picker.DatePickerFragment;
import com.kiminonawa.mydiary.shared.TimeTools;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

/**
 * The day of an entry: it is shown on the cover ("Octubre 21") and the person can change it with the calendar window.
 * Only the day is chosen (the hour and the minute are the ones of the moment it was written).
 */
public class EntryDateField implements DatePickerDialog.OnDateSetListener {

    private final Calendar calendar = Calendar.getInstance();
    private final TimeTools timeTools;
    private final SimpleDateFormat hourFormat = new SimpleDateFormat("HH:mm", Locale.US);
    private final TextView month, day, time;
    private final DiaryCover cover;

    public EntryDateField(Context context, View rootView, DiaryCover cover) {
        this.cover = cover;
        this.timeTools = TimeTools.getInstance(context.getApplicationContext());
        this.month = (TextView) rootView.findViewById(R.id.TV_diary_month);
        this.day = (TextView) rootView.findViewById(R.id.TV_diary_day);
        this.time = (TextView) rootView.findViewById(R.id.TV_diary_time);
    }

    /**
     * The moment of the entry: right now.
     */
    public void setNow() {
        setMillis(System.currentTimeMillis());
    }

    public void setMillis(long millis) {
        calendar.setTimeInMillis(millis);
        refresh();
    }

    public long getMillis() {
        return calendar.getTimeInMillis();
    }

    /**
     * Opens the calendar window to change the day.
     */
    public void openPicker(FragmentManager fragmentManager) {
        DatePickerFragment datePickerFragment = DatePickerFragment.newInstance(calendar.getTimeInMillis());
        datePickerFragment.setOnDateSetListener(this);
        datePickerFragment.show(fragmentManager, "datePickerFragment");
    }

    @Override
    public void onDateSet(DatePicker view, int year, int monthOfYear, int dayOfMonth) {
        //Since JellyBean, the onDateSet() method of the DatePicker class is called twice
        if (view.isShown()) {
            calendar.set(year, monthOfYear, dayOfMonth);
            refresh();
        }
    }

    private void refresh() {
        month.setText(timeTools.getMonthsFullName()[calendar.get(Calendar.MONTH)]);
        //Only the month and the day
        cover.setDate(calendar);
        day.setText(timeTools.getDaysFullName()[calendar.get(Calendar.DAY_OF_WEEK) - 1]);
        time.setText(hourFormat.format(calendar.getTime()));
    }
}
