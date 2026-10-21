package com.kiminonawa.mydiary.backup.obj;

import java.util.List;

/**
 * Created by daxia on 2017/2/14.
 */

public class BUDiaryEntries {


    public final static long NO_BU_DIARY_ID = -1;
    public final static long NO_BU_DIARY_TIME = -1;

    private long diary_entries_id;
    private String diary_entries_title;
    private long diary_entries_time;
    private int diary_entries_mood, diary_entries_weather;
    private boolean diary_entries_attachment;
    private String diary_entries_location;
    //Tags like ",school,trip," (missing in backups made by older versions)
    private String diary_entries_tags;
    //File name of the photo chosen as the cover (missing in older backups: the first photo is used)
    private String diary_entries_thumbnail;
    private List<BUDiaryItem> diary_item_list;

    public BUDiaryEntries(long diary_entries_id, long diary_entries_time, String diary_entries_title,
                          int diary_entries_mood, int diary_entries_weather,
                          boolean diary_entries_attachment, String diary_entries_location,
                          String diary_entries_tags, String diary_entries_thumbnail,
                          List<BUDiaryItem> diary_item_list) {
        this.diary_entries_id = diary_entries_id;
        this.diary_entries_time = diary_entries_time;
        this.diary_entries_title = diary_entries_title;
        this.diary_entries_mood = diary_entries_mood;
        this.diary_entries_weather = diary_entries_weather;
        this.diary_entries_attachment = diary_entries_attachment;
        this.diary_entries_location = diary_entries_location;
        this.diary_entries_tags = diary_entries_tags;
        this.diary_entries_thumbnail = diary_entries_thumbnail;
        this.diary_item_list = diary_item_list;
    }

    public long getDiaryEntriesId() {
        return diary_entries_id;
    }

    public long getDiaryEntriesTime() {
        return diary_entries_time;
    }

    public String getDiaryEntriesTitle() {
        return diary_entries_title;
    }

    public int getDiaryEntriesMood() {
        return diary_entries_mood;
    }

    public int getDiaryEntriesWeather() {
        return diary_entries_weather;
    }

    public boolean isDiaryEntriesAttachment() {
        return diary_entries_attachment;
    }

    public String getDiaryEntriesLocation() {
        return diary_entries_location;
    }

    public String getDiaryEntriesTags() {
        return diary_entries_tags == null ? "" : diary_entries_tags;
    }

    public String getDiaryEntriesThumbnail() {
        return diary_entries_thumbnail == null ? "" : diary_entries_thumbnail;
    }

    public List<BUDiaryItem> getDiaryItemList() {
        return diary_item_list;
    }
}
