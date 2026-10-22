package com.kiminonawa.mydiary.entries.diary.editor;

import android.net.Uri;
import android.widget.Toast;

import androidx.fragment.app.Fragment;

import com.kiminonawa.mydiary.R;
import com.kiminonawa.mydiary.entries.diary.CopyPhotoTask;
import com.kiminonawa.mydiary.entries.diary.item.DiaryItemHelper;
import com.kiminonawa.mydiary.shared.FileManager;

import java.util.LinkedList;
import java.util.List;

/**
 * Brings the photos that the person picks (from the gallery or from the camera) into the folder of the entry that is
 * being written, one at a time and in order. When a photo is ready the listener adds it to the entry.
 */
public class PhotoImporter implements CopyPhotoTask.CopyPhotoCallBack {

    public interface Listener {
        /**
         * A photo is ready in the folder of the entry.
         *
         * @param fileName name of the photo file
         */
        void onPhotoReady(String fileName);
    }

    private final Fragment host;
    private final Listener listener;
    private FileManager targetFolder;
    //Pictures picked from the gallery that are waiting to be copied
    private final LinkedList<Uri> pendingUris = new LinkedList<>();
    private boolean isCopying = false;

    public PhotoImporter(Fragment host, Listener listener) {
        this.host = host;
        this.listener = listener;
    }

    /**
     * The folder where the photos of the entry are put while it is written.
     */
    public void setTargetFolder(FileManager targetFolder) {
        this.targetFolder = targetFolder;
    }

    /**
     * Pictures chosen in the gallery.
     */
    public void addUris(List<Uri> uris) {
        //The check "is it really a picture" is done while decoding (see CopyPhotoTask),
        //because the file name can't tell it reliably.
        pendingUris.addAll(uris);
        copyNext();
    }

    /**
     * A picture that the camera app saved in the folder of the entry.
     */
    public void addCameraFile(String fileName) {
        //Get the saved file, turn it the right way up and make it smaller
        new CopyPhotoTask(host.getActivity(), fileName,
                DiaryItemHelper.getVisibleWidth(host.getActivity()), DiaryItemHelper.getVisibleHeight(host.getActivity()),
                targetFolder, this).execute();
    }

    private void copyNext() {
        if (isCopying || pendingUris.isEmpty() || host.getActivity() == null) {
            return;
        }
        isCopying = true;
        Uri uri = pendingUris.poll();
        new CopyPhotoTask(host.getActivity(), uri,
                DiaryItemHelper.getVisibleWidth(host.getActivity()), DiaryItemHelper.getVisibleHeight(host.getActivity()),
                targetFolder, this).execute();
    }

    @Override
    public void onCopyCompiled(String fileName) {
        isCopying = false;
        if (host.getActivity() == null) {
            pendingUris.clear();
            return;
        }
        if (fileName != null) {
            listener.onPhotoReady(fileName);
        } else {
            Toast.makeText(host.getActivity(), host.getString(R.string.toast_photo_load_failed), Toast.LENGTH_LONG).show();
        }
        //Next picture of the selection, if there is one
        copyNext();
    }
}
