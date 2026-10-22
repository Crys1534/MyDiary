package com.kiminonawa.mydiary.entries.calendar;

import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;

import com.prolificinteractive.materialcalendarview.CalendarDay;
import com.prolificinteractive.materialcalendarview.DayViewDecorator;
import com.prolificinteractive.materialcalendarview.DayViewFacade;

/**
 * Puts the photo of the entry as the background of ONE day of the month calendar (the library asks the
 * decorator only once, so every day with a photo has its own decorator).
 */
public class PhotoDayDecorator implements DayViewDecorator {

    private final CalendarDay day;
    private final Drawable photo;

    public PhotoDayDecorator(CalendarDay day, Drawable photo) {
        this.day = day;
        this.photo = photo;
    }

    @Override
    public boolean shouldDecorate(CalendarDay other) {
        return day.equals(other);
    }

    @Override
    public void decorate(DayViewFacade view) {
        view.setBackgroundDrawable(photo);
        view.addSpan(new ForegroundColorSpan(Color.WHITE));
        view.addSpan(new StyleSpan(android.graphics.Typeface.BOLD));
    }
}
