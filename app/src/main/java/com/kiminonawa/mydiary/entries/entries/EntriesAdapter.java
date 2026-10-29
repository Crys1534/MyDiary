package com.kiminonawa.mydiary.entries.entries;

import androidx.recyclerview.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.kiminonawa.mydiary.R;
import com.kiminonawa.mydiary.entries.diary.DiaryInfoHelper;
import com.kiminonawa.mydiary.entries.diary.TagHelper;
import com.kiminonawa.mydiary.main.EntryCardHolder;
import com.kiminonawa.mydiary.shared.EditMode;
import com.kiminonawa.mydiary.shared.ThemeManager;
import com.kiminonawa.mydiary.shared.TimeTools;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;

/**
 * Created by daxia on 2016/10/17.
 */

public class EntriesAdapter extends RecyclerView.Adapter<EntryCardHolder> implements EditMode {


    private List<EntriesEntity> entriesList;
    private EntriesFragment mFragment;
    //Only the month and the day, like "October 21" / "Octubre 21" (the hour is not shown any more)
    private TimeTools timeTools;
    private String[] monthsFullName;
    private ThemeManager themeManager;

    private boolean isEditMode = false;
    //The thumbnail is 82dp (it touches the edges of the card)
    private int thumbnailSizePx;

    public EntriesAdapter(EntriesFragment fragment, List<EntriesEntity> topicList) {
        this.mFragment = fragment;
        this.entriesList = topicList;
        this.timeTools = TimeTools.getInstance(mFragment.getActivity().getApplicationContext());
        this.monthsFullName = timeTools.getMonthsFullName();
        this.themeManager = ThemeManager.getInstance();
        this.thumbnailSizePx = (int) (82 * mFragment.getResources().getDisplayMetrics().density);
    }


    @Override
    public EntryCardHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.rv_entries_item, parent, false);
        final EntryCardHolder holder = new EntryCardHolder(view, themeManager.getThemeDarkColor(mFragment.getActivity()));
        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //Single click: open the entry (to edit it when the edit mode of the list is on)
                openEntry(holder.getAdapterPosition(), isEditMode);
            }
        });
        holder.itemView.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                //Long click always opens the entry to edit it
                openEntry(holder.getAdapterPosition(), true);
                return true;
            }
        });
        return holder;
    }

    private void openEntry(int position, boolean edit) {
        if (position == RecyclerView.NO_POSITION) {
            return;
        }
        DiaryViewerDialogFragment diaryViewerDialog =
                DiaryViewerDialogFragment.newInstance(entriesList.get(position).getId(), edit);
        diaryViewerDialog.setTargetFragment(mFragment, 0);
        //Revert the icon
        if (isEditMode) {
            mFragment.setEditModeUI(isEditMode);
        }
        diaryViewerDialog.show(mFragment.getFragmentManager(), "diaryViewerDialog");
    }

    @Override
    public int getItemCount() {
        return entriesList.size();
    }

    @Override
    public void onBindViewHolder(EntryCardHolder holder, final int position) {
        EntriesEntity entity = entriesList.get(position);
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(entity.getCreateDate());

        if (showHeader(position)) {
            holder.header.setVisibility(View.VISIBLE);
            holder.header.setText(monthsFullName[calendar.get(Calendar.MONTH)]);
        } else {
            holder.header.setVisibility(View.GONE);
        }

        //Month and day, then the tags (without #)
        String tagsText = TagHelper.toDisplay(entity.getTags());
        holder.time.setText(timeTools.monthAndDay(calendar.getTimeInMillis())
                + (tagsText.length() > 0 ? "   " + tagsText : ""));
        holder.title.setText(entity.getTitle());
        holder.title.setMaxLines(2);
        holder.snippet.setVisibility(View.GONE);

        holder.status.setVisibility(View.VISIBLE);
        holder.weather.setImageResource(DiaryInfoHelper.getWeatherResourceId(entity.getWeatherId()));
        holder.mood.setImageResource(DiaryInfoHelper.getMoodResourceId(entity.getMoodId()));
        holder.attachment.setVisibility(entity.hasAttachment() ? View.VISIBLE : View.GONE);

        //Small picture of the cover photo (the part that was chosen)
        holder.bindThumbnail(entity.getThumbnailPath(), entity.getThumbnailArea(), thumbnailSizePx);
    }


    private boolean showHeader(final int position) {
        if (position == 0) {
            return true;
        } else {
            Calendar previousCalendar = new GregorianCalendar();
            previousCalendar.setTime(entriesList.get(position - 1).getCreateDate());
            Calendar currentCalendar = new GregorianCalendar();
            currentCalendar.setTime(entriesList.get(position).getCreateDate());
            if (previousCalendar.get(Calendar.YEAR) != currentCalendar.get(Calendar.YEAR)) {
                return true;
            } else {
                if (previousCalendar.get(Calendar.MONTH) != currentCalendar.get(Calendar.MONTH)) {
                    return true;
                } else {
                    return false;
                }
            }
        }
    }

    @Override
    public boolean isEditMode() {
        return isEditMode;
    }

    @Override
    public void setEditMode(boolean editMode) {
        isEditMode = editMode;
    }
}
