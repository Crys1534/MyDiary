package com.kiminonawa.mydiary.entries.entries;

import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.kiminonawa.mydiary.R;
import com.kiminonawa.mydiary.db.DBManager;
import com.kiminonawa.mydiary.db.DBStructure;
import com.kiminonawa.mydiary.entries.DiaryActivity;
import com.kiminonawa.mydiary.entries.EditDiaryBackDialogFragment;
import com.kiminonawa.mydiary.entries.diary.CopyPhotoTask;
import com.kiminonawa.mydiary.entries.diary.DiaryCover;
import com.kiminonawa.mydiary.entries.diary.DiaryPhotoBottomSheet;
import com.kiminonawa.mydiary.entries.diary.TagHelper;
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
import com.kiminonawa.mydiary.entries.diary.item.DiaryChecklist;
import com.kiminonawa.mydiary.entries.diary.item.DiaryItemHelper;
import com.kiminonawa.mydiary.entries.diary.item.DiaryPhoto;
import com.kiminonawa.mydiary.entries.diary.item.IDairyRow;
import com.kiminonawa.mydiary.entries.photo.PhotoDetailViewerActivity;
import com.kiminonawa.mydiary.entries.photo.PhotoOverviewActivity;
import com.kiminonawa.mydiary.shared.FileManager;
import com.kiminonawa.mydiary.shared.PermissionHelper;
import com.kiminonawa.mydiary.shared.ScreenHelper;
import com.kiminonawa.mydiary.shared.Sfx;
import com.kiminonawa.mydiary.shared.ThemeManager;
import com.kiminonawa.mydiary.shared.PaperStyle;
import com.kiminonawa.mydiary.shared.ViewTools;
import com.kiminonawa.mydiary.shared.gui.AspectRatioRelativeLayout;
import com.kiminonawa.mydiary.shared.statusbar.ChinaPhoneHelper;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static com.kiminonawa.mydiary.shared.PermissionHelper.REQUEST_CAMERA_AND_WRITE_ES_PERMISSION;

/**
 * The window that shows an entry (reading) or edits it (editing).
 * <p>
 * Created by daxia on 2016/10/27.
 * <p>
 * The pieces that are the same in the page that writes a new entry (place, photos, day, title and tags, weather and
 * mood, the buttons of the editor, the rows) live in the package "editor", so they are written only once.
 */

public class DiaryViewerDialogFragment extends DialogFragment implements View.OnClickListener,
        DiaryDeleteDialogFragment.DeleteCallback, CopyDiaryToEditCacheTask.EditTaskCallBack,
        DiaryPhotoBottomSheet.PhotoCallBack, UpdateDiaryTask.UpdateDiaryCallBack,
        EditDiaryBackDialogFragment.BackDialogCallback, EditorActions.Host, PhotoImporter.Listener {

    /**
     * Callback
     */
    public interface DiaryViewerCallback {
        void deleteDiary();

        void updateDiary();
    }

    private DiaryViewerCallback callback;

    private static final String TAG = "DiaryViewer";

    /**
     * UI
     */
    private ProgressBar PB_diary_item_content_hint;
    private LinearLayout LL_diary_item_content;
    private ImageView IV_diary_close_dialog, IV_diary_photo, IV_diary_delete, IV_diary_move, IV_diary_save;
    //The cover (thumbnail, date, title, tags)
    private DiaryCover diaryCover;
    //Folds the cover away while the person types (only when editing)
    private CoverCollapser coverCollapser;
    //Tags of the diary as they are in the database (",school,trip,")
    private String savedTags = "";
    private boolean isEditMode;

    /**
     * The parts of the entry (the title, the weather and the mood only exist when it is edited)
     */
    private TitleTagsField titleTags;
    private WeatherMoodField weatherMood;
    private EntryDateField dateField;
    private LocationField locationField;

    /**
     * diary content & info
     */
    private long diaryId;
    private DiaryItemHelper diaryItemHelper;
    private EditorActions editorActions;
    private AreaPickerController areaPicker;
    private PhotoImporter photoImporter;
    private AudioFileImporter audioImporter;
    //The photo that is being chosen is for the cover only (it is not put in the text)
    private boolean pickingCover = false;
    private FileManager diaryFileManager;

    /**
     * Edit Mode
     */
    private CopyDiaryToEditCacheTask mTask;
    private Handler loadDiaryHandler;
    private boolean initHandlerOrTaskIsRunning = false;

    /**
     * Diary Photo viewer
     */
    private ArrayList<Uri> diaryPhotoFileList;

    /**
     * Permission
     */
    private boolean firstAllowCameraPermission = false;


    public static DiaryViewerDialogFragment newInstance(long diaryId, boolean isEditMode) {
        Bundle args = new Bundle();
        DiaryViewerDialogFragment fragment = new DiaryViewerDialogFragment();
        args.putLong("diaryId", diaryId);
        args.putBoolean("isEditMode", isEditMode);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        isEditMode = getArguments().getBoolean("isEditMode", false);
        //A page of the notebook is opened
        Sfx.play(getActivity(), isEditMode ? Sfx.Sound.PEN_CLICK : Sfx.Sound.PAGE_SOFT);
        if (isEditMode) {
            Toast.makeText(getActivity(),
                    getString(R.string.toast_diary_long_click_edit), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Dialog dialog = new Dialog(getActivity(), getTheme()) {
            @Override
            public void onBackPressed() {
                if (isEditMode) {
                    askBeforeLeaving();
                } else {
                    super.onBackPressed();
                }
            }
        };
        // request a window without the title
        dialog.getWindow().requestFeature(Window.FEATURE_NO_TITLE);
        //The entry uses the whole screen (also to read it): the page color is the background and the activity behind
        //is not dimmed (the status bar keeps looking like the rest of the app)
        dialog.getWindow().setBackgroundDrawable(
                new android.graphics.drawable.ColorDrawable(androidx.core.content.ContextCompat.getColor(getActivity(), R.color.app_surface)));
        dialog.getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        return dialog;
    }

    /**
     * Editing and going back: "leave without saving?"
     */
    private void askBeforeLeaving() {
        EditDiaryBackDialogFragment backDialogFragment = new EditDiaryBackDialogFragment();
        backDialogFragment.setTargetFragment(DiaryViewerDialogFragment.this, 0);
        backDialogFragment.show(getFragmentManager(), "backDialogFragment");
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        this.getDialog().setCanceledOnTouchOutside(false);
        View rootView = inflater.inflate(R.layout.fragment_diary, container);
        ScrollView ScrollView_diary_content = (ScrollView) rootView.findViewById(R.id.ScrollView_diary_content);
        ViewTools.setScrollBarColor(getActivity(), ScrollView_diary_content);
        //Notebook sheet (Settings): paper behind the entry
        PaperStyle.applyToPage(getActivity(), rootView.findViewById(R.id.RL_diary_page),
                rootView.findViewById(R.id.RL_diary_content));

        RelativeLayout RL_diary_info = (RelativeLayout) rootView.findViewById(R.id.RL_diary_info);
        RL_diary_info.setBackgroundColor(ThemeManager.getInstance().getThemeMainColor(getActivity()));
        diaryCover = new DiaryCover(rootView, new DiaryCover.Listener() {
            @Override
            public void onEditTagsRequested() {
                onCoverEditTags();
            }
        });
        diaryCover.setEditMode(isEditMode);
        if (isEditMode) {
            coverCollapser = new CoverCollapser(rootView, (AspectRatioRelativeLayout) RL_diary_info);
        }

        LinearLayout LL_diary_edit_bar = (LinearLayout) rootView.findViewById(R.id.LL_diary_edit_bar);
        LL_diary_edit_bar.setBackgroundColor(ThemeManager.getInstance().getThemeMainColor(getActivity()));

        PB_diary_item_content_hint = (ProgressBar) rootView.findViewById(R.id.PB_diary_item_content_hint);
        LL_diary_item_content = (LinearLayout) rootView.findViewById(R.id.LL_diary_item_content);

        IV_diary_close_dialog = (ImageView) rootView.findViewById(R.id.IV_diary_close_dialog);
        IV_diary_close_dialog.setVisibility(View.VISIBLE);
        IV_diary_close_dialog.setOnClickListener(this);

        IV_diary_photo = (ImageView) rootView.findViewById(R.id.IV_diary_photo);
        IV_diary_delete = (ImageView) rootView.findViewById(R.id.IV_diary_delete);
        IV_diary_move = (ImageView) rootView.findViewById(R.id.IV_diary_move);
        IV_diary_save = (ImageView) rootView.findViewById(R.id.IV_diary_save);

        dateField = new EntryDateField(getActivity(), rootView, diaryCover);
        locationField = new LocationField(this, rootView, isEditMode);

        diaryItemHelper = new DiaryItemHelper(LL_diary_item_content);
        areaPicker = new AreaPickerController(this, diaryItemHelper, new AreaPickerController.Listener() {
            @Override
            public void onAreaChanged() {
                updateCover();
            }
        });
        editorActions = new EditorActions(this, diaryItemHelper, LL_diary_item_content, areaPicker);
        photoImporter = new PhotoImporter(this, this);
        audioImporter = new AudioFileImporter(this, new AudioFileImporter.Listener() {
            @Override
            public void onAudioReady(String fileName, String displayName) {
                editorActions.insertLocalAudio(fileName, displayName);
            }
        });
        diaryCover.setPickAreaListener(new Runnable() {
            @Override
            public void run() {
                if (diaryFileManager != null) {
                    areaPicker.pickForCover(diaryItemHelper.getThumbnailPhoto(), diaryFileManager.getDir());
                }
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
        initView(rootView);
        return rootView;
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        callback = (DiaryViewerCallback) getTargetFragment();
        diaryId = getArguments().getLong("diaryId", -1L);
        //Init the object
        if (diaryId != -1) {
            if (isEditMode) {
                diaryFileManager = new FileManager(getActivity(), FileManager.DIARY_EDIT_CACHE_DIR);
                diaryFileManager.clearDir();
                photoImporter.setTargetFolder(diaryFileManager);
                audioImporter.setTargetFolder(diaryFileManager);
                PB_diary_item_content_hint.setVisibility(View.VISIBLE);
                mTask = new CopyDiaryToEditCacheTask(getActivity(), diaryFileManager, this);
                //Make ths ProgressBar show 0.7s+.
                loadDiaryHandler = new Handler();
                initHandlerOrTaskIsRunning = true;
                loadDiaryHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        //Copy the file into editCash
                        mTask.execute(((DiaryActivity) getActivity()).getTopicId(), diaryId);
                    }
                }, 700);
            } else {
                diaryFileManager = new FileManager(getActivity(), ((DiaryActivity) getActivity()).getTopicId(), diaryId);
                diaryPhotoFileList = new ArrayList<>();
                initData();
            }
        }
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
    public void onStart() {
        super.onStart();
        //The whole screen. The keyboard pushes the bottom bar up (the page is resized, not moved)
        Dialog dialog = getDialog();
        if (dialog != null && dialog.getWindow() != null) {
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            dialog.getWindow().setSoftInputMode(
                    android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        if (coverCollapser != null) {
            coverCollapser.start();
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        if (coverCollapser != null) {
            coverCollapser.stop();
        }
        if (diaryItemHelper != null) {
            diaryItemHelper.releaseAudio();
        }
        if (initHandlerOrTaskIsRunning) {
            if (loadDiaryHandler != null) {
                loadDiaryHandler.removeCallbacksAndMessages(null);
            }
            if (mTask != null) {
                mTask.cancel(true);
            }
            dismissAllowingStateLoss();
        }
        locationField.onStop();
    }

    // ---- EditorActions.Host ----

    @Override
    public Activity getEditorActivity() {
        return getActivity();
    }

    @Override
    public View getFocusView() {
        return getDialog().getCurrentFocus();
    }

    @Override
    public File getPhotoFolder() {
        return diaryFileManager.getDir();
    }

    @Override
    public void onCoverChanged() {
        updateCover();
    }

    @Override
    public void pickAudioFile() {
        audioImporter.pick();
    }

    private void initData() {
        DBManager dbManager = new DBManager(getActivity());
        dbManager.opeDB();
        Cursor diaryInfoCursor = dbManager.selectDiaryInfoByDiaryId(diaryId);
        //load Time
        dateField.setMillis(diaryInfoCursor.getLong(1));
        int tagsColumn = diaryInfoCursor.getColumnIndex(DBStructure.DiaryEntry_V2.COLUMN_TAGS);
        savedTags = tagsColumn >= 0 && diaryInfoCursor.getString(tagsColumn) != null
                ? diaryInfoCursor.getString(tagsColumn) : "";
        diaryCover.setTags(savedTags);
        if (isEditMode) {
            //Allow to edit diary
            titleTags.setTitle(diaryInfoCursor.getString(2));
            titleTags.setTags(savedTags);
        } else {
            //The title is on the cover
            diaryCover.setTitle(diaryInfoCursor.getString(2));
        }
        int thumbnailColumn = diaryInfoCursor.getColumnIndex(DBStructure.DiaryEntry_V2.COLUMN_THUMBNAIL);
        String chosenThumbnail = thumbnailColumn >= 0 ? diaryInfoCursor.getString(thumbnailColumn) : "";
        locationField.setName(diaryInfoCursor.getString(7));
        int mood = diaryInfoCursor.getInt(3);
        int weather = diaryInfoCursor.getInt(4);
        if (isEditMode) {
            weatherMood.set(mood, weather);
        } else {
            //Reading: only the icon of the weather, on the cover
            diaryCover.setWeather(weather);
        }
        diaryInfoCursor.close();
        //Get diary detail
        loadDiaryItemContent(dbManager);
        diaryItemHelper.setThumbnailFileName(chosenThumbnail);
        updateCover();
        dbManager.closeDB();
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
     * The pencil / "Edit" of the tags on the cover: a small window to write them.
     * Editing the entry: they are kept in the hidden box and saved with the entry.
     * Only reading: they are saved right away.
     */
    private void onCoverEditTags() {
        if (isEditMode) {
            titleTags.editTags(getActivity());
            return;
        }
        TagHelper.showEditDialog(getActivity(), savedTags, new TagHelper.Callback() {
            @Override
            public void onTags(String newTags) {
                saveTagsOnly(newTags);
            }
        });
    }

    private void saveTagsOnly(String newTags) {
        try {
            DBManager dbManager = new DBManager(getActivity());
            dbManager.opeDB();
            dbManager.updateDiaryTags(diaryId, newTags);
            dbManager.closeDB();
            Sfx.play(getActivity(), Sfx.Sound.POP);
            savedTags = newTags;
            diaryCover.setTags(savedTags);
            //The list of entries shows the tags too
            if (callback != null) {
                callback.updateDiary();
            }
        } catch (Exception e) {
            Log.e(TAG, "save tags fail", e);
        }
    }

    private void initView(View rootView) {
        if (isEditMode) {
            //For hidden hint
            titleTags = new TitleTagsField(rootView, diaryCover, ThemeManager.getInstance().getThemeMainColor(getActivity()));
            titleTags.setTitle(" ");
            weatherMood = new WeatherMoodField(getActivity(), rootView, diaryCover);
            rootView.findViewById(R.id.LL_diary_time_information).setOnClickListener(this);

            IV_diary_photo.setImageResource(R.drawable.ic_photo_camera_white_24dp);
            IV_diary_photo.setOnClickListener(this);
            //The bold, italic, lists, checklist and audio buttons
            editorActions.bindBar(rootView);
        } else {
            rootView.findViewById(R.id.EDT_diary_title).setVisibility(View.GONE);
            rootView.findViewById(R.id.RL_diary_weather).setVisibility(View.GONE);
            rootView.findViewById(R.id.RL_diary_mood).setVisibility(View.GONE);
            //The title is on the cover now
            rootView.findViewById(R.id.TV_diary_title_content).setVisibility(View.GONE);

            //Only when reading: delete, and move / copy to another diary
            //(the changes of an edition are not saved yet)
            IV_diary_delete.setVisibility(View.VISIBLE);
            IV_diary_delete.setOnClickListener(this);
            IV_diary_move.setVisibility(View.VISIBLE);
            IV_diary_move.setOnClickListener(this);
            IV_diary_save.setVisibility(View.GONE);

            IV_diary_photo.setImageResource(R.drawable.ic_photo_white_24dp);
            IV_diary_photo.setOnClickListener(this);
        }
    }

    private void loadDiaryItemContent(DBManager dbManager) {
        Cursor diaryContentCursor = dbManager.selectDiaryContentByDiaryId(diaryId);
        int areaColumn = diaryContentCursor.getColumnIndex(DBStructure.DiaryItemEntry_V2.COLUMN_AREA);
        String photoFolder = diaryFileManager.getDirAbsolutePath();
        //To count how many photo is in the diary on view mode.
        int photoCount = 0;
        for (int i = 0; i < diaryContentCursor.getCount(); i++) {
            int type = diaryContentCursor.getInt(1);
            String content = diaryContentCursor.getString(3);
            String area = areaColumn >= 0 ? diaryContentCursor.getString(areaColumn) : "";
            IDairyRow diaryItem = DiaryItemFactory.create(getActivity(), diaryItemHelper, type, content, area,
                    photoFolder, isEditMode, editorActions);
            if (diaryItem == null) {
                //Unknown kind of item: skip it
                diaryContentCursor.moveToNext();
                continue;
            }
            if (!isEditMode) {
                if (diaryItem instanceof DiaryPhoto && ((DiaryPhoto) diaryItem).isCoverOnly()) {
                    //The photo that is only the cover is not shown in the text (nothing to tap)
                } else if (diaryItem instanceof DiaryPhoto) {
                    //Reading: tap a photo to see it big
                    ((DiaryPhoto) diaryItem).setDraweeViewClickListener(this);
                    ((DiaryPhoto) diaryItem).setDraweeViewPositionTag(photoCount);
                    photoCount++;
                    diaryPhotoFileList.add(Uri.parse(FileManager.FILE_HEADER + photoFolder + "/" + content));
                } else if (diaryItem instanceof DiaryChecklist) {
                    //Not editing: the text can't be changed, but the boxes can be ticked (saved right away)
                    ((DiaryChecklist) diaryItem).setToggleListener(new DiaryChecklist.ToggleListener() {
                        @Override
                        public void onToggled(DiaryChecklist item) {
                            Sfx.play(getActivity(), Sfx.Sound.CHECK);
                            saveChecklistState(item);
                        }
                    });
                }
            }
            diaryItem.setPosition(i);
            diaryItemHelper.createItem(diaryItem);
            diaryContentCursor.moveToNext();
        }
        diaryContentCursor.close();
    }

    /**
     * Ticking a box while reading a diary is saved right away.
     */
    private void saveChecklistState(DiaryChecklist item) {
        try {
            DBManager dbManager = new DBManager(getActivity());
            dbManager.opeDB();
            dbManager.updateDiaryItemContent(diaryId, item.getPosition(), item.getContent());
            dbManager.closeDB();
        } catch (Exception e) {
            Log.e(TAG, "save checklist fail", e);
        }
    }

    private void updateDiary() {
        new UpdateDiaryTask(getActivity(), dateField.getMillis(), titleTags.getTitle(),
                weatherMood.getMood(), weatherMood.getWeather(),
                locationField.getName(), titleTags.getTags(),
                //Check  attachment
                diaryItemHelper.getNowPhotoCount() > 0,
                diaryItemHelper, diaryFileManager, this).execute(((DiaryActivity) getActivity()).getTopicId(), diaryId);

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
        DiaryPhotoBottomSheet diaryPhotoBottomSheet = DiaryPhotoBottomSheet.newInstance(true);
        diaryPhotoBottomSheet.setTargetFragment(this, 0);
        diaryPhotoBottomSheet.show(getFragmentManager(), "diaryPhotoBottomSheet");
    }

    @Override
    public void onDiaryUpdated() {
        Sfx.play(getActivity(), Sfx.Sound.SAVE);
        this.dismissAllowingStateLoss();
        callback.updateDiary();
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
            editorActions.setCoverOnlyPhoto(fileName, diaryFileManager.getDirAbsolutePath());
        } else {
            editorActions.insertPhoto(fileName, diaryFileManager.getDirAbsolutePath());
        }
    }


    @Override
    public void onDiaryDelete() {
        Sfx.play(getActivity(), Sfx.Sound.CRUMPLE);
        callback.deleteDiary();
        dismiss();
    }

    @Override
    public void onBack() {
        Sfx.play(getActivity(), Sfx.Sound.CLOSE);
        dismiss();
    }


    @Override
    public void onCopyToEditCacheCompiled(int result) {
        if (result == CopyDiaryToEditCacheTask.RESULT_COPY_SUCCESSFUL) {
            PB_diary_item_content_hint.setVisibility(View.GONE);
            initData();
            //Open the click listener
            IV_diary_save.setOnClickListener(this);
        } else {
            dismissAllowingStateLoss();
        }
        initHandlerOrTaskIsRunning = false;
    }

    @Override
    public void onClick(View v) {

        switch (v.getId()) {
            case R.id.LL_diary_time_information:
                dateField.openPicker(getFragmentManager());
                break;
            case R.id.SDV_diary_new_photo:
                int draweeViewPosition = (int) v.getTag();
                Intent gotoPhotoDetailViewer = new Intent(getActivity(), PhotoDetailViewerActivity.class);
                gotoPhotoDetailViewer.putParcelableArrayListExtra(
                        PhotoDetailViewerActivity.DIARY_PHOTO_FILE_LIST, diaryPhotoFileList);
                gotoPhotoDetailViewer.putExtra(PhotoDetailViewerActivity.SELECT_POSITION, draweeViewPosition);
                getActivity().startActivity(gotoPhotoDetailViewer);
                break;
            case R.id.IV_diary_photo:
                if (isEditMode) {
                    //Allow add photo
                    addPhotoFromButton(false);
                } else {
                    //Show the gallery
                    Intent gotoPhotoOverviewIntent = new Intent(getActivity(), PhotoOverviewActivity.class);
                    gotoPhotoOverviewIntent.putExtra(PhotoOverviewActivity.PHOTO_OVERVIEW_TOPIC_ID, ((DiaryActivity) getActivity()).getTopicId());
                    gotoPhotoOverviewIntent.putExtra(PhotoOverviewActivity.PHOTO_OVERVIEW_DIARY_ID, diaryId);
                    getActivity().startActivity(gotoPhotoOverviewIntent);
                }
                break;
            case R.id.IV_diary_close_dialog:
                if (isEditMode) {
                    askBeforeLeaving();
                } else {
                    Sfx.play(getActivity(), Sfx.Sound.CLOSE);
                    dismiss();
                }
                break;
            case R.id.IV_diary_move:
                showMoveCopyMenu();
                break;
            case R.id.IV_diary_delete:
                DiaryDeleteDialogFragment diaryDeleteDialogFragment =
                        DiaryDeleteDialogFragment.newInstance(((DiaryActivity) getActivity()).getTopicId(), diaryId);
                diaryDeleteDialogFragment.setTargetFragment(this, 0);
                diaryDeleteDialogFragment.show(getFragmentManager(), "diaryDeleteDialogFragment");
                break;
            case R.id.IV_diary_save:
                if (diaryItemHelper.getItemSize() > 0) {
                    updateDiary();
                } else {
                    Toast.makeText(getActivity(), getString(R.string.toast_diary_empty), Toast.LENGTH_SHORT).show();
                }
                break;
        }
    }

    /**
     * The "move / copy" button: first the choice (move or copy), then the diary.
     */
    private void showMoveCopyMenu() {
        android.widget.PopupMenu menu = new android.widget.PopupMenu(getActivity(), IV_diary_move);
        menu.getMenu().add(0, 1, 0, R.string.diary_move_menu_move);
        menu.getMenu().add(0, 2, 1, R.string.diary_move_menu_copy);
        menu.setOnMenuItemClickListener(new android.widget.PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                chooseDiaryForMoveCopy(item.getItemId() == 1);
                return true;
            }
        });
        menu.show();
    }

    private void chooseDiaryForMoveCopy(final boolean move) {
        final long currentTopicId = ((DiaryActivity) getActivity()).getTopicId();
        final java.util.List<DiaryMover.TopicOption> topics = new java.util.ArrayList<>();
        for (DiaryMover.TopicOption option : DiaryMover.diaryTopics(getActivity())) {
            if (!move || option.id != currentTopicId) {
                topics.add(option);
            }
        }
        if (topics.isEmpty()) {
            Toast.makeText(getActivity(), R.string.diary_move_no_other, Toast.LENGTH_LONG).show();
            return;
        }
        String[] names = new String[topics.size()];
        for (int i = 0; i < names.length; i++) {
            names[i] = topics.get(i).name;
            if (topics.get(i).id == currentTopicId) {
                names[i] += " " + getString(R.string.diary_move_this_diary);
            }
        }
        new androidx.appcompat.app.AlertDialog.Builder(getActivity())
                .setTitle(move ? R.string.diary_move_title_move : R.string.diary_move_title_copy)
                .setItems(names, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        moveOrCopyTo(move, currentTopicId, topics.get(which));
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void moveOrCopyTo(boolean move, long currentTopicId, DiaryMover.TopicOption target) {
        boolean ok;
        if (move) {
            ok = DiaryMover.move(getActivity(), diaryId, currentTopicId, target.id);
        } else {
            ok = DiaryMover.copy(getActivity(), diaryId, currentTopicId, target.id) >= 0;
        }
        if (ok) {
            Sfx.play(getActivity(), Sfx.Sound.SLIDE);
        }
        if (!ok) {
            Toast.makeText(getActivity(), R.string.diary_move_failed, Toast.LENGTH_LONG).show();
            return;
        }
        Toast.makeText(getActivity(), getString(move ? R.string.diary_moved : R.string.diary_copied, target.name),
                Toast.LENGTH_LONG).show();
        if (callback != null) {
            callback.updateDiary();
        }
        if (move) {
            dismiss();
        }
    }
}
