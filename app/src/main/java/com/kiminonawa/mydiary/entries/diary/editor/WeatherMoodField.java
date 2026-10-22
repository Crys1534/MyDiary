package com.kiminonawa.mydiary.entries.diary.editor;

import android.content.Context;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Spinner;

import com.kiminonawa.mydiary.R;
import com.kiminonawa.mydiary.entries.diary.DiaryCover;
import com.kiminonawa.mydiary.entries.diary.DiaryInfoHelper;
import com.kiminonawa.mydiary.entries.diary.ImageArrayAdapter;

/**
 * The weather and the mood of an entry while it is written: two lists with pictures. The cover shows the weather that
 * is chosen (the icon and its name while editing).
 */
public class WeatherMoodField {

    private final Spinner weather;
    private final Spinner mood;
    private final DiaryCover cover;

    public WeatherMoodField(Context context, View rootView, final DiaryCover cover) {
        this.cover = cover;
        weather = (Spinner) rootView.findViewById(R.id.SP_diary_weather);
        mood = (Spinner) rootView.findViewById(R.id.SP_diary_mood);
        weather.setVisibility(View.VISIBLE);
        mood.setVisibility(View.VISIBLE);
        weather.setAdapter(new ImageArrayAdapter(context, DiaryInfoHelper.getWeatherArray()));
        mood.setAdapter(new ImageArrayAdapter(context, DiaryInfoHelper.getMoodArray()));
        weather.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                cover.setWeather(position);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    public int getWeather() {
        return weather.getSelectedItemPosition();
    }

    public int getMood() {
        return mood.getSelectedItemPosition();
    }

    public void set(int moodPosition, int weatherPosition) {
        mood.setSelection(moodPosition);
        weather.setSelection(weatherPosition);
        cover.setWeather(weatherPosition);
    }
}
