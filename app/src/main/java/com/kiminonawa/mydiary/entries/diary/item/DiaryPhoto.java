package com.kiminonawa.mydiary.entries.diary.item;

import android.app.Activity;
import android.net.Uri;
import android.view.View;

import com.kiminonawa.mydiary.entries.diary.area.PhotoArea;
import com.kiminonawa.mydiary.shared.gui.DiaryPhotoLayout;

/**
 * Created by daxia on 2016/11/19.
 */

public class DiaryPhoto implements IDairyRow {

    private DiaryPhotoLayout diaryPhotoLayout;
    private String photoFileName;
    private Uri photoUri;
    private int position;
    //The part of the photo that is seen (the file is never cut)
    private PhotoArea area = PhotoArea.NONE;

    public DiaryPhoto(Activity activity) {
        diaryPhotoLayout = new DiaryPhotoLayout(activity);
        //Default is editable
        setEditMode(true);
    }

    /**
     * This tag is only used in view mode
     * @param draweeViewPositionTag
     */
    public void setDraweeViewPositionTag(int draweeViewPositionTag) {
        diaryPhotoLayout.setDraweeViewPositionTag(draweeViewPositionTag);
    }

    /**
     * Edit mode , the delete button
     *
     * @param clickListener
     */
    public void setDeleteClickListener(View.OnClickListener clickListener) {
        diaryPhotoLayout.setDeleteOnClick(clickListener);
    }

    /**
     * The view mode , you can click to open the large imgae
     *
     * @param clickListener
     */
    public void setDraweeViewClickListener(View.OnClickListener clickListener) {
        diaryPhotoLayout.setDraweeViewClick(clickListener);
    }


    /**
     * Edit mode: the star that makes this photo the cover of the diary.
     */
    public void setThumbnailClickListener(View.OnClickListener clickListener) {
        diaryPhotoLayout.setThumbnailOnClick(clickListener);
    }

    /**
     * Edit mode: the button that opens the screen to choose what part of the photo is seen.
     */
    public void setCropClickListener(View.OnClickListener clickListener) {
        diaryPhotoLayout.setCropOnClick(clickListener);
    }

    public void setThumbnailSelected(boolean selected) {
        diaryPhotoLayout.setThumbnailSelected(selected);
    }

    /**
     * @return complete address of the photo ("file:///..."), or null when it is not known yet
     */
    public String getPhotoUriString() {
        return photoUri == null ? null : photoUri.toString();
    }

    public void setPhoto(Uri photoUri, String photoFileName) {
        this.photoUri = photoUri;
        this.photoFileName = photoFileName;
        diaryPhotoLayout.setPhotoUri(photoUri, area.body);
    }

    /**
     * The part of the photo that is seen inside the entry (4:3) and on the cover (4:5).
     */
    public PhotoArea getArea() {
        return area;
    }

    public void setArea(PhotoArea area) {
        this.area = area == null ? PhotoArea.NONE : area;
        if (photoUri != null) {
            diaryPhotoLayout.setPhotoUri(photoUri, this.area.body);
        }
    }


    public void setPhotoFileName(String photoFileName) {
        this.photoFileName = photoFileName;
    }

    @Override
    public void setContent(String content) {
        this.photoUri = Uri.parse(content);
        diaryPhotoLayout.setPhotoUri(photoUri, area.body);
    }

    @Override
    public void setPosition(int position) {
        this.position = position;
        //When  content is modified(e.g.insert or delete) , update setDeletePositionTag
        diaryPhotoLayout.setDeletePositionTag(position);
    }

    @Override
    public int getPosition() {
        return position;
    }

    @Override
    public int getType() {
        return TYPE_PHOTO;
    }

    @Override
    public View getView() {
        return diaryPhotoLayout;
    }

    @Override
    public void setEditMode(boolean isEditMode) {
        diaryPhotoLayout.setVisibleViewByMode(isEditMode);
    }

    @Override
    public String getContent() {
        return photoFileName;
    }

}
