package com.kiminonawa.mydiary.entries.diary;

import com.kiminonawa.mydiary.R;

/**
 * Created by daxia on 2016/10/31.
 */

public class DiaryInfoHelper {

    public final static int WEATHER_SIZE = 10;
    public final static int WEATHER_SUNNY = 0;
    public final static int WEATHER_CLOUD = 1;
    public final static int WEATHER_WINDY = 2;
    public final static int WEATHER_RAINY = 3;
    public final static int WEATHER_SNOWY = 4;
    public final static int WEATHER_FOGGY = 5;
    //New ones: always add at the end, the number is what is saved in the database
    public final static int WEATHER_PARTLY_CLOUDY = 6;
    public final static int WEATHER_STORMY = 7;
    public final static int WEATHER_NIGHT = 8;
    public final static int WEATHER_HOT = 9;


    public final static int MOOD_SIZE = 9;
    public final static int MOOD_HAPPY = 0;
    public final static int MOOD_SOSO = 1;
    public final static int MOOD_UNHAPPY = 2;
    //New ones: always add at the end, the number is what is saved in the database
    public final static int MOOD_VERY_HAPPY = 3;
    public final static int MOOD_VERY_SAD = 4;
    public final static int MOOD_ANGRY = 5;
    public final static int MOOD_LOVE = 6;
    public final static int MOOD_SLEEPY = 7;
    public final static int MOOD_SURPRISED = 8;


    /**
     * Weather
     */
    public static int getWeatherResourceId(int weather) {
        int weatherResourceId;
        switch (weather) {
            default:
                weatherResourceId = R.drawable.ic_weather_sunny;
                break;
            case WEATHER_CLOUD:
                weatherResourceId = R.drawable.ic_weather_cloud;
                break;
            case WEATHER_WINDY:
                weatherResourceId = R.drawable.ic_weather_windy;
                break;
            case WEATHER_RAINY:
                weatherResourceId = R.drawable.ic_weather_rainy;
                break;
            case WEATHER_SNOWY:
                weatherResourceId = R.drawable.ic_weather_snowy;
                break;
            case WEATHER_FOGGY:
                weatherResourceId = R.drawable.ic_weather_foggy;
                break;
            case WEATHER_PARTLY_CLOUDY:
                weatherResourceId = R.drawable.ic_weather_partly_cloudy;
                break;
            case WEATHER_STORMY:
                weatherResourceId = R.drawable.ic_weather_stormy;
                break;
            case WEATHER_NIGHT:
                weatherResourceId = R.drawable.ic_weather_night;
                break;
            case WEATHER_HOT:
                weatherResourceId = R.drawable.ic_weather_hot;
                break;
        }
        return weatherResourceId;
    }


    public static Integer[] getWeatherArray() {
        return new Integer[]{R.drawable.ic_weather_sunny, R.drawable.ic_weather_cloud,
                R.drawable.ic_weather_windy, R.drawable.ic_weather_rainy, R.drawable.ic_weather_snowy,
                R.drawable.ic_weather_foggy, R.drawable.ic_weather_partly_cloudy,
                R.drawable.ic_weather_stormy, R.drawable.ic_weather_night, R.drawable.ic_weather_hot};
    }


    /**
     * Mood
     */
    public static int getMoodResourceId(int mood) {
        int moodResourceId;
        switch (mood) {
            default:
                moodResourceId = R.drawable.ic_mood_happy;
                break;
            case MOOD_SOSO:
                moodResourceId = R.drawable.ic_mood_soso;
                break;
            case MOOD_UNHAPPY:
                moodResourceId = R.drawable.ic_mood_unhappy;
                break;
            case MOOD_VERY_HAPPY:
                moodResourceId = R.drawable.ic_mood_very_happy;
                break;
            case MOOD_VERY_SAD:
                moodResourceId = R.drawable.ic_mood_very_sad;
                break;
            case MOOD_ANGRY:
                moodResourceId = R.drawable.ic_mood_angry;
                break;
            case MOOD_LOVE:
                moodResourceId = R.drawable.ic_mood_love;
                break;
            case MOOD_SLEEPY:
                moodResourceId = R.drawable.ic_mood_sleepy;
                break;
            case MOOD_SURPRISED:
                moodResourceId = R.drawable.ic_mood_surprised;
                break;
        }
        return moodResourceId;
    }

    public static Integer[] getMoodArray() {
        return new Integer[]{R.drawable.ic_mood_happy, R.drawable.ic_mood_soso,
                R.drawable.ic_mood_unhappy, R.drawable.ic_mood_very_happy, R.drawable.ic_mood_very_sad,
                R.drawable.ic_mood_angry, R.drawable.ic_mood_love, R.drawable.ic_mood_sleepy,
                R.drawable.ic_mood_surprised};
    }


}
