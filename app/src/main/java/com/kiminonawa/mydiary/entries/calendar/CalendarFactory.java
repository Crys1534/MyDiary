package com.kiminonawa.mydiary.entries.calendar;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;

import androidx.core.content.ContextCompat;

import com.kiminonawa.mydiary.R;
import com.kiminonawa.mydiary.shared.ColorTools;
import com.kiminonawa.mydiary.shared.ScreenHelper;
import com.kiminonawa.mydiary.shared.ThemeManager;
import com.kiminonawa.mydiary.shared.TimeTools;

import java.util.Calendar;

/**
 * Created by daxia on 2017/2/4.
 */

public class CalendarFactory {


    private Context mContext;
    //The page of the big calendar: white in light mode, dark in dark mode
    private int pageColor;
    private Calendar calendar;
    private int dateChange = 0;

    private TimeTools timeTools;
    private Paint monthPaint, datePaint, dayPaint;

    private Rect textRect;
    private int textBaseX;
    private float centerBaseLine, monthBaseLine, dayBaseLine;
    //Test size
    private float scale;


    public CalendarFactory(Context context,Calendar calendar ,int width, int height) {

        this.calendar = calendar;
        timeTools = TimeTools.getInstance(context);
        mContext = context;
        pageColor = ContextCompat.getColor(context, R.color.app_surface);
        textRect = new Rect(0, 0, width, height);
        scale = context.getResources().getDisplayMetrics().density;

        //init Color
        monthPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        setPrintTextSize(monthPaint,40);
        monthPaint.setColor(ColorTools.readableOnSurface(context, ThemeManager.getInstance().getThemeDarkColor(context)));
        monthPaint.setTextAlign(Paint.Align.CENTER);

        datePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        setPrintTextSize(datePaint,130);
        datePaint.setColor(ColorTools.readableOnSurface(context, ThemeManager.getInstance().getThemeDarkColor(context)));
        datePaint.setTextAlign(Paint.Align.CENTER);
        datePaint.setTypeface(Typeface.DEFAULT_BOLD);

        dayPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        setPrintTextSize(dayPaint,25);
        dayPaint.setColor(ColorTools.readableOnSurface(context, ThemeManager.getInstance().getThemeDarkColor(context)));
        dayPaint.setTextAlign(Paint.Align.CENTER);

        textBaseX = width / 2;
        centerBaseLine = textRect.centerY() + (getTextHeight(datePaint) / 2) - datePaint.getFontMetrics().bottom;
        monthBaseLine = centerBaseLine + (datePaint.getFontMetrics().top - ScreenHelper.dpToPixel(mContext.getResources(), 5));
        dayBaseLine = centerBaseLine + (getTextHeight(dayPaint) + ScreenHelper.dpToPixel(mContext.getResources(), 20));
    }

    private void setPrintTextSize(Paint paint, float textSize) {
        paint.setTextSize(textSize * scale + 0.5f);

    }

    public synchronized void onDraw(Canvas canvas) {
        canvas.drawColor(pageColor);
        updateCalendar(canvas);
    }


    public synchronized void nextDateDraw(Canvas canvas) {
        canvas.drawColor(pageColor);
        dateChange = 1;
        calendar.set(Calendar.DATE, calendar.get(Calendar.DATE) + dateChange);
        updateCalendar(canvas);
    }

    public synchronized void preDateDraw(Canvas canvas) {
        canvas.drawColor(pageColor);
        dateChange = -1;
        calendar.set(Calendar.DATE, calendar.get(Calendar.DATE) + dateChange);
        updateCalendar(canvas);
    }


    private void updateCalendar(Canvas canvas) {
        canvas.drawText(String.valueOf(calendar.get(Calendar.DAY_OF_MONTH)),
                textBaseX, centerBaseLine, datePaint);

        canvas.drawText(timeTools.getMonthsFullName()[calendar.get(Calendar.MONTH)],
                textBaseX, monthBaseLine, monthPaint);

        canvas.drawText(timeTools.getDaysFullName()[calendar.get(Calendar.DAY_OF_WEEK) - 1],
                textBaseX, dayBaseLine, dayPaint);
    }

    private float getTextHeight(Paint paint) {
        Paint.FontMetrics fontMetrics = paint.getFontMetrics();
        float top = fontMetrics.top;
        float bottom = fontMetrics.bottom;
        return bottom - top;
    }

}
