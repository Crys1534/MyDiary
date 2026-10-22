package com.kiminonawa.mydiary.entries.entries;

import android.app.DatePickerDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.app.TimePickerDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.PorterDuff;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.appcompat.app.AlertDialog;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.TimePicker;
import android.view.MenuItem;
import android.widget.Toast;

import com.kiminonawa.mydiary.R;
import com.kiminonawa.mydiary.db.DBManager;
import com.kiminonawa.mydiary.db.DBStructure;
import com.kiminonawa.mydiary.entries.diary.PhotoCropController;
import com.kiminonawa.mydiary.entries.diary.TagHelper;
import com.kiminonawa.mydiary.entries.DiaryActivity;
import com.kiminonawa.mydiary.entries.EditDiaryBackDialogFragment;
import com.kiminonawa.mydiary.entries.diary.AudioLinkDialog;
import com.kiminonawa.mydiary.entries.diary.CopyPhotoTask;
import com.kiminonawa.mydiary.entries.diary.DiaryCover;
import com.kiminonawa.mydiary.entries.diary.DiaryInfoHelper;
import com.kiminonawa.mydiary.entries.diary.DiaryPhotoBottomSheet;
import com.kiminonawa.mydiary.entries.diary.ImageArrayAdapter;
import com.kiminonawa.mydiary.entries.diary.item.DiaryAudio;
import com.kiminonawa.mydiary.entries.diary.item.DiaryChecklist;
import com.kiminonawa.mydiary.entries.diary.item.DiaryItemHelper;
import com.kiminonawa.mydiary.entries.diary.item.RichText;
import com.kiminonawa.mydiary.entries.diary.item.DiaryPhoto;
import com.kiminonawa.mydiary.entries.diary.item.DiaryText;
import com.kiminonawa.mydiary.entries.diary.item.DiaryTextTag;
import com.kiminonawa.mydiary.entries.diary.item.IDairyRow;
import com.kiminonawa.mydiary.entries.diary.picker.DatePickerFragment;
import com.kiminonawa.mydiary.entries.diary.picker.TimePickerFragment;
import com.kiminonawa.mydiary.entries.photo.PhotoDetailViewerActivity;
import com.kiminonawa.mydiary.entries.photo.PhotoOverviewActivity;
import com.kiminonawa.mydiary.shared.FileManager;
import com.kiminonawa.mydiary.shared.LocationHelper;
import com.kiminonawa.mydiary.shared.PermissionHelper;
import com.kiminonawa.mydiary.shared.ScreenHelper;
import com.kiminonawa.mydiary.shared.ThemeManager;
import com.kiminonawa.mydiary.shared.TimeTools;
import com.kiminonawa.mydiary.shared.ViewTools;
import com.kiminonawa.mydiary.shared.statusbar.ChinaPhoneHelper;

import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

import static android.content.Context.LOCATION_SERVICE;
import static com.kiminonawa.mydiary.shared.PermissionHelper.REQUEST_CAMERA_AND_WRITE_ES_PERMISSION;

/**
 * Created by daxia on 2016/10/27.
 */

public class DiaryViewerDialogFragment extends DialogFragment implements View.OnClickListener,
        DiaryDeleteDialogFragment.DeleteCallback, CopyDiaryToEditCacheTask.EditTaskCallBack,
        DiaryPhotoBottomSheet.PhotoCallBack, CopyPhotoTask.CopyPhotoCallBack,
        UpdateDiaryTask.UpdateDiaryCallBack, EditDiaryBackDialogFragment.BackDialogCallback,
        DatePickerDialog.OnDateSetListener, TimePickerDialog.OnTimeSetListener {

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
    private ScrollView ScrollView_diary_content;
    private RelativeLayout RL_diary_info;
    private ProgressBar PB_diary_item_content_hint;
    private LinearLayout LL_diary_time_information;

    private TextView TV_diary_month, TV_diary_date, TV_diary_day, TV_diary_time;

    private ImageView IV_diary_weather;
    private ImageView IV_diary_location_name_icon;
    private TextView TV_diary_weather, TV_diary_location;
    private RelativeLayout RL_diary_weather, RL_diary_mood;
    private Spinner SP_diary_weather, SP_diary_mood;

    private TextView TV_diary_title_content;
    private EditText EDT_diary_title, EDT_diary_tags;
    private TextView TV_diary_tags;

    //The cover (thumbnail, date, title, tags)
    private DiaryCover diaryCover;
    private PhotoCropController photoCropController;
    //Tags of the diary as they are in the database (",school,trip,")
    private String savedTags = "";

    private LinearLayout LL_diary_item_content;
    private ImageView IV_diary_close_dialog, IV_diary_location, IV_diary_photo,
            IV_diary_delete, IV_diary_move, IV_diary_clear, IV_diary_save, IV_diary_bold, IV_diary_italic, IV_diary_list, IV_diary_checklist, IV_diary_audio;
    private boolean isEditMode;

    /**
     * diary content & info
     */
    private long diaryId;
    private DiaryItemHelper diaryItemHelper;
    private FileManager diaryFileManager;
    /**
     * Pictures picked from the gallery that are waiting to be copied (one at a time, in order).
     */
    private final LinkedList<Uri> pendingPhotoUris = new LinkedList<>();
    private boolean isCopyingPhoto = false;

    /**
     * Edit Mode
     */
    private CopyDiaryToEditCacheTask mTask;
    private Handler loadDiaryHandler;
    private boolean initHandlerOrTaskIsRunning = false;

    private Calendar calendar;
    private TimeTools timeTools;
    private SimpleDateFormat sdf;

    /**
     * Diary Photo viewer
     */
    private ArrayList<Uri> diaryPhotoFileList;

    /**
     * Google Place API
     */
    /**
     * Location
     */
    private DiaryViewerHandler diaryViewerHandler;
    private Location diaryLocations = null;
    private LocationManager locationManager;
    private boolean haveLocation;
    private String noLocation;
    private ProgressDialog progressDialog;


    /**
     * Permission
     */
    private boolean firstAllowLocationPermission = false;
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
                    EditDiaryBackDialogFragment backDialogFragment = new EditDiaryBackDialogFragment();
                    backDialogFragment.setTargetFragment(DiaryViewerDialogFragment.this, 0);
                    backDialogFragment.show(getFragmentManager(), "backDialogFragment");
                } else {
                    super.onBackPressed();
                }
            }
        };
        // request a window without the title
        dialog.getWindow().requestFeature(Window.FEATURE_NO_TITLE);
        //Set background is transparent , for dialog radius
        dialog.getWindow().getDecorView().getBackground().setAlpha(0);
        return dialog;
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        this.getDialog().setCanceledOnTouchOutside(false);
        View rootView = inflater.inflate(R.layout.fragment_diary, container);
        ScrollView_diary_content = (ScrollView) rootView.findViewById(R.id.ScrollView_diary_content);
        ViewTools.setScrollBarColor(getActivity(), ScrollView_diary_content);

        RL_diary_info = (RelativeLayout) rootView.findViewById(R.id.RL_diary_info);
        RL_diary_info.setBackground(ThemeManager.getInstance().createDiaryViewerInfoBg(getActivity()));
        diaryCover = new DiaryCover(rootView, new DiaryCover.Listener() {
            @Override
            public void onEditTagsRequested() {
                onCoverEditTags();
            }
        });
        diaryCover.setTopCornerRadius(ScreenHelper.dpToPixel(getResources(), 10));
        diaryCover.setEditMode(isEditMode);

        LinearLayout LL_diary_edit_bar = (LinearLayout) rootView.findViewById(R.id.LL_diary_edit_bar);
        LL_diary_edit_bar.setBackground(ThemeManager.getInstance().createDiaryViewerEditBarBg(getActivity()));

        PB_diary_item_content_hint = (ProgressBar) rootView.findViewById(R.id.PB_diary_item_content_hint);

        EDT_diary_title = (EditText) rootView.findViewById(R.id.EDT_diary_title);
        EDT_diary_tags = (EditText) rootView.findViewById(R.id.EDT_diary_tags);
        TV_diary_tags = (TextView) rootView.findViewById(R.id.TV_diary_tags);

        TV_diary_month = (TextView) rootView.findViewById(R.id.TV_diary_month);
        TV_diary_date = (TextView) rootView.findViewById(R.id.TV_diary_date);
        TV_diary_day = (TextView) rootView.findViewById(R.id.TV_diary_day);
        TV_diary_time = (TextView) rootView.findViewById(R.id.TV_diary_time);

        IV_diary_location_name_icon = (ImageView) rootView.findViewById(R.id.IV_diary_location_name_icon);
        TV_diary_location = (TextView) rootView.findViewById(R.id.TV_diary_location);

        LL_diary_item_content = (LinearLayout) rootView.findViewById(R.id.LL_diary_item_content);

        IV_diary_close_dialog = (ImageView) rootView.findViewById(R.id.IV_diary_close_dialog);
        IV_diary_close_dialog.setVisibility(View.VISIBLE);
        IV_diary_close_dialog.setOnClickListener(this);

        IV_diary_location = (ImageView) rootView.findViewById(R.id.IV_diary_location);

        IV_diary_photo = (ImageView) rootView.findViewById(R.id.IV_diary_photo);
        IV_diary_delete = (ImageView) rootView.findViewById(R.id.IV_diary_delete);
        IV_diary_move = (ImageView) rootView.findViewById(R.id.IV_diary_move);
        IV_diary_clear = (ImageView) rootView.findViewById(R.id.IV_diary_clear);
        IV_diary_save = (ImageView) rootView.findViewById(R.id.IV_diary_save);
        IV_diary_bold = (ImageView) rootView.findViewById(R.id.IV_diary_bold);
        IV_diary_italic = (ImageView) rootView.findViewById(R.id.IV_diary_italic);
        IV_diary_list = (ImageView) rootView.findViewById(R.id.IV_diary_list);
        IV_diary_checklist = (ImageView) rootView.findViewById(R.id.IV_diary_checklist);
        IV_diary_audio = (ImageView) rootView.findViewById(R.id.IV_diary_audio);

        diaryItemHelper = new DiaryItemHelper(LL_diary_item_content);
        photoCropController = new PhotoCropController(this, diaryItemHelper, new PhotoCropController.Listener() {
            @Override
            public void onPhotoCropped() {
                updateCover();
            }
        });
        diaryCover.setCropListener(new Runnable() {
            @Override
            public void run() {
                DiaryPhoto coverPhoto = diaryItemHelper.getThumbnailPhoto();
                if (coverPhoto != null && diaryFileManager != null) {
                    photoCropController.cropCover(coverPhoto.getContent(), diaryFileManager.getDir());
                }
            }
        });
        initView(rootView);
        noLocation = getString(R.string.diary_no_location);

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
                diaryViewerHandler = new DiaryViewerHandler(this);
                diaryFileManager = new FileManager(getActivity(), FileManager.DIARY_EDIT_CACHE_DIR);
                diaryFileManager.clearDir();
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
        //The screen that crops a photo to 4:3 is closed
        if (photoCropController != null && photoCropController.onActivityResult(requestCode, resultCode, data)) {
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        if (requestCode == PermissionHelper.REQUEST_ACCESS_FINE_LOCATION_PERMISSION) {
            //The exact or the approximate location are both fine
            if (grantResults.length > 0
                    && PermissionHelper.checkAnyPermissionResult(grantResults)) {
                firstAllowLocationPermission = true;
            } else {
                PermissionHelper.showAccessDialog(getActivity());
            }
        } else if (requestCode == PermissionHelper.REQUEST_CAMERA_AND_WRITE_ES_PERMISSION) {
            if (grantResults.length > 0
                    && PermissionHelper.checkAllPermissionResult(grantResults)) {
                firstAllowCameraPermission = true;
            } else {
                AlertDialog.Builder builder = new AlertDialog.Builder(getActivity())
                        .setTitle(getString(R.string.diary_location_permission_title))
                        .setMessage(getString(R.string.diary_photo_permission_content))
                        .setPositiveButton(getString(R.string.dialog_button_ok), null);
                builder.show();
            }
        }
    }


    @Override
    public void onResume() {
        super.onResume();
        //For PermissionsResult
        if (firstAllowLocationPermission) {
            startGetLocation();
            firstAllowLocationPermission = false;
        }
        if (firstAllowCameraPermission) {
            openPhotoBottomSheet();
            firstAllowCameraPermission = false;
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        //Modify dialog size
        Dialog dialog = getDialog();
        if (dialog != null) {
            int dialogHeight;
            if (ChinaPhoneHelper.getDeviceStatusBarType() == ChinaPhoneHelper.OTHER) {
                dialogHeight = ScreenHelper.getScreenHeight(getActivity())
                        - ScreenHelper.getStatusBarHeight(getActivity())
                        - ScreenHelper.dpToPixel(getActivity().getResources(), 2 * 10);
            } else {
                dialogHeight = ScreenHelper.getScreenHeight(getActivity())
                        - ScreenHelper.dpToPixel(getActivity().getResources(), 2 * 10);
            }
            int dialogWidth = ScreenHelper.getScreenWidth(getActivity())
                    - ScreenHelper.dpToPixel(getActivity().getResources(), 2 * 5);
            dialog.getWindow().setLayout(dialogWidth, dialogHeight);
        }
    }

    @Override
    public void onStop() {
        super.onStop();
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
        if (locationManager != null) {
            try {
                locationManager.removeUpdates(locationListener);
            } catch (SecurityException e) {
                e.printStackTrace();
            }
        }
        if (diaryViewerHandler != null) {
            diaryViewerHandler.removeCallbacksAndMessages(null);
        }
        if (progressDialog != null) {
            progressDialog.dismiss();
        }
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        super.onDismiss(dialog);
    }


    private void initData() {
        DBManager dbManager = new DBManager(getActivity());
        dbManager.opeDB();
        Cursor diaryInfoCursor = dbManager.selectDiaryInfoByDiaryId(diaryId);
        //load Time
        calendar = Calendar.getInstance();
        calendar.setTimeInMillis(diaryInfoCursor.getLong(1));
        timeTools = TimeTools.getInstance(getActivity().getApplicationContext());
        sdf = new SimpleDateFormat("HH:mm");
        setDiaryTime();
        int tagsColumn = diaryInfoCursor.getColumnIndex(DBStructure.DiaryEntry_V2.COLUMN_TAGS);
        String tagsText = tagsColumn >= 0 ? TagHelper.toDisplay(diaryInfoCursor.getString(tagsColumn)) : "";
        savedTags = tagsColumn >= 0 && diaryInfoCursor.getString(tagsColumn) != null
                ? diaryInfoCursor.getString(tagsColumn) : "";
        diaryCover.setTags(savedTags);
        if (isEditMode) {
            //Allow to edit diary
            LL_diary_time_information.setOnClickListener(this);
            EDT_diary_title.setText(diaryInfoCursor.getString(2));
            EDT_diary_tags.setText(tagsText.length() > 0 ? tagsText + " " : "");
        } else {
            //The title is on the cover
            diaryCover.setTitle(diaryInfoCursor.getString(2));
        }
        int thumbnailColumn = diaryInfoCursor.getColumnIndex(DBStructure.DiaryEntry_V2.COLUMN_THUMBNAIL);
        String chosenThumbnail = thumbnailColumn >= 0 ? diaryInfoCursor.getString(thumbnailColumn) : "";
        //load location
        String locationName = diaryInfoCursor.getString(7);
        if (locationName != null && !"".equals(locationName)) {
            haveLocation = true;
            TV_diary_location.setText(locationName);
        } else {
            haveLocation = false;
        }
        initLocationIcon();
        setIcon(diaryInfoCursor.getInt(3), diaryInfoCursor.getInt(4));
        diaryInfoCursor.close();
        //Get diary detail
        loadDiaryItemContent(dbManager);
        diaryItemHelper.setThumbnailFileName(chosenThumbnail);
        updateCover();
        dbManager.closeDB();
    }

    /**
     * The cover shows the photo that is the thumbnail (chosen with the star, or the first photo).
     */
    private void updateCover() {
        if (diaryCover == null || diaryItemHelper == null) {
            return;
        }
        diaryItemHelper.refreshThumbnailMarks();
        diaryCover.setCover(diaryItemHelper.getThumbnailUriString());
    }

    /**
     * The pencil / "Edit" of the tags on the cover: a small window to write them.
     * Editing the entry: they are kept in the hidden box and saved with the entry.
     * Only reading: they are saved right away.
     */
    private void onCoverEditTags() {
        String current = isEditMode ? TagHelper.normalize(EDT_diary_tags.getText().toString()) : savedTags;
        TagHelper.showEditDialog(getActivity(), current, new TagHelper.Callback() {
            @Override
            public void onTags(String newTags) {
                if (isEditMode) {
                    //The text watcher updates the cover
                    EDT_diary_tags.setText(TagHelper.toDisplay(newTags));
                } else {
                    saveTagsOnly(newTags);
                }
            }
        });
    }

    private void saveTagsOnly(String newTags) {
        try {
            DBManager dbManager = new DBManager(getActivity());
            dbManager.opeDB();
            dbManager.updateDiaryTags(diaryId, newTags);
            dbManager.closeDB();
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
            initProgressDialog();
            initLocationManager();

            LL_diary_time_information = (LinearLayout) rootView.findViewById(R.id.LL_diary_time_information);
            SP_diary_mood = (Spinner) rootView.findViewById(R.id.SP_diary_mood);
            SP_diary_mood.setVisibility(View.VISIBLE);
            SP_diary_weather = (Spinner) rootView.findViewById(R.id.SP_diary_weather);
            SP_diary_weather.setVisibility(View.VISIBLE);

            //For hidden hint
            EDT_diary_title.setText(" ");
            EDT_diary_title.getBackground().mutate().setColorFilter(ThemeManager.getInstance().getThemeMainColor(getActivity()),
                    PorterDuff.Mode.SRC_ATOP);
            EDT_diary_title.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                }

                @Override
                public void afterTextChanged(Editable s) {
                    diaryCover.setTitle(s.toString());
                }
            });
            EDT_diary_tags.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                }

                @Override
                public void afterTextChanged(Editable s) {
                    diaryCover.setTags(TagHelper.normalize(s.toString()));
                }
            });
            //Only keeps the text: the tags are written in the window of the pencil of the cover
            EDT_diary_tags.setVisibility(View.GONE);

            initMoodSpinner();
            initWeatherSpinner();
            //The cover shows the weather that is chosen (the icon and its name while editing)
            SP_diary_weather.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    diaryCover.setWeather(position);
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {
                }
            });
            IV_diary_location.setOnClickListener(this);
            TV_diary_location.setOnClickListener(this);

            IV_diary_delete.setOnClickListener(this);
            IV_diary_clear.setVisibility(View.GONE);

            IV_diary_photo.setImageResource(R.drawable.ic_photo_camera_white_24dp);
            IV_diary_photo.setOnClickListener(this);

            IV_diary_bold.setVisibility(View.VISIBLE);
            IV_diary_bold.setOnClickListener(this);
            IV_diary_italic.setVisibility(View.VISIBLE);
            IV_diary_italic.setOnClickListener(this);
            IV_diary_list.setVisibility(View.VISIBLE);
            IV_diary_list.setOnClickListener(this);
            IV_diary_checklist.setVisibility(View.VISIBLE);
            IV_diary_checklist.setOnClickListener(this);
            IV_diary_audio.setVisibility(View.VISIBLE);
            IV_diary_audio.setOnClickListener(this);
        } else {
            EDT_diary_title.setVisibility(View.GONE);
            RL_diary_weather = (RelativeLayout) rootView.findViewById(R.id.RL_diary_weather);
            RL_diary_weather.setVisibility(View.GONE);
            RL_diary_mood = (RelativeLayout) rootView.findViewById(R.id.RL_diary_mood);
            RL_diary_mood.setVisibility(View.GONE);

            //The weather of the cover: only the icon (see setIcon)
            IV_diary_location_name_icon.setVisibility(View.VISIBLE);

            //The title is on the cover now
            TV_diary_title_content = (TextView) rootView.findViewById(R.id.TV_diary_title_content);
            TV_diary_title_content.setVisibility(View.GONE);

            IV_diary_delete.setOnClickListener(this);
            //Move / copy to another diary (only when reading: the changes of an edition are not saved yet)
            IV_diary_move.setVisibility(View.VISIBLE);
            IV_diary_move.setOnClickListener(this);
            IV_diary_clear.setVisibility(View.GONE);
            IV_diary_save.setVisibility(View.GONE);

            IV_diary_photo.setImageResource(R.drawable.ic_photo_white_24dp);
            IV_diary_photo.setOnClickListener(this);
        }
    }


    private void initMoodSpinner() {
        ImageArrayAdapter moodArrayAdapter = new ImageArrayAdapter(getActivity(), DiaryInfoHelper.getMoodArray());
        SP_diary_mood.setAdapter(moodArrayAdapter);
    }

    private void initWeatherSpinner() {
        ImageArrayAdapter weatherArrayAdapter = new ImageArrayAdapter(getActivity(), DiaryInfoHelper.getWeatherArray());
        SP_diary_weather.setAdapter(weatherArrayAdapter);
    }

    private void loadDiaryItemContent(DBManager dbManager) {
        Cursor diaryContentCursor = dbManager.selectDiaryContentByDiaryId(diaryId);
        //To count how many photo is in the diary on view mode.
        int photoCount = 0;
        for (int i = 0; i < diaryContentCursor.getCount(); i++) {
            IDairyRow diaryItem = null;
            String content = "";
            if (diaryContentCursor.getInt(1) == IDairyRow.TYPE_PHOTO) {
                diaryItem = new DiaryPhoto(getActivity());
                content = FileManager.FILE_HEADER +
                        diaryFileManager.getDirAbsolutePath() + "/" + diaryContentCursor.getString(3);
                //For get the right file name (also when only reading: the cover is found by it)
                ((DiaryPhoto) diaryItem).setPhotoFileName(diaryContentCursor.getString(3));
                if (isEditMode) {
                    diaryItem.setEditMode(true);
                    ((DiaryPhoto) diaryItem).setDeleteClickListener(this);
                    ((DiaryPhoto) diaryItem).setThumbnailClickListener(this);
                    ((DiaryPhoto) diaryItem).setCropClickListener(this);
                } else {
                    diaryItem.setEditMode(false);
                    ((DiaryPhoto) diaryItem).setDraweeViewClickListener(this);
                    ((DiaryPhoto) diaryItem).setDraweeViewPositionTag(photoCount);
                    photoCount++;
                    diaryPhotoFileList.add(Uri.parse(content));
                }
            } else if (diaryContentCursor.getInt(1) == IDairyRow.TYPE_TEXT) {
                diaryItem = new DiaryText(getActivity());
                content = diaryContentCursor.getString(3);
                if (!isEditMode) {
                    diaryItem.setEditMode(false);
                }
            } else if (diaryContentCursor.getInt(1) == IDairyRow.TYPE_AUDIO) {
                diaryItem = new DiaryAudio(getActivity());
                content = diaryContentCursor.getString(3);
                if (isEditMode) {
                    diaryItem.setEditMode(true);
                    ((DiaryAudio) diaryItem).setDeleteClickListener(this);
                } else {
                    diaryItem.setEditMode(false);
                }
            } else if (diaryContentCursor.getInt(1) == IDairyRow.TYPE_CHECKLIST) {
                diaryItem = new DiaryChecklist(getActivity(), diaryItemHelper);
                content = diaryContentCursor.getString(3);
                if (!isEditMode) {
                    //Not editing: the text can't be changed, but the boxes can be ticked (saved right away)
                    diaryItem.setEditMode(false);
                    ((DiaryChecklist) diaryItem).setToggleListener(new DiaryChecklist.ToggleListener() {
                        @Override
                        public void onToggled(DiaryChecklist item) {
                            saveChecklistState(item);
                        }
                    });
                }
            } else {
                //Unknown kind of item: skip it
                diaryContentCursor.moveToNext();
                continue;
            }
            diaryItem.setContent(content);
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

    private void initProgressDialog() {
        progressDialog = new ProgressDialog(getActivity());
        progressDialog.setMessage(getString(R.string.process_dialog_loading));
        progressDialog.setCancelable(false);
        progressDialog.setProgressStyle(android.R.style.Widget_ProgressBar);
    }

    private void initLocationManager() {
        locationManager = (LocationManager) getActivity().getSystemService(LOCATION_SERVICE);
    }

    private void initLocationIcon() {
        if (haveLocation) {
            IV_diary_location.setImageResource(R.drawable.ic_location_on_white_24dp);
        } else {
            IV_diary_location.setImageResource(R.drawable.ic_location_off_white_24dp);
            TV_diary_location.setText(noLocation);
        }
        //Only reading: without a place there is no pin and no "No location" on the cover.
        //Editing: they stay, the person can tap the text to write a place.
        int visibility = (isEditMode || haveLocation) ? View.VISIBLE : View.GONE;
        IV_diary_location_name_icon.setVisibility(visibility);
        TV_diary_location.setVisibility(visibility);
    }

    private void startGetLocation() {
        openGPSListener();
    }

    /**
     * The tap on the place text: the person types the place by hand.
     */
    private void editLocationByHand() {
        String current = haveLocation ? TV_diary_location.getText().toString() : "";
        LocationHelper.showEditDialog(getActivity(), current, new LocationHelper.TextCallback() {
            @Override
            public void onText(String text) {
                TV_diary_location.setText(text);
                haveLocation = text.length() > 0;
                initLocationIcon();
            }
        });
    }

    /**
     * Called (main thread) when the phone found where it is, or when the waiting time ended.
     */
    private void onLocationFixed() {
        try {
            locationManager.removeUpdates(locationListener);
        } catch (SecurityException e) {
            e.printStackTrace();
        }
        final Location location = diaryLocations;
        diaryLocations = null;
        if (location == null) {
            Toast.makeText(getActivity(), getString(R.string.toast_location_timeout), Toast.LENGTH_LONG).show();
            progressDialog.dismiss();
            haveLocation = false;
            initLocationIcon();
            return;
        }
        LocationHelper.findPlaceName(getActivity(), location, new LocationHelper.NameCallback() {
            @Override
            public void onName(String name, boolean isAddress) {
                progressDialog.dismiss();
                if (!isAdded()) {
                    return;
                }
                if (!isAddress) {
                    Toast.makeText(getActivity(), getString(R.string.toast_geocoder_fail), Toast.LENGTH_LONG).show();
                }
                TV_diary_location.setText(name);
                haveLocation = true;
                initLocationIcon();
            }
        });
    }

    private void openGPSListener() {
        progressDialog.show();
        boolean started = false;
        List<String> providers = locationManager.getAllProviders();
        String[] kinds = {LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER};
        for (String kind : kinds) {
            if (providers.contains(kind)) {
                try {
                    locationManager.requestLocationUpdates(kind, 0, 0, locationListener);
                    started = true;
                } catch (SecurityException e) {
                    //This kind of location is not allowed (for example, the person allowed only the approximate one)
                    e.printStackTrace();
                } catch (IllegalArgumentException e) {
                    e.printStackTrace();
                }
            }
        }
        if (started) {
            //Waiting for the location: max timeout
            diaryViewerHandler.sendEmptyMessageDelayed(0, 20000);
        } else {
            progressDialog.dismiss();
            Toast.makeText(getActivity(), getString(R.string.toast_location_not_open), Toast.LENGTH_LONG).show();
        }
    }

    private LocationListener locationListener = new LocationListener() {
        @Override
        public void onLocationChanged(Location location) {
            diaryLocations = new Location(location);
            diaryViewerHandler.removeCallbacksAndMessages(null);
            diaryViewerHandler.sendEmptyMessage(0);
            try {
                locationManager.removeUpdates(this);
            } catch (SecurityException e) {
                e.printStackTrace();
            }

        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {

        }

        @Override
        public void onProviderEnabled(String provider) {

        }

        @Override
        public void onProviderDisabled(String provider) {

        }
    };


    private void setDiaryTime() {
        TV_diary_month.setText(timeTools.getMonthsFullName()[calendar.get(Calendar.MONTH)]);
        //Only the month and the day
        diaryCover.setDate(calendar);
        TV_diary_day.setText(timeTools.getDaysFullName()[calendar.get(Calendar.DAY_OF_WEEK) - 1]);
        TV_diary_time.setText(sdf.format(calendar.getTime()));
    }

    private void setIcon(int mood, int weather) {
        if (isEditMode) {
            SP_diary_mood.setSelection(mood);
            SP_diary_weather.setSelection(weather);
        }
        //Reading: only the icon. Editing: the icon and its name.
        diaryCover.setWeather(weather);
    }

    private void loadFileFromTemp(String fileName) {
        try {
            String tempFileSrc = FileManager.FILE_HEADER + diaryFileManager.getDirAbsolutePath() + "/" + fileName;
            DiaryPhoto diaryPhoto = new DiaryPhoto(getActivity());
            diaryPhoto.setPhoto(Uri.parse(tempFileSrc), fileName);
            DiaryTextTag tag = checkoutOldDiaryContent();
            //Check edittext is focused
            if (tag != null) {
                //Add new edittext
                DiaryText diaryText = new DiaryText(getActivity());
                diaryText.setPosition(tag.getPositionTag());
                diaryText.setContent(tag.getNextEditTextStr());
                diaryItemHelper.createItem(diaryText, tag.getPositionTag() + 1);
                diaryText.getView().requestFocus();
                //Add photo
                diaryPhoto.setPosition(tag.getPositionTag() + 1);
                diaryPhoto.setDeleteClickListener(this);
                diaryPhoto.setThumbnailClickListener(this);
                diaryPhoto.setCropClickListener(this);
                diaryItemHelper.createItem(diaryPhoto, tag.getPositionTag() + 1);
            } else {
                //Add photo
                diaryPhoto.setPosition(diaryItemHelper.getItemSize());
                diaryPhoto.setDeleteClickListener(this);
                diaryPhoto.setThumbnailClickListener(this);
                diaryPhoto.setCropClickListener(this);
                diaryItemHelper.createItem(diaryPhoto);
                //Add new edittext
                DiaryText diaryText = new DiaryText(getActivity());
                diaryText.setPosition(diaryItemHelper.getItemSize());
                diaryItemHelper.createItem(diaryText);
                diaryText.getView().requestFocus();
            }
        } catch (Exception e) {
            Log.e(TAG, e.toString());
            Toast.makeText(getActivity(), getString(R.string.toast_photo_path_error), Toast.LENGTH_LONG).show();
        } finally {
            diaryItemHelper.resortPosition();
            updateCover();
        }
    }

    private DiaryTextTag checkoutOldDiaryContent() {
        View focusView = getDialog().getCurrentFocus();
        DiaryTextTag tag = null;
        if (focusView instanceof EditText && focusView.getTag() != null &&
                focusView.getTag() instanceof DiaryTextTag) {
            EditText currentEditText = (EditText) focusView;
            tag = (DiaryTextTag) focusView.getTag();
            //Forget the text of a previous photo, or it would be added again
            tag.setNextEditTextStr(null);
            if (currentEditText.getText().toString().length() > 0) {
                int index = currentEditText.getSelectionStart();
                //Keep the bold of the text that goes to the next text box
                String nextEditTextStr = RichText.toMarkup(currentEditText.getText()
                        .subSequence(index, currentEditText.getText().length()));
                currentEditText.getText().delete(index, currentEditText.getText().length());
                tag.setNextEditTextStr(nextEditTextStr);
            }
        }
        return tag;
    }

    private void updateDiary() {

        //Create locationName
        String locationName = TV_diary_location.getText().toString();
        if (noLocation.equals(locationName)) {
            locationName = "";
        }
        new UpdateDiaryTask(getActivity(), calendar.getTimeInMillis(), EDT_diary_title.getText().toString(),
                SP_diary_mood.getSelectedItemPosition(), SP_diary_weather.getSelectedItemPosition(),
                locationName, TagHelper.normalize(EDT_diary_tags.getText().toString()),
                //Check  attachment
                diaryItemHelper.getNowPhotoCount() > 0 ? true : false,
                diaryItemHelper, diaryFileManager, this).execute(((DiaryActivity) getActivity()).getTopicId(), diaryId);

    }

    private void openPhotoBottomSheet() {
        DiaryPhotoBottomSheet diaryPhotoBottomSheet = DiaryPhotoBottomSheet.newInstance(true);
        diaryPhotoBottomSheet.setTargetFragment(this, 0);
        diaryPhotoBottomSheet.show(getFragmentManager(), "diaryPhotoBottomSheet");
    }

    @Override
    public void onDiaryUpdated() {
        this.dismissAllowingStateLoss();
        callback.updateDiary();
    }

    @Override
    public void selectPhoto(Uri uri) {
        List<Uri> uris = new ArrayList<>();
        uris.add(uri);
        selectPhotos(uris);
    }

    @Override
    public void selectPhotos(List<Uri> uris) {
        //The check "is it really a picture" is done while decoding (see CopyPhotoTask),
        //because the file name can't tell it reliably.
        pendingPhotoUris.addAll(uris);
        copyNextPendingPhoto();
    }

    private void copyNextPendingPhoto() {
        if (isCopyingPhoto || pendingPhotoUris.isEmpty() || getActivity() == null) {
            return;
        }
        isCopyingPhoto = true;
        Uri uri = pendingPhotoUris.poll();
        //1.Copy bitmap to temp for rotating & resize
        //2.Then Load bitmap call back ;
        new CopyPhotoTask(getActivity(), uri,
                DiaryItemHelper.getVisibleWidth(getActivity()), DiaryItemHelper.getVisibleHeight(getActivity()),
                diaryFileManager, this).execute();
    }

    @Override
    public void addPhoto(String fileName) {
        //1.get saved file for rotating & resize from temp
        //2.Then , Load bitmap in call back ;
        new CopyPhotoTask(getActivity(), fileName,
                DiaryItemHelper.getVisibleWidth(getActivity()), DiaryItemHelper.getVisibleHeight(getActivity()),
                diaryFileManager, this).execute();
    }

    @Override
    public void onCopyCompiled(String fileName) {
        isCopyingPhoto = false;
        if (getActivity() == null) {
            pendingPhotoUris.clear();
            return;
        }
        if (fileName != null) {
            loadFileFromTemp(fileName);
        } else {
            Toast.makeText(getActivity(), getString(R.string.toast_photo_load_failed), Toast.LENGTH_LONG).show();
        }
        //Next picture of the selection, if there is one
        copyNextPendingPhoto();
    }


    @Override
    public void onDiaryDelete() {
        callback.deleteDiary();
        dismiss();
    }

    @Override
    public void onBack() {
        dismiss();
    }

    @Override
    public void onDateSet(DatePicker view, int year, int monthOfYear, int dayOfMonth) {
        //Since JellyBean, the onDateSet() method of the DatePicker class is called twice
        if (view.isShown()) {
            calendar.set(year, monthOfYear, dayOfMonth);
            //Only the day is chosen: the hour and the minute are not asked any more
            setDiaryTime();
        }
    }

    @Override
    public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
        //Since JellyBean, the onTimeSet() method of the TimePicker class is called twice
        if (view.isShown()) {
            calendar.set(Calendar.HOUR_OF_DAY, hourOfDay);
            calendar.set(Calendar.MINUTE, minute);
            setDiaryTime();
        }
    }


    @Override
    public void onCopyToEditCacheCompiled(int result) {
        if (result == CopyDiaryToEditCacheTask.RESULT_COPY_SUCCESSFUL) {
            PB_diary_item_content_hint.setVisibility(View.GONE);
            initData();
            //Open the click listener
            IV_diary_clear.setOnClickListener(this);
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
                DatePickerFragment datePickerFragment = DatePickerFragment.newInstance(calendar.getTimeInMillis());
                datePickerFragment.setOnDateSetListener(this);
                datePickerFragment.show(getFragmentManager(), "datePickerFragment");
                break;
            case R.id.TV_diary_location:
                editLocationByHand();
                break;
            case R.id.IV_diary_location:
                if (haveLocation) {
                    haveLocation = false;
                    initLocationIcon();
                } else {
                    if (PermissionHelper.checkPermission(this, PermissionHelper.REQUEST_ACCESS_FINE_LOCATION_PERMISSION)) {
                        //Check gps is open
                        if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                            startGetLocation();
                        } else {
                            Toast.makeText(getActivity(), getString(R.string.toast_location_not_open), Toast.LENGTH_LONG).show();
                        }

                    }
                }
                break;
            case R.id.IV_diary_bold:
                if (!diaryItemHelper.toggleBoldAtFocus(getDialog().getCurrentFocus())) {
                    Toast.makeText(getActivity(), getString(R.string.toast_format_select_text), Toast.LENGTH_SHORT).show();
                }
                break;
            case R.id.IV_diary_italic:
                if (!diaryItemHelper.toggleItalicAtFocus(getDialog().getCurrentFocus())) {
                    Toast.makeText(getActivity(), getString(R.string.toast_format_select_text), Toast.LENGTH_SHORT).show();
                }
                break;
            case R.id.IV_diary_list:
                diaryItemHelper.showListMenu(getActivity(), IV_diary_list, getDialog().getCurrentFocus());
                break;
            case R.id.IV_diary_checklist:
                diaryItemHelper.insertChecklistAtFocus(getDialog().getCurrentFocus());
                break;
            case R.id.IV_diary_audio:
                AudioLinkDialog.show(getActivity(), new AudioLinkDialog.Callback() {
                    @Override
                    public void onAudioLink(String link) {
                        diaryItemHelper.insertAudioAtFocus(getDialog().getCurrentFocus(), link,
                                DiaryViewerDialogFragment.this);
                    }
                });
                break;
            case R.id.IV_diary_audio_delete:
                int audioPosition = (int) v.getTag();
                if (diaryItemHelper.get(audioPosition) instanceof DiaryAudio) {
                    ((DiaryAudio) diaryItemHelper.get(audioPosition)).release();
                }
                diaryItemHelper.remove(audioPosition);
                LL_diary_item_content.removeViewAt(audioPosition);
                diaryItemHelper.mergerAdjacentText(audioPosition);
                diaryItemHelper.resortPosition();
                break;
            case R.id.IV_diary_photo_delete:
                int deletePosition = (int) v.getTag();
                Log.e("test", "deletePosition = " + deletePosition);
                diaryItemHelper.remove(deletePosition);
                LL_diary_item_content.removeViewAt(deletePosition);
                diaryItemHelper.mergerAdjacentText(deletePosition);
                diaryItemHelper.resortPosition();
                updateCover();
                break;
            case R.id.IV_diary_photo_crop:
                IDairyRow cropRow = diaryItemHelper.get((int) v.getTag());
                if (cropRow instanceof DiaryPhoto) {
                    photoCropController.crop(cropRow.getContent(), diaryFileManager.getDir());
                }
                break;
            case R.id.IV_diary_photo_thumbnail:
                diaryItemHelper.chooseThumbnailAt((int) v.getTag());
                updateCover();
                Toast.makeText(getActivity(), getString(R.string.cover_thumbnail_chosen), Toast.LENGTH_SHORT).show();
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
                    if (FileManager.getSDCardFreeSize() > FileManager.MIN_FREE_SPACE) {
                        if (PermissionHelper.checkPermission(this, REQUEST_CAMERA_AND_WRITE_ES_PERMISSION)) {
                            if (diaryItemHelper.getNowPhotoCount() < DiaryItemHelper.MAX_PHOTO_COUNT) {
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
                    EditDiaryBackDialogFragment backDialogFragment = new EditDiaryBackDialogFragment();
                    backDialogFragment.setTargetFragment(DiaryViewerDialogFragment.this, 0);
                    backDialogFragment.show(getFragmentManager(), "backDialogFragment");
                } else {
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
            case R.id.IV_diary_clear:
                dismiss();
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

    private static class DiaryViewerHandler extends Handler {

        private WeakReference<DiaryViewerDialogFragment> mFrag;

        DiaryViewerHandler(DiaryViewerDialogFragment aFragment) {
            mFrag = new WeakReference<>(aFragment);
        }

        @Override
        public void handleMessage(Message msg) {
            DiaryViewerDialogFragment theFrag = mFrag.get();
            if (theFrag != null && theFrag.isAdded()) {
                theFrag.onLocationFixed();
            }
        }
    }
}
