package com.kiminonawa.mydiary.entries.calendar;


import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PorterDuff;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;

import com.kiminonawa.mydiary.shared.ColorTools;
import com.kiminonawa.mydiary.shared.Sfx;
import com.kiminonawa.mydiary.R;
import com.kiminonawa.mydiary.entries.BaseDiaryFragment;
import com.kiminonawa.mydiary.entries.DiaryActivity;
import com.kiminonawa.mydiary.shared.ThemeManager;
import com.prolificinteractive.materialcalendarview.CalendarDay;
import com.prolificinteractive.materialcalendarview.CalendarMode;
import com.prolificinteractive.materialcalendarview.DayViewDecorator;
import com.prolificinteractive.materialcalendarview.DayViewFacade;
import com.prolificinteractive.materialcalendarview.MaterialCalendarView;
import com.prolificinteractive.materialcalendarview.OnDateSelectedListener;
import com.prolificinteractive.materialcalendarview.spans.DotSpan;

import com.kiminonawa.mydiary.entries.entries.EntriesEntity;

import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static java.util.Collections.binarySearch;


public class CalendarFragment extends BaseDiaryFragment implements View.OnClickListener,
        OnDateSelectedListener, DayViewDecorator {


    /**
     * Calendar
     */
    private Calendar calendar;
    private Date currentDate;

    private ThemeManager themeManager;

    /**
     * UI
     */
    private RelativeLayout RL_calendar_content;
    private RelativeLayout RL_calendar_edit_bar;
    private FloatingActionButton FAB_calendar_change_mode;

    /**
     * calendar Mode
     */
    private PageEffectView pageEffectView;
    private MaterialCalendarView materialCalendarView;

    private int currentMode;

    /**
     * The photo of every day (month mode): day -> path of the photo, and the small pictures already read
     */
    private Map<CalendarDay, String> dayPhotoPaths = new LinkedHashMap<>();
    private final Map<String, Bitmap> smallPictures = new HashMap<>();
    private int photoLoadToken = 0;
    private static final int MAX_PHOTO_DAYS = 400;
    private static final int SMALL_PICTURE_PX = 128;

    private static final int MODE_DAY = 1;
    private static final int MODE_MONTH = 2;


    public CalendarFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        calendar = Calendar.getInstance();
        currentDate = new Date();
        calendar.setTime(currentDate);
        themeManager = ThemeManager.getInstance();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_calendar, container, false);

        RL_calendar_edit_bar = (RelativeLayout) rootView.findViewById(R.id.RL_calendar_edit_bar);
        RL_calendar_edit_bar.setBackgroundColor(themeManager.getThemeMainColor(getActivity()));

        RL_calendar_content = (RelativeLayout) rootView.findViewById(R.id.RL_calendar_content);

        FAB_calendar_change_mode = (FloatingActionButton) rootView.findViewById(R.id.FAB_calendar_change_mode);
        //Set the color
        FAB_calendar_change_mode.getDrawable()
                .setColorFilter(themeManager.getThemeMainColor(getActivity()), PorterDuff.Mode.SRC_ATOP);
        FAB_calendar_change_mode.setOnClickListener(this);
        return rootView;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        //default mode
        currentMode = MODE_DAY;
        initCalendarMode();
    }

    public void refreshCalendar() {
        switch (currentMode) {
            case MODE_DAY:
                //TODO add decorators
                break;
            case MODE_MONTH:
                setupDecorators();
                break;
        }
    }

    /**
     * The days that have an entry with a photo (the newest entry of the day), the newest days first.
     */
    private Map<CalendarDay, String> findDayPhotos() {
        Map<CalendarDay, String> result = new LinkedHashMap<>();
        Calendar day = Calendar.getInstance();
        for (EntriesEntity entity : getEntriesList()) {
            String path = entity.getThumbnailPath();
            if (path == null || path.length() == 0) {
                continue;
            }
            day.setTime(entity.getCreateDate());
            CalendarDay key = CalendarDay.from(day);
            if (!result.containsKey(key)) {
                result.put(key, path);
                if (result.size() >= MAX_PHOTO_DAYS) {
                    break;
                }
            }
        }
        return result;
    }

    /**
     * Dots for the days with an entry and a picture for the days with a photo. The pictures are read in the
     * background (small ones), the dots are seen at once.
     */
    private void setupDecorators() {
        if (materialCalendarView == null) {
            return;
        }
        dayPhotoPaths = findDayPhotos();
        showDecorators();
        final int token = ++photoLoadToken;
        final Map<CalendarDay, String> wanted = new LinkedHashMap<>(dayPhotoPaths);
        new Thread(new Runnable() {
            @Override
            public void run() {
                boolean loadedSomething = false;
                for (String path : wanted.values()) {
                    if (token != photoLoadToken) {
                        return;
                    }
                    synchronized (smallPictures) {
                        if (smallPictures.containsKey(path)) {
                            continue;
                        }
                    }
                    Bitmap small = decodeSmall(path);
                    if (small != null) {
                        synchronized (smallPictures) {
                            smallPictures.put(path, small);
                        }
                        loadedSomething = true;
                    }
                }
                if (loadedSomething) {
                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {
                            if (token == photoLoadToken && isAdded() && materialCalendarView != null) {
                                showDecorators();
                            }
                        }
                    });
                }
            }
        }).start();
    }

    private void showDecorators() {
        if (materialCalendarView == null || getActivity() == null) {
            return;
        }
        float density = getResources().getDisplayMetrics().density;
        java.util.List<DayViewDecorator> decorators = new java.util.ArrayList<>();
        decorators.add(this);
        synchronized (smallPictures) {
            for (Map.Entry<CalendarDay, String> entry : dayPhotoPaths.entrySet()) {
                Bitmap small = smallPictures.get(entry.getValue());
                if (small != null) {
                    decorators.add(new PhotoDayDecorator(entry.getKey(), new DayPhotoDrawable(small, density)));
                }
            }
        }
        //All together: the calendar is redrawn only once
        materialCalendarView.removeDecorators();
        materialCalendarView.addDecorators(decorators);
    }

    private static Bitmap decodeSmall(String path) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(path, bounds);
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                return null;
            }
            int sample = 1;
            while (bounds.outWidth / (sample * 2) >= SMALL_PICTURE_PX && bounds.outHeight / (sample * 2) >= SMALL_PICTURE_PX) {
                sample *= 2;
            }
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sample;
            options.inPreferredConfig = Bitmap.Config.RGB_565;
            return BitmapFactory.decodeFile(path, options);
        } catch (Throwable e) {
            return null;
        }
    }

    private void initCalendarMode() {
        RL_calendar_content.removeAllViews();
        switch (currentMode) {
            case MODE_DAY:
                materialCalendarView = null;
                pageEffectView = new PageEffectView(getActivity(), calendar);
                RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT,
                        RelativeLayout.LayoutParams.MATCH_PARENT);
                pageEffectView.setLayoutParams(params);
                RL_calendar_content.addView(pageEffectView);
                break;
            case MODE_MONTH:
                pageEffectView = null;
                materialCalendarView = new MaterialCalendarView(getActivity());
                RelativeLayout.LayoutParams calendarViewParams = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT,
                        RelativeLayout.LayoutParams.MATCH_PARENT);
                materialCalendarView.setLayoutParams(calendarViewParams);
                materialCalendarView.setShowOtherDates(MaterialCalendarView.SHOW_ALL);
                //A bit see-through, so the photo of the selected day can still be seen
                materialCalendarView.setSelectionColor(
                        ColorUtils.setAlphaComponent(ThemeManager.getInstance().getThemeMainColor(getActivity()), 120));
                materialCalendarView.state().edit()
                        .setFirstDayOfWeek(Calendar.MONDAY)
                        .setCalendarDisplayMode(CalendarMode.MONTHS)
                        .commit();
                if (ColorTools.isNight(getActivity())) {
                    //The library draws dark numbers and arrows unless it is told otherwise
                    materialCalendarView.setDateTextAppearance(R.style.CalendarDateTextNight);
                    materialCalendarView.setArrowColor(ContextCompat.getColor(getActivity(), R.color.app_icon));
                }
                materialCalendarView.setCurrentDate(calendar);
                materialCalendarView.setDateSelected(calendar, true);
                materialCalendarView.setOnDateChangedListener(this);
                RL_calendar_content.addView(materialCalendarView);
                //Add view first , then add Decorators (dots and photos)
                setupDecorators();
                break;
        }
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.FAB_calendar_change_mode:
                //togle the mode
                if (currentMode == MODE_DAY) {
                    currentMode = MODE_MONTH;
                } else {
                    currentMode = MODE_DAY;
                }
                initCalendarMode();
                break;
        }
    }

    @Override
    public void onDateSelected(@NonNull MaterialCalendarView widget, @NonNull CalendarDay date, boolean selected) {
        //Make calendar sync the new date
        Sfx.play(getActivity(), Sfx.Sound.TAB);
        calendar.setTime(date.getDate());

        //Goto the diary position
        int diaryPosition = Collections.binarySearch(getEntriesList(), date);
        if (diaryPosition >= 0) {
            ((DiaryActivity) getActivity()).callEntriesGotoDiaryPosition(diaryPosition);
        }
    }

    @Override
    public boolean shouldDecorate(CalendarDay day) {
        //A day with a photo shows the photo, not the dot
        if (dayPhotoPathsHasPicture(day)) {
            return false;
        }
        return binarySearch(getEntriesList(), day) >= 0;
    }

    private boolean dayPhotoPathsHasPicture(CalendarDay day) {
        String path = dayPhotoPaths.get(day);
        if (path == null) {
            return false;
        }
        synchronized (smallPictures) {
            return smallPictures.containsKey(path);
        }
    }

    @Override
    public void decorate(DayViewFacade view) {
        view.addSpan(new DotSpan(5, ThemeManager.getInstance().getThemeDarkColor(getActivity())));
    }
}
