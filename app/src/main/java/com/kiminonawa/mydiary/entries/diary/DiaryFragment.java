package com.kiminonawa.mydiary.entries.diary;


import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.kiminonawa.mydiary.shared.Sfx;
import com.kiminonawa.mydiary.R;
import com.kiminonawa.mydiary.backup.obj.BUDiaryEntries;
import com.kiminonawa.mydiary.backup.obj.BUDiaryItem;
import com.kiminonawa.mydiary.entries.BaseDiaryFragment;
import com.kiminonawa.mydiary.entries.DiaryActivity;
import com.kiminonawa.mydiary.entries.diary.area.AreaPickerController;
import com.kiminonawa.mydiary.entries.diary.editor.CoverCollapser;
import com.kiminonawa.mydiary.entries.diary.editor.DiaryItemFactory;
import com.kiminonawa.mydiary.entries.diary.editor.EditorActions;
import com.kiminonawa.mydiary.entries.diary.editor.EntryDateField;
import com.kiminonawa.mydiary.entries.diary.editor.LocationField;
import com.kiminonawa.mydiary.entries.diary.editor.AudioFileImporter;
import com.kiminonawa.mydiary.entries.diary.editor.PhotoImporter;
import com.kiminonawa.mydiary.entries.diary.editor.TitleTagsField;
import com.kiminonawa.mydiary.entries.diary.editor.WeatherMoodField;
import com.kiminonawa.mydiary.entries.diary.item.DiaryItemHelper;
import com.kiminonawa.mydiary.entries.diary.item.DiaryText;
import com.kiminonawa.mydiary.entries.diary.item.IDairyRow;
import com.kiminonawa.mydiary.shared.FileManager;
import com.kiminonawa.mydiary.shared.PermissionHelper;
import com.kiminonawa.mydiary.shared.SPFManager;
import com.kiminonawa.mydiary.shared.ThemeManager;
import com.kiminonawa.mydiary.shared.PaperStyle;
import com.kiminonawa.mydiary.shared.ViewTools;
import com.kiminonawa.mydiary.shared.gui.AspectRatioRelativeLayout;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Observable;
import java.util.Observer;

import static com.kiminonawa.mydiary.backup.obj.BUDiaryEntries.NO_BU_DIARY_ID;
import static com.kiminonawa.mydiary.backup.obj.BUDiaryEntries.NO_BU_DIARY_TIME;
import static com.kiminonawa.mydiary.shared.PermissionHelper.REQUEST_CAMERA_AND_WRITE_ES_PERMISSION;


/**
 * This page doesn't be used in the movie.
 * I define this page for write diary.
 * <p>
 * The pieces that are the same in the window that edits an old entry (place, photos, day, title and tags, weather and
 * mood, the buttons of the editor, the rows) live in the package "editor", so they are written only once.
 */

public class DiaryFragment extends BaseDiaryFragment implements View.OnClickListener,
        DiaryPhotoBottomSheet.PhotoCallBack, Observer, SaveDiaryTask.SaveDiaryCallBack,
        EditorActions.Host, PhotoImporter.Listener {


    private String TAG = "DiaryFragment";

    /**
     * UI
     */
    private LinearLayout LL_diary_item_content;
    private TextView TV_diary_item_content_hint;
    private ImageView IV_diary_photo, IV_diary_save;
    //The cover (thumbnail, date, title, tags)
    private DiaryCover diaryCover;
    //Folds the cover away while the person types
    private CoverCollapser coverCollapser;

    /**
     * The parts of the entry that are written
     */
    private TitleTagsField titleTags;
    private WeatherMoodField weatherMood;
    private EntryDateField dateField;
    private LocationField locationField;

    /**
     * Permission
     */
    private boolean firstAllowCameraPermission = false;

    /**
     * diary item
     */
    private DiaryItemHelper diaryItemHelper;
    private EditorActions editorActions;
    private AreaPickerController areaPicker;
    private PhotoImporter photoImporter;
    private AudioFileImporter audioImporter;
    //The photo that is being chosen is for the cover only (it is not put in the text)
    private boolean pickingCover = false;
    /**
     * File
     */
    private FileManager diaryTempFileManager;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        diaryTempFileManager = new FileManager(getActivity(), getTopicId());
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_diary, container, false);

        ScrollView ScrollView_diary_content = (ScrollView) rootView.findViewById(R.id.ScrollView_diary_content);
        ViewTools.setScrollBarColor(getActivity(), ScrollView_diary_content);
        //Notebook sheet (Settings): paper behind the entry
        PaperStyle.applyToPage(getActivity(), rootView.findViewById(R.id.RL_diary_page),
                rootView.findViewById(R.id.RL_diary_content));

        int mainColor = ThemeManager.getInstance().getThemeMainColor(getActivity());
        RelativeLayout RL_diary_info = (RelativeLayout) rootView.findViewById(R.id.RL_diary_info);
        RL_diary_info.setBackgroundColor(mainColor);
        LinearLayout LL_diary_edit_bar = (LinearLayout) rootView.findViewById(R.id.LL_diary_edit_bar);
        LL_diary_edit_bar.setBackgroundColor(mainColor);

        rootView.findViewById(R.id.LL_diary_time_information).setOnClickListener(this);

        diaryCover = new DiaryCover(rootView, new DiaryCover.Listener() {
            @Override
            public void onEditTagsRequested() {
                //The pencil of the cover: a small window to write the tags (the hidden box keeps them)
                titleTags.editTags(getActivity());
            }
        });
        diaryCover.setEditMode(true);
        coverCollapser = new CoverCollapser(rootView,
                (AspectRatioRelativeLayout) rootView.findViewById(R.id.RL_diary_info));
        titleTags = new TitleTagsField(rootView, diaryCover, mainColor);
        weatherMood = new WeatherMoodField(getActivity(), rootView, diaryCover);
        dateField = new EntryDateField(getActivity(), rootView, diaryCover);
        locationField = new LocationField(this, rootView, true);

        TV_diary_item_content_hint = (TextView) rootView.findViewById(R.id.TV_diary_item_content_hint);
        //For create diary
        LL_diary_item_content = (LinearLayout) rootView.findViewById(R.id.LL_diary_item_content);
        LL_diary_item_content.setOnClickListener(this);

        IV_diary_photo = (ImageView) rootView.findViewById(R.id.IV_diary_photo);
        IV_diary_photo.setOnClickListener(this);
        IV_diary_save = (ImageView) rootView.findViewById(R.id.IV_diary_save);
        IV_diary_save.setOnClickListener(this);

        return rootView;
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        diaryItemHelper = new DiaryItemHelper(LL_diary_item_content);
        areaPicker = new AreaPickerController(this, diaryItemHelper, new AreaPickerController.Listener() {
            @Override
            public void onAreaChanged() {
                updateCover();
            }
        });
        editorActions = new EditorActions(this, diaryItemHelper, LL_diary_item_content, areaPicker);
        editorActions.bindBar(view);
        photoImporter = new PhotoImporter(this, this);
        photoImporter.setTargetFolder(diaryTempFileManager);
        audioImporter = new AudioFileImporter(this, new AudioFileImporter.Listener() {
            @Override
            public void onAudioReady(String fileName, String displayName) {
                editorActions.insertLocalAudio(fileName, displayName);
            }
        });
        audioImporter.setTargetFolder(diaryTempFileManager);
        diaryCover.setPickAreaListener(new Runnable() {
            @Override
            public void run() {
                areaPicker.pickForCover(diaryItemHelper.getThumbnailPhoto(), diaryTempFileManager.getDir());
            }
        });
        diaryCover.setPickPhotoListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                editorActions.showCoverPhotoMenu(v, new Runnable() {
                    @Override
                    public void run() {
                        addPhotoFromButton(true);
                    }
                });
            }
        });
        dateField.setNow();
        clearDiaryPage();
        //Revert the auto saved diary
        revertAutoSaveDiary();
    }


    @Override
    public void onStart() {
        super.onStart();
        diaryItemHelper.addObserver(this);
        coverCollapser.start();
    }

    @Override
    public void onResume() {
        super.onResume();
        //For PermissionsResult
        locationField.onResume();
        if (firstAllowCameraPermission) {
            openPhotoBottomSheet();
            firstAllowCameraPermission = false;
        }
    }


    @Override
    public void onPause() {
        super.onPause();
        //Auto Save the diary
        autoSaveDiary();
    }

    @Override
    public void onStop() {
        super.onStop();
        //Release the resource
        diaryItemHelper.deleteObserver(this);
        coverCollapser.stop();
        diaryItemHelper.releaseAudio();
        locationField.onStop();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        //The screen where the part of a photo that is seen is chosen is closed
        if (areaPicker != null && areaPicker.onActivityResult(requestCode, resultCode, data)) {
            return;
        }
        //The audio file that was chosen on the phone
        if (audioImporter != null && audioImporter.onActivityResult(requestCode, resultCode, data)) {
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        if (locationField.onRequestPermissionsResult(requestCode, grantResults)) {
            return;
        }
        if (requestCode == PermissionHelper.REQUEST_CAMERA_AND_WRITE_ES_PERMISSION) {
            if (grantResults.length > 0
                    && PermissionHelper.checkAllPermissionResult(grantResults)) {
                firstAllowCameraPermission = true;
            } else {
                PermissionHelper.showAddPhotoDialog(getActivity());
            }
        }
    }

    // ---- EditorActions.Host ----

    @Override
    public Activity getEditorActivity() {
        return getActivity();
    }

    @Override
    public View getFocusView() {
        return getActivity().getCurrentFocus();
    }

    @Override
    public File getPhotoFolder() {
        return diaryTempFileManager.getDir();
    }

    @Override
    public void onCoverChanged() {
        updateCover();
    }

    @Override
    public void pickAudioFile() {
        audioImporter.pick();
    }

    /**
     * The cover shows the photo that is the thumbnail (chosen with the star, or the first photo), with the part that
     * the person chose.
     */
    private void updateCover() {
        if (diaryCover == null || diaryItemHelper == null) {
            return;
        }
        diaryItemHelper.refreshThumbnailMarks();
        diaryCover.setCover(diaryItemHelper.getThumbnailUriString(), diaryItemHelper.getThumbnailCoverArea());
    }

    /**
     * Clear and set the UUI
     */
    private void clearDiaryPage() {
        locationField.setName("");
        weatherMood.set(0, 0);
        titleTags.setTitle("");
        titleTags.setTags("");
        diaryItemHelper.initDiary();
        updateCover();
    }

    /**
     * The temp file only be clear when diary save
     */
    private void clearDiaryTemp() {
        diaryTempFileManager.clearDir();
        SPFManager.clearDiaryAutoSave(getActivity(), getTopicId());
    }

    private void autoSaveDiary() {
        if (diaryItemHelper.getItemSize() > 0) {
            List<BUDiaryItem> diaryItemItemList = new ArrayList<>();
            for (int x = 0; x < diaryItemHelper.getItemSize(); x++) {
                diaryItemItemList.add(
                        new BUDiaryItem(diaryItemHelper.get(x).getType(),
                                diaryItemHelper.get(x).getPosition(),
                                diaryItemHelper.get(x).getContent(),
                                diaryItemHelper.getAreaTextAt(x)));
            }
            BUDiaryEntries autoSaveDiary = new BUDiaryEntries(
                    NO_BU_DIARY_ID, NO_BU_DIARY_TIME,
                    titleTags.getTitle(),
                    weatherMood.getMood(),
                    weatherMood.getWeather(),
                    diaryItemHelper.getNowPhotoCount() > 0,
                    locationField.getName(), titleTags.getTags(),
                    diaryItemHelper.getChosenThumbnail(), diaryItemItemList);
            SPFManager.setDiaryAutoSave(getActivity(), getTopicId(), new Gson().toJson(autoSaveDiary));
        }
    }

    /**
     * Revert diray from SPF
     */
    private void revertAutoSaveDiary() {

        if (SPFManager.getDiaryAutoSave(getActivity(), getTopicId()) != null) {
            try {
                BUDiaryEntries autoSaveDiary = new Gson().fromJson(
                        SPFManager.getDiaryAutoSave(getActivity(), getTopicId()), BUDiaryEntries.class);
                titleTags.setTitle(autoSaveDiary.getDiaryEntriesTitle());
                titleTags.setTags(autoSaveDiary.getDiaryEntriesTags());
                locationField.setName(autoSaveDiary.getDiaryEntriesLocation());
                weatherMood.set(autoSaveDiary.getDiaryEntriesMood(), autoSaveDiary.getDiaryEntriesWeather());
                loadDiaryItemContent(autoSaveDiary);
                diaryItemHelper.setThumbnailFileName(autoSaveDiary.getDiaryEntriesThumbnail());
                updateCover();
            } catch (Exception e) {
                Log.e(TAG, "Load auto save fail", e);
            }
            TV_diary_item_content_hint.setVisibility(
                    diaryItemHelper.getContentItemCount() > 0 ? View.INVISIBLE : View.VISIBLE);
        } else {
            TV_diary_item_content_hint.setVisibility(View.VISIBLE);
        }
    }

    private void loadDiaryItemContent(BUDiaryEntries autoSaveDiary) {
        for (int i = 0; i < autoSaveDiary.getDiaryItemList().size(); i++) {
            BUDiaryItem saved = autoSaveDiary.getDiaryItemList().get(i);
            //In this page , it always is  edit mode.
            IDairyRow diaryItem = DiaryItemFactory.create(getActivity(), diaryItemHelper,
                    saved.getDiaryItemType(), saved.getDiaryItemContent(), saved.getDiaryItemArea(),
                    diaryTempFileManager.getDirAbsolutePath(), true, editorActions);
            if (diaryItem == null) {
                //Unknown kind of item: skip it
                continue;
            }
            diaryItem.setPosition(i);
            diaryItemHelper.createItem(diaryItem);
        }
    }

    private void saveDiary() {
        new SaveDiaryTask(getActivity(), dateField.getMillis(),
                titleTags.getTitle(),
                weatherMood.getMood(), weatherMood.getWeather(),
                //Check  attachment
                diaryItemHelper.getNowPhotoCount() > 0,
                locationField.getName(), titleTags.getTags(),
                diaryItemHelper, getTopicId(), this).execute(getTopicId());
    }

    /**
     * The camera button (a photo for the text) or the button of the cover (a photo only for the cover).
     */
    private void addPhotoFromButton(boolean forCover) {
        pickingCover = forCover;
        if (FileManager.getSDCardFreeSize() > FileManager.MIN_FREE_SPACE) {
            if (PermissionHelper.checkPermission(this, REQUEST_CAMERA_AND_WRITE_ES_PERMISSION)) {
                if (forCover || diaryItemHelper.getNowPhotoCount() < DiaryItemHelper.MAX_PHOTO_COUNT) {
                    openPhotoBottomSheet();
                } else {
                    Toast.makeText(getActivity(),
                            String.format(getResources().getString(R.string.toast_max_photo), DiaryItemHelper.MAX_PHOTO_COUNT),
                            Toast.LENGTH_SHORT).show();
                }
            }
        } else {
            //Insufficient
            Toast.makeText(getActivity(), getString(R.string.toast_space_insufficient), Toast.LENGTH_SHORT).show();
        }
    }

    private void openPhotoBottomSheet() {
        DiaryPhotoBottomSheet diaryPhotoBottomSheet = DiaryPhotoBottomSheet.newInstance(false);
        diaryPhotoBottomSheet.setTargetFragment(this, 0);
        diaryPhotoBottomSheet.show(getFragmentManager(), "diaryPhotoBottomSheet");
    }

    // ---- DiaryPhotoBottomSheet.PhotoCallBack ----

    @Override
    public void selectPhoto(Uri uri) {
        List<Uri> uris = new ArrayList<>();
        uris.add(uri);
        photoImporter.addUris(uris);
    }

    @Override
    public void selectPhotos(List<Uri> uris) {
        if (pickingCover && uris.size() > 1) {
            //Only one photo can be the cover
            uris = new ArrayList<>(uris.subList(0, 1));
        }
        photoImporter.addUris(uris);
    }

    @Override
    public void addPhoto(String fileName) {
        photoImporter.addCameraFile(fileName);
    }

    // ---- PhotoImporter.Listener ----

    @Override
    public void onPhotoReady(String fileName) {
        if (pickingCover) {
            pickingCover = false;
            editorActions.setCoverOnlyPhoto(fileName, diaryTempFileManager.getDirAbsolutePath());
        } else {
            editorActions.insertPhoto(fileName, diaryTempFileManager.getDirAbsolutePath());
        }
    }


    @Override
    public void update(Observable observable, Object data) {
        if (diaryItemHelper.getContentItemCount() > 0) {
            TV_diary_item_content_hint.setVisibility(View.GONE);
        } else {
            TV_diary_item_content_hint.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onDiarySaved() {
        //A stamp: saved
        Sfx.play(getActivity(), Sfx.Sound.SAVE);
        //For next diary
        dateField.setNow();
        //Clear
        clearDiaryPage();
        clearDiaryTemp();
        //Set flag
        ((DiaryActivity) getActivity()).callEntriesListRefresh();
        //Goto entries page
        ((DiaryActivity) getActivity()).gotoPageSilently(0);
    }


    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.LL_diary_time_information:
                dateField.openPicker(getFragmentManager());
                break;
            case R.id.LL_diary_item_content:
                if (diaryItemHelper.getContentItemCount() == 0) {
                    //Nothing is written yet (a photo that is only the cover is not text, it stays)
                    if (diaryItemHelper.getItemSize() == 0) {
                        diaryItemHelper.initDiary();
                    }
                    //Add default edittext item
                    DiaryText diaryText = new DiaryText(getActivity());
                    diaryText.setPosition(diaryItemHelper.getItemSize());
                    diaryItemHelper.createItem(diaryText);
                    //set Focus
                    diaryText.getView().requestFocus();
                    //Show keyboard automatically
                    InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.showSoftInput(diaryText.getView(), InputMethodManager.SHOW_IMPLICIT);
                } else if (diaryItemHelper.getContentItemCount() == 1) {
                    //Make the soft keyboard can be opened when it is only one item.
                    IDairyRow only = diaryItemHelper.getLastContentRow();
                    only.getView().requestFocus();
                    InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.showSoftInput(only.getView(), InputMethodManager.SHOW_IMPLICIT);
                }
                break;
            case R.id.IV_diary_photo:
                addPhotoFromButton(false);
                break;
            case R.id.IV_diary_save:
                if (diaryItemHelper.getItemSize() > 0) {
                    saveDiary();
                } else {
                    Toast.makeText(getActivity(), getString(R.string.toast_diary_empty), Toast.LENGTH_SHORT).show();
                }
                break;
        }
    }
}
