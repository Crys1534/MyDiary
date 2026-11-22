package com.kiminonawa.mydiary.entries.diary.editor;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;

import androidx.fragment.app.Fragment;

import com.kiminonawa.mydiary.entries.diary.item.DiaryGallery;
import com.kiminonawa.mydiary.shared.FileManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Photos for a gallery: first the design (for a new gallery), then the photos of the phone. They are copied into the
 * folder of the entry and the gallery is put where the cursor is (or the photos fill the empty places of a gallery).
 * Photos that do not fit make more galleries with the same design.
 */
public class GalleryPicker {

    public static final int REQUEST_GALLERY_PHOTOS = 7301;

    private final Fragment host;
    private final PhotoImporter importer;
    private final EditorActions actions;
    //null: a new gallery
    private DiaryGallery target;
    private String layout = DiaryGallery.LAYOUT_2X2;

    public GalleryPicker(Fragment host, PhotoImporter importer, EditorActions actions) {
        this.host = host;
        this.importer = importer;
        this.actions = actions;
    }

    /**
     * A new gallery: choose its design, then its photos.
     */
    public void startNew() {
        if (host.getActivity() == null) {
            return;
        }
        DiaryGallery.chooseLayout(host.getActivity(), layout, new DiaryGallery.LayoutChosen() {
            @Override
            public void onLayoutChosen(String chosen) {
                target = null;
                layout = chosen;
                FileManager.startBrowseMultipleImageFiles(host, REQUEST_GALLERY_PHOTOS);
            }
        });
    }

    /**
     * More photos for the empty places of a gallery.
     */
    public void addTo(DiaryGallery gallery) {
        target = gallery;
        layout = gallery.getLayout();
        FileManager.startBrowseMultipleImageFiles(host, REQUEST_GALLERY_PHOTOS);
    }

    public boolean onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != REQUEST_GALLERY_PHOTOS) {
            return false;
        }
        if (resultCode != Activity.RESULT_OK || data == null) {
            return true;
        }
        List<Uri> uris = new ArrayList<>();
        ClipData clip = data.getClipData();
        if (clip != null) {
            for (int i = 0; i < clip.getItemCount(); i++) {
                if (clip.getItemAt(i).getUri() != null) {
                    uris.add(clip.getItemAt(i).getUri());
                }
            }
        } else if (data.getData() != null) {
            uris.add(data.getData());
        }
        final DiaryGallery into = target;
        final String design = layout;
        importer.addUrisAsBatch(uris, new PhotoImporter.BatchListener() {
            @Override
            public void onBatchReady(List<String> fileNames) {
                List<String> rest = fileNames;
                if (into != null) {
                    rest = into.addPhotos(fileNames);
                }
                actions.insertGalleries(design, rest);
            }
        });
        return true;
    }
}
