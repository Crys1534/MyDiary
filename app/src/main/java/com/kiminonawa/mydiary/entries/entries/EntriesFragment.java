package com.kiminonawa.mydiary.entries.entries;


import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.kiminonawa.mydiary.R;
import com.kiminonawa.mydiary.entries.BaseDiaryFragment;
import com.kiminonawa.mydiary.entries.DiaryActivity;
import com.kiminonawa.mydiary.entries.photo.PhotoOverviewActivity;
import com.kiminonawa.mydiary.shared.ThemeManager;
import com.kiminonawa.mydiary.shared.ViewTools;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


public class EntriesFragment extends BaseDiaryFragment implements
        DiaryViewerDialogFragment.DiaryViewerCallback, View.OnClickListener {

    /**
     * UI
     */
    private TextView TV_entries_count;
    private RelativeLayout RL_entries_edit_bar;
    private TextView TV_entries_edit_msg;
    private ImageView IV_entries_edit, IV_entries_photo;
    private TextView TV_entries_tag;
    //Chip with the year (only when the entries on the screen are not from this year)
    private TextView TV_entries_year;

    /**
     * Tag filter: the list that is shown is the diary list without the entries that don't have the tag
     */
    private List<EntriesEntity> shownList = new ArrayList<>();
    private String tagFilter = null;

    /**
     * RecyclerView
     */
    private RecyclerView RecyclerView_entries;
    private EntriesAdapter entriesAdapter;

    public EntriesFragment() {
        // Required empty public constructor
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_entries, container, false);

        IV_entries_edit = (ImageView) rootView.findViewById(R.id.IV_entries_edit);
        IV_entries_edit.setOnClickListener(this);
        IV_entries_photo = (ImageView) rootView.findViewById(R.id.IV_entries_photo);
        IV_entries_photo.setOnClickListener(this);
        TV_entries_tag = (TextView) rootView.findViewById(R.id.TV_entries_tag);
        TV_entries_tag.setOnClickListener(this);
        TV_entries_year = (TextView) rootView.findViewById(R.id.TV_entries_year);
        TV_entries_edit_msg = (TextView) rootView.findViewById(R.id.TV_entries_edit_msg);
        TV_entries_edit_msg.setTextColor(ThemeManager.getInstance().getThemeMainColor(getActivity()));

        RecyclerView_entries = (RecyclerView) rootView.findViewById(R.id.RecyclerView_entries);
        TV_entries_count = (TextView) rootView.findViewById(R.id.TV_entries_count);
        RL_entries_edit_bar = (RelativeLayout) rootView.findViewById(R.id.RL_entries_edit_bar);
        RL_entries_edit_bar.setBackgroundColor(ThemeManager.getInstance().getThemeMainColor(getActivity()));
        return rootView;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        initRecyclerView();
        countEntries();
        //Open a diary right away when the app asked for it (memories)
        final long openDiaryId = ((DiaryActivity) getActivity()).consumeOpenDiaryId();
        if (openDiaryId != -1) {
            new Handler().post(new Runnable() {
                @Override
                public void run() {
                    if (isAdded()) {
                        openDiary(openDiaryId);
                    }
                }
            });
        }
    }

    private void openDiary(long diaryId) {
        DiaryViewerDialogFragment diaryViewerDialog = DiaryViewerDialogFragment.newInstance(diaryId, false);
        diaryViewerDialog.setTargetFragment(this, 0);
        diaryViewerDialog.show(getFragmentManager(), "diaryViewerDialog");
    }

    @Override
    public void onStart() {
        super.onStart();
    }


    private void initRecyclerView() {
        LinearLayoutManager lmr = new LinearLayoutManager(getActivity());
        RecyclerView_entries.setLayoutManager(lmr);
        applyTagFilter();
        entriesAdapter = new EntriesAdapter(EntriesFragment.this, shownList);
        RecyclerView_entries.setAdapter(entriesAdapter);
        RecyclerView_entries.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                updateYearChip();
            }
        });
        RecyclerView_entries.post(new Runnable() {
            @Override
            public void run() {
                updateYearChip();
            }
        });
        //true for close all view
        setEditModeUI(true);
    }


    /**
     * Shows the year at the top (centered) when the first entry on the screen is not from this year.
     */
    private void updateYearChip() {
        if (TV_entries_year == null || RecyclerView_entries == null || !isAdded()) {
            return;
        }
        RecyclerView.LayoutManager lm = RecyclerView_entries.getLayoutManager();
        int first = lm instanceof LinearLayoutManager
                ? ((LinearLayoutManager) lm).findFirstVisibleItemPosition() : -1;
        if (first < 0 || first >= shownList.size()) {
            TV_entries_year.setVisibility(View.GONE);
            return;
        }
        Calendar entryDate = Calendar.getInstance();
        entryDate.setTime(shownList.get(first).getCreateDate());
        int year = entryDate.get(Calendar.YEAR);
        if (year != Calendar.getInstance().get(Calendar.YEAR)) {
            TV_entries_year.setText(String.valueOf(year));
            TV_entries_year.setVisibility(View.VISIBLE);
        } else {
            TV_entries_year.setVisibility(View.GONE);
        }
    }

    private void countEntries() {
        TV_entries_count.setText(
                getResources().getQuantityString(R.plurals.entries_count,
                        shownList.size(), shownList.size()));
        //The list changed: the year chip may have to change too
        RecyclerView_entries.post(new Runnable() {
            @Override
            public void run() {
                updateYearChip();
            }
        });
    }

    public void setEditModeUI(boolean isEditMode) {
        if (isEditMode) {
            entriesAdapter.setEditMode(false);
            TV_entries_edit_msg.setVisibility(View.GONE);
            IV_entries_edit.setImageDrawable(ViewTools.getDrawable(getActivity(), R.drawable.ic_mode_edit_white_24dp));
        } else {
            entriesAdapter.setEditMode(true);
            TV_entries_edit_msg.setVisibility(View.VISIBLE);
            IV_entries_edit.setImageDrawable(ViewTools.getDrawable(getActivity(), R.drawable.ic_mode_edit_cancel_white_24dp));
        }
    }

    public void gotoDiaryPosition(int position) {
        //The position comes from the calendar, that knows the whole list
        if (tagFilter != null) {
            tagFilter = null;
            applyTagFilter();
            entriesAdapter.notifyDataSetChanged();
            countEntries();
        }
        RecyclerView_entries.scrollToPosition(position);
    }

    /**
     * Fill shownList with the entries that have the chosen tag (all of them if there is no tag).
     */
    private void applyTagFilter() {
        List<EntriesEntity> allEntries = getEntriesList();
        if (tagFilter != null) {
            //The tag may be gone (entry deleted or edited)
            boolean tagStillExists = false;
            for (EntriesEntity entity : allEntries) {
                if (entity.getTagList().contains(tagFilter)) {
                    tagStillExists = true;
                    break;
                }
            }
            if (!tagStillExists) {
                tagFilter = null;
            }
        }
        shownList.clear();
        for (EntriesEntity entity : allEntries) {
            if (tagFilter == null || entity.getTagList().contains(tagFilter)) {
                shownList.add(entity);
            }
        }
        //"#" is the button; while a tag is chosen the tag is shown (without #)
        TV_entries_tag.setText(tagFilter == null ? "#" : tagFilter);
    }

    private void showTagFilterDialog() {
        //Count how many entries use every tag
        final Map<String, Integer> tagCount = new LinkedHashMap<>();
        for (EntriesEntity entity : getEntriesList()) {
            for (String tag : entity.getTagList()) {
                Integer count = tagCount.get(tag);
                tagCount.put(tag, count == null ? 1 : count + 1);
            }
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle(R.string.tags_filter_title);
        if (tagCount.isEmpty()) {
            builder.setMessage(R.string.tags_filter_none);
            builder.setPositiveButton(R.string.dialog_button_ok, null);
        } else {
            final List<String> tags = new ArrayList<>(tagCount.keySet());
            //The first row is "all entries"
            String[] rows = new String[tags.size() + 1];
            rows[0] = getString(R.string.tags_filter_all);
            for (int i = 0; i < tags.size(); i++) {
                rows[i + 1] = tags.get(i) + "  (" + tagCount.get(tags.get(i)) + ")";
            }
            builder.setItems(rows, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    tagFilter = which == 0 ? null : tags.get(which - 1);
                    applyTagFilter();
                    entriesAdapter.notifyDataSetChanged();
                    countEntries();
                }
            });
        }
        builder.show();
    }

    public void updateEntriesData() {
        updateEntriesList();
        applyTagFilter();
        entriesAdapter.notifyDataSetChanged();
        countEntries();
        RecyclerView_entries.post(new Runnable() {
            @Override
            public void run() {
                updateYearChip();
            }
        });
        ((DiaryActivity) getActivity()).callCalendarRefresh();
    }

    @Override
    public void deleteDiary() {
        updateEntriesData();
    }

    @Override
    public void updateDiary() {
        updateEntriesData();
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.IV_entries_edit:
                setEditModeUI(entriesAdapter.isEditMode());
                break;
            case R.id.TV_entries_tag:
                showTagFilterDialog();
                break;
            case R.id.IV_entries_photo:
                Intent gotoPhotoOverviewIntent = new Intent(getActivity(), PhotoOverviewActivity.class);
                gotoPhotoOverviewIntent.putExtra(PhotoOverviewActivity.PHOTO_OVERVIEW_TOPIC_ID, getTopicId());
                getActivity().startActivity(gotoPhotoOverviewIntent);
                break;

        }
    }
}
