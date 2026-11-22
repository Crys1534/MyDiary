package com.kiminonawa.mydiary.entries.diary;

import android.app.Dialog;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import androidx.annotation.Nullable;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import androidx.core.content.FileProvider;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.kiminonawa.mydiary.R;
import com.kiminonawa.mydiary.entries.DiaryActivity;
import com.kiminonawa.mydiary.shared.FileManager;
import com.kiminonawa.mydiary.shared.ThemeManager;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static android.app.Activity.RESULT_OK;

/**
 * Created by daxia on 2016/11/19.
 */

public class DiaryPhotoBottomSheet extends BottomSheetDialogFragment implements View.OnClickListener {

    public interface PhotoCallBack {
        void addPhoto(String fileName);

        void selectPhoto(Uri uri);

        /**
         * One or more pictures chosen from the gallery, to be added in order.
         */
        void selectPhotos(List<Uri> uris);

        /**
         * One or more videos chosen from the gallery, to be added in order.
         */
        void selectVideos(List<Uri> uris);

        /**
         * A new gallery of photos: choose its design, then its photos.
         */
        void newGallery();
    }

    private RelativeLayout RL_diary_photo_dialog;
    private ImageView IV_diary_photo_add_a_photo, IV_diary_photo_select_a_photo;

    /**
     * Camera & select photo
     */
    private static final int REQUEST_START_CAMERA_CODE = 1;
    private static final int REQUEST_SELECT_IMAGE_CODE = 2;

    /**
     * File
     */
    private FileManager fileManager;
    private String tempFileName;

    private PhotoCallBack callBack;


    public static DiaryPhotoBottomSheet newInstance(boolean isEditMode) {
        return newInstance(isEditMode, true);
    }

    /**
     * @param allowVideo false when choosing the cover (only a photo can be the cover)
     */
    public static DiaryPhotoBottomSheet newInstance(boolean isEditMode, boolean allowVideo) {
        Bundle args = new Bundle();
        args.putBoolean("allowVideo", allowVideo);
        DiaryPhotoBottomSheet fragment = new DiaryPhotoBottomSheet();
        args.putBoolean("isEditMode", isEditMode);
        fragment.setArguments(args);
        return fragment;
    }


    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Dialog dialog = super.onCreateDialog(savedInstanceState);
        if (getArguments().getBoolean("isEditMode", false)) {
            fileManager = new FileManager(getActivity(), FileManager.DIARY_EDIT_CACHE_DIR);
        } else {
            fileManager = new FileManager(getActivity(), ((DiaryActivity) getActivity()).getTopicId());
        }
        try {
            callBack = (PhotoCallBack) getTargetFragment();
        } catch (ClassCastException e) {
            e.printStackTrace();
            Toast.makeText(getActivity(), getString(R.string.toast_photo_intent_error), Toast.LENGTH_LONG).show();
        }
        return dialog;
    }

    private boolean allowVideo() {
        return getArguments() == null || getArguments().getBoolean("allowVideo", true);
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        this.getDialog().setCanceledOnTouchOutside(true);
        View rootView = inflater.inflate(R.layout.bottom_sheet_diary_photo, container);
        RL_diary_photo_dialog = (RelativeLayout) rootView.findViewById(R.id.RL_diary_photo_dialog);
        RL_diary_photo_dialog.setBackgroundColor(ThemeManager.getInstance().getThemeMainColor(getActivity()));

        IV_diary_photo_add_a_photo = (ImageView) rootView.findViewById(R.id.IV_diary_photo_add_a_photo);
        IV_diary_photo_add_a_photo.setOnClickListener(this);
        IV_diary_photo_select_a_photo = (ImageView) rootView.findViewById(R.id.IV_diary_photo_select_a_photo);
        IV_diary_photo_select_a_photo.setOnClickListener(this);
        View videoButton = rootView.findViewById(R.id.IV_diary_photo_select_a_video);
        View galleryButton = rootView.findViewById(R.id.IV_diary_photo_gallery);
        if (allowVideo()) {
            videoButton.setOnClickListener(this);
            galleryButton.setOnClickListener(this);
        } else {
            //Choosing the cover: only one photo
            ((View) videoButton.getParent()).setVisibility(View.GONE);
            ((View) galleryButton.getParent()).setVisibility(View.GONE);
        }

        return rootView;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_START_CAMERA_CODE) {
            if (resultCode == RESULT_OK) {
                callBack.addPhoto(tempFileName);
            }
            dismiss();
        } else if (requestCode == REQUEST_SELECT_IMAGE_CODE) {
            if (resultCode == RESULT_OK) {
                //Several pictures come in the ClipData, a single one may come in getData()
                List<Uri> uris = new ArrayList<>();
                if (data != null) {
                    ClipData clipData = data.getClipData();
                    if (clipData != null) {
                        for (int i = 0; i < clipData.getItemCount(); i++) {
                            Uri itemUri = clipData.getItemAt(i).getUri();
                            if (itemUri != null) {
                                uris.add(itemUri);
                            }
                        }
                    }
                    if (uris.isEmpty() && data.getData() != null) {
                        uris.add(data.getData());
                    }
                }
                //The gallery can give photos and videos together
                List<Uri> videos = new ArrayList<>();
                if (allowVideo() && getActivity() != null) {
                    for (int i = uris.size() - 1; i >= 0; i--) {
                        if (com.kiminonawa.mydiary.entries.diary.editor.VideoImporter.isVideo(
                                getActivity().getContentResolver(), uris.get(i))) {
                            videos.add(0, uris.remove(i));
                        }
                    }
                }
                if (!videos.isEmpty() && callBack != null) {
                    callBack.selectVideos(videos);
                }
                //fix the ZenPhone C & HTC 626 crash issues
                if (!videos.isEmpty() && uris.isEmpty()) {
                    //Only videos: nothing more to do
                } else if (!uris.isEmpty() && callBack != null) {
                    callBack.selectPhotos(uris);
                } else {
                    Toast.makeText(getActivity(), getString(R.string.toast_photo_intent_error), Toast.LENGTH_LONG).show();
                }
            }
            dismiss();
        }
    }


    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.IV_diary_photo_add_a_photo:
                Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                tempFileName = "/" + fileManager.createRandomFileName();
                File tmpFile = new File(fileManager.getDir(), tempFileName);
                Uri outputFileUri;
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                    outputFileUri = Uri.fromFile(tmpFile);
                } else {
                    //Fix the Android N+ file can't be send
                    outputFileUri = FileProvider.getUriForFile(getActivity(),
                            getActivity().getApplicationContext().getPackageName() + ".provider", tmpFile);
                }
                intent.putExtra(MediaStore.EXTRA_OUTPUT, outputFileUri);
                try {
                    startActivityForResult(intent, REQUEST_START_CAMERA_CODE);
                } catch (android.content.ActivityNotFoundException e) {
                    //No camera app in the phone
                    Toast.makeText(getActivity(), getString(R.string.toast_photo_intent_error), Toast.LENGTH_LONG).show();
                }
                break;
            case R.id.IV_diary_photo_select_a_photo:
                if (allowVideo()) {
                    //Photos and videos of the gallery
                    FileManager.startBrowseMultipleMediaFiles(this, REQUEST_SELECT_IMAGE_CODE, true);
                } else {
                    FileManager.startBrowseMultipleImageFiles(this, REQUEST_SELECT_IMAGE_CODE);
                }
                break;
            case R.id.IV_diary_photo_gallery:
                if (callBack != null) {
                    callBack.newGallery();
                }
                dismiss();
                break;
            case R.id.IV_diary_photo_select_a_video:
                //Only the videos of the gallery
                FileManager.startBrowseMultipleMediaFiles(this, REQUEST_SELECT_IMAGE_CODE, false);
                break;
        }
    }
}
