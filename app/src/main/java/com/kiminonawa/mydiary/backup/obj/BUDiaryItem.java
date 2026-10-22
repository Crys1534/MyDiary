package com.kiminonawa.mydiary.backup.obj;

/**
 * Created by daxia on 2017/2/14.
 */

public class BUDiaryItem {


    private int diary_item_type;
    private int diary_item_position;
    private String diary_item_content;
    //Photos: the part of the photo that is seen (older backups do not have it: null)
    private String diary_item_area;

    public BUDiaryItem(int diary_item_type, int diary_item_position, String diary_item_content) {
        this(diary_item_type, diary_item_position, diary_item_content, "");
    }

    public BUDiaryItem(int diary_item_type, int diary_item_position, String diary_item_content,
                       String diary_item_area) {
        this.diary_item_type = diary_item_type;
        this.diary_item_position = diary_item_position;
        this.diary_item_content = diary_item_content;
        this.diary_item_area = diary_item_area;
    }

    public String getDiaryItemArea() {
        return diary_item_area == null ? "" : diary_item_area;
    }

    public int getDiaryItemType() {
        return diary_item_type;
    }

    public int getDiaryItemPosition() {
        return diary_item_position;
    }

    public String getDiaryItemContent() {
        return diary_item_content;
    }
}
