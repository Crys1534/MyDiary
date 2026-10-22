package com.kiminonawa.mydiary.entries.diary.area;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.RectF;
import android.widget.Toast;

import androidx.fragment.app.Fragment;

import com.kiminonawa.mydiary.R;
import com.kiminonawa.mydiary.entries.diary.item.DiaryItemHelper;
import com.kiminonawa.mydiary.entries.diary.item.DiaryPhoto;
import com.kiminonawa.mydiary.shared.gui.AspectRatioRelativeLayout;

import java.io.File;

/**
 * "Choose what part is seen": opens the screen where the person moves and zooms a photo under a frame
 * (4:3 for the photos of the entry, 4:5 for the cover) and keeps the answer in the photo item.
 * The photo file is never changed, so nothing is lost and it can be chosen again any time.
 * Used by the page of a new entry and by the window that edits an entry.
 */
public class AreaPickerController {

    private static final int REQUEST_AREA = 7501;
    //The photos of the entry are wide (4:3)
    private static final float BODY_RATIO = 4f / 3f;

    public interface Listener {
        /**
         * The visible area of a photo changed (update the cover, etc.).
         */
        void onAreaChanged();
    }

    private final Fragment fragment;
    private final DiaryItemHelper diaryItemHelper;
    private final Listener listener;

    //The choice in progress
    private String pendingFileName;
    private boolean pendingForCover;

    public AreaPickerController(Fragment fragment, DiaryItemHelper diaryItemHelper, Listener listener) {
        this.fragment = fragment;
        this.diaryItemHelper = diaryItemHelper;
        this.listener = listener;
    }

    /**
     * For a photo inside the entry (4:3).
     *
     * @param photoDir the folder where the photos of the entry that is being edited are
     */
    public void pickForBody(DiaryPhoto photo, File photoDir) {
        open(photo, photoDir, false);
    }

    /**
     * For the cover (4:5).
     */
    public void pickForCover(DiaryPhoto photo, File photoDir) {
        open(photo, photoDir, true);
    }

    private void open(DiaryPhoto photo, File photoDir, boolean forCover) {
        Context context = fragment.getActivity();
        if (context == null || photo == null || photo.getContent() == null) {
            return;
        }
        File source = new File(photoDir, photo.getContent());
        if (!source.exists()) {
            Toast.makeText(context, R.string.toast_area_fail, Toast.LENGTH_LONG).show();
            return;
        }
        RectF current = forCover ? photo.getArea().cover : photo.getArea().body;
        Intent intent = new Intent(context, AreaPickerActivity.class);
        intent.putExtra(AreaPickerActivity.EXTRA_PATH, source.getAbsolutePath());
        intent.putExtra(AreaPickerActivity.EXTRA_RATIO, forCover
                ? (float) AspectRatioRelativeLayout.COVER_RATIO_W / AspectRatioRelativeLayout.COVER_RATIO_H
                : BODY_RATIO);
        intent.putExtra(AreaPickerActivity.EXTRA_TITLE,
                context.getString(forCover ? R.string.area_title_cover : R.string.area_title_body));
        if (current != null) {
            intent.putExtra(AreaPickerActivity.EXTRA_AREA, PhotoArea.formatRect(current));
        }
        pendingFileName = photo.getContent();
        pendingForCover = forCover;
        fragment.startActivityForResult(intent, REQUEST_AREA);
    }

    /**
     * Call it from onActivityResult of the fragment.
     *
     * @return true when the result was for the area screen
     */
    public boolean onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != REQUEST_AREA) {
            return false;
        }
        Context context = fragment.getActivity();
        String fileName = pendingFileName;
        boolean forCover = pendingForCover;
        pendingFileName = null;
        if (fileName == null || context == null || resultCode != Activity.RESULT_OK || data == null) {
            return true;
        }
        DiaryPhoto photo = diaryItemHelper.findPhoto(fileName);
        if (photo == null) {
            //The photo was deleted while choosing
            return true;
        }
        RectF area = PhotoArea.parseRect(data.getStringExtra(AreaPickerActivity.EXTRA_AREA));
        if (forCover) {
            photo.setArea(photo.getArea().withCover(area));
        } else {
            photo.setArea(photo.getArea().withBody(area));
        }
        Toast.makeText(context, R.string.toast_area_done, Toast.LENGTH_SHORT).show();
        if (listener != null) {
            listener.onAreaChanged();
        }
        return true;
    }
}
