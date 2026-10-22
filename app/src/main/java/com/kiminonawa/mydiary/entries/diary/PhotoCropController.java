package com.kiminonawa.mydiary.entries.diary;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

import androidx.fragment.app.Fragment;

import com.kiminonawa.mydiary.R;
import com.kiminonawa.mydiary.entries.diary.item.DiaryItemHelper;
import com.kiminonawa.mydiary.shared.FileManager;
import com.kiminonawa.mydiary.shared.ThemeManager;
import com.yalantis.ucrop.UCrop;

import java.io.File;

/**
 * "Crop to 4:3": the person chooses what part of a photo is seen (moving and zooming the photo under a 4:3 frame).
 * The cropped photo is a new file that takes the place of the old one in the entry (the original is still in the
 * gallery of the phone). This is used by the page of a new entry and by the window that edits an entry.
 */
public class PhotoCropController {

    //The photos are 4:3 everywhere (list, cover, entry)
    private static final int RATIO_W = 4;
    private static final int RATIO_H = 3;
    //Never bigger than this (the photos of the diary are not that big anyway)
    private static final int MAX_W = 2400;
    private static final int MAX_H = 1800;

    public interface Listener {
        /**
         * The entry shows another file now (update the cover, etc.).
         */
        void onPhotoCropped();
    }

    private final Fragment fragment;
    private final DiaryItemHelper diaryItemHelper;
    private final Listener listener;

    //The crop in progress
    private String pendingOldName;
    private File pendingOutput;

    public PhotoCropController(Fragment fragment, DiaryItemHelper diaryItemHelper, Listener listener) {
        this.fragment = fragment;
        this.diaryItemHelper = diaryItemHelper;
        this.listener = listener;
    }

    /**
     * Opens the screen to crop the photo.
     *
     * @param fileName name of the photo file (it is inside photoDir)
     * @param photoDir the folder where the photos of the entry that is being edited are
     */
    public void crop(String fileName, File photoDir) {
        Context context = fragment.getActivity();
        if (context == null || fileName == null) {
            return;
        }
        File source = new File(photoDir, fileName);
        if (!source.exists()) {
            Toast.makeText(context, R.string.toast_crop_photo_fail, Toast.LENGTH_LONG).show();
            return;
        }
        File output = new File(photoDir, FileManager.createRandomFileName());
        UCrop.Options options = new UCrop.Options();
        options.setToolbarColor(ThemeManager.getInstance().getThemeMainColor(context));
        options.setStatusBarColor(ThemeManager.getInstance().getThemeDarkColor(context));
        options.setToolbarTitle(context.getString(R.string.crop_photo_title));
        options.setCompressionQuality(92);
        options.setFreeStyleCropEnabled(false);
        pendingOldName = fileName;
        pendingOutput = output;
        UCrop.of(Uri.fromFile(source), Uri.fromFile(output))
                .withAspectRatio(RATIO_W, RATIO_H)
                .withMaxResultSize(MAX_W, MAX_H)
                .withOptions(options)
                .start(context, fragment);
    }

    /**
     * Call it from onActivityResult of the fragment.
     *
     * @return true when the result was for the crop screen
     */
    public boolean onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != UCrop.REQUEST_CROP) {
            return false;
        }
        Context context = fragment.getActivity();
        String oldName = pendingOldName;
        File output = pendingOutput;
        pendingOldName = null;
        pendingOutput = null;
        if (oldName == null || output == null || context == null) {
            return true;
        }
        if (resultCode == Activity.RESULT_OK && output.exists() && output.length() > 0) {
            Uri newUri = Uri.parse(FileManager.FILE_HEADER + output.getAbsolutePath());
            if (diaryItemHelper.replacePhotoFile(oldName, output.getName(), newUri)) {
                //The old file is not used any more
                //noinspection ResultOfMethodCallIgnored
                new File(output.getParentFile(), oldName).delete();
                Toast.makeText(context, R.string.toast_crop_photo_done, Toast.LENGTH_SHORT).show();
                if (listener != null) {
                    listener.onPhotoCropped();
                }
            } else {
                //The photo was deleted while cropping
                //noinspection ResultOfMethodCallIgnored
                output.delete();
            }
        } else {
            //Cancelled (or failed): nothing changes
            //noinspection ResultOfMethodCallIgnored
            output.delete();
            if (resultCode == UCrop.RESULT_ERROR) {
                Toast.makeText(context, R.string.toast_crop_photo_fail, Toast.LENGTH_LONG).show();
            }
        }
        return true;
    }
}
