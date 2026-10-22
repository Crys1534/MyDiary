package com.kiminonawa.mydiary.shared.gui;

import android.app.Activity;
import android.graphics.RectF;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;

import com.facebook.drawee.view.SimpleDraweeView;
import com.kiminonawa.mydiary.R;
import com.kiminonawa.mydiary.entries.diary.area.AreaImage;
import com.kiminonawa.mydiary.entries.diary.item.DiaryItemHelper;

/**
 * Created by daxia on 2016/11/20.
 */

public class DiaryPhotoLayout extends LinearLayout {

    //The photos are shown in a 4:3 frame (the person chooses what part; the complete photo is seen when it is opened)
    private static final float PHOTO_ASPECT_RATIO = 4f / 3f;

    private SimpleDraweeView SDV_diary_new_photo;
    private ImageView IV_diary_photo_delete, IV_diary_photo_thumbnail, IV_diary_photo_crop;

    public DiaryPhotoLayout(Activity activity) {
        super(activity);
        View v = LayoutInflater.from(activity).inflate(R.layout.layout_diaryphoto, this, true);
        SDV_diary_new_photo = (SimpleDraweeView) v.findViewById(R.id.SDV_diary_new_photo);
        SDV_diary_new_photo.setAspectRatio(PHOTO_ASPECT_RATIO);
        IV_diary_photo_delete = (ImageView) v.findViewById(R.id.IV_diary_photo_delete);
        IV_diary_photo_thumbnail = (ImageView) v.findViewById(R.id.IV_diary_photo_thumbnail);
        IV_diary_photo_crop = (ImageView) v.findViewById(R.id.IV_diary_photo_crop);
    }

    /**
     * @param body the part of the photo that is seen (fractions of the photo), or null for the center
     */
    public void setPhotoUri(Uri photoUri, RectF body) {
        int width = DiaryItemHelper.getVisibleWidth(getContext());
        AreaImage.show(SDV_diary_new_photo, photoUri, body, width, (int) (width / PHOTO_ASPECT_RATIO));
    }

    public void setDeleteOnClick(OnClickListener listener) {
        IV_diary_photo_delete.setOnClickListener(listener);
    }

    public void setDeletePositionTag(int position) {
        IV_diary_photo_delete.setTag(position);
        IV_diary_photo_thumbnail.setTag(position);
        IV_diary_photo_crop.setTag(position);
    }

    /**
     * Edit mode: the star that makes this photo the cover of the diary.
     */
    public void setThumbnailOnClick(OnClickListener listener) {
        IV_diary_photo_thumbnail.setOnClickListener(listener);
    }

    /**
     * Edit mode: the button that opens the screen to choose what part of this photo is seen.
     */
    public void setCropOnClick(OnClickListener listener) {
        IV_diary_photo_crop.setOnClickListener(listener);
    }

    public void setThumbnailSelected(boolean selected) {
        IV_diary_photo_thumbnail.setImageResource(selected
                ? R.drawable.ic_star_white_24dp : R.drawable.ic_star_border_white_24dp);
    }

    public void setDraweeViewClick(OnClickListener listener) {
        SDV_diary_new_photo.setOnClickListener(listener);
    }

    public void setDraweeViewPositionTag(int position) {
        SDV_diary_new_photo.setTag(position);
    }

    public void setVisibleViewByMode(boolean isEditMode) {
        if (isEditMode) {
            IV_diary_photo_delete.setVisibility(VISIBLE);
            IV_diary_photo_thumbnail.setVisibility(VISIBLE);
            IV_diary_photo_crop.setVisibility(VISIBLE);
        } else {
            IV_diary_photo_delete.setVisibility(GONE);
            IV_diary_photo_thumbnail.setVisibility(GONE);
            IV_diary_photo_crop.setVisibility(GONE);
        }
    }

    public SimpleDraweeView getPhoto() {
        return SDV_diary_new_photo;
    }


}
