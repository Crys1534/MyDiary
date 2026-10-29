package com.kiminonawa.mydiary.main;

import android.app.Activity;
import android.content.Intent;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.recyclerview.widget.RecyclerView;

import com.kiminonawa.mydiary.R;
import com.kiminonawa.mydiary.entries.DiaryActivity;
import com.kiminonawa.mydiary.entries.diary.DiaryInfoHelper;
import com.kiminonawa.mydiary.entries.diary.TagHelper;
import com.kiminonawa.mydiary.shared.ThemeManager;
import com.kiminonawa.mydiary.shared.TimeTools;

import java.util.List;

/**
 * The list of "A day like today": the same cards as the list of entries (the small picture, the date with the tags,
 * the title, the weather, the mood), with a title for every year.
 */
public class MemoriesAdapter extends RecyclerView.Adapter<EntryCardHolder> {

    private final Activity activity;
    private final List<MemoriesHelper.Memory> memories;
    private final TimeTools timeTools;
    private final int thumbnailSizePx;
    private final int themeColor;
    //Called when an entry is opened (to close the window that shows this list)
    private Runnable onOpen = null;

    public MemoriesAdapter(Activity activity, List<MemoriesHelper.Memory> memories) {
        this.activity = activity;
        this.memories = memories;
        this.timeTools = TimeTools.getInstance(activity.getApplicationContext());
        this.thumbnailSizePx = (int) (82 * activity.getResources().getDisplayMetrics().density);
        this.themeColor = ThemeManager.getInstance().getThemeDarkColor(activity);
    }

    public void setOnOpenListener(Runnable onOpen) {
        this.onOpen = onOpen;
    }

    @Override
    public EntryCardHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.rv_entries_item, parent, false);
        return new EntryCardHolder(view, themeColor);
    }

    @Override
    public void onBindViewHolder(EntryCardHolder holder, int position) {
        final MemoriesHelper.Memory memory = memories.get(position);

        //A title for the first entry of every year
        boolean newYear = position == 0 || memories.get(position - 1).year != memory.year;
        if (newYear) {
            holder.header.setVisibility(View.VISIBLE);
            holder.header.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 24);
            //a little air: the title must not touch the card under it
            int gap = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 6,
                    activity.getResources().getDisplayMetrics());
            holder.header.setPadding(0, gap, 0, gap);
            holder.header.setText(memory.yearsAgo == 1
                    ? activity.getString(R.string.today_years_one, memory.year)
                    : activity.getString(R.string.today_years_many, memory.yearsAgo, memory.year));
        } else {
            holder.header.setVisibility(View.GONE);
        }

        String tagsText = TagHelper.toDisplay(memory.tags);
        holder.time.setText(timeTools.monthAndDay(memory.time)
                + (tagsText.length() > 0 ? "   " + tagsText : ""));
        holder.title.setText(memory.title == null || memory.title.trim().length() == 0
                ? activity.getString(R.string.diary_no_title) : memory.title.trim());
        holder.weather.setImageResource(DiaryInfoHelper.getWeatherResourceId(memory.weather));
        holder.mood.setImageResource(DiaryInfoHelper.getMoodResourceId(memory.mood));
        holder.attachment.setVisibility(memory.hasAttachment ? View.VISIBLE : View.GONE);

        holder.bindThumbnail(memory.thumbnailPath, memory.thumbnailArea, thumbnailSizePx);

        holder.card.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                open(memory);
            }
        });
    }

    private void open(MemoriesHelper.Memory memory) {
        Intent intent = new Intent(activity, DiaryActivity.class);
        intent.putExtra("has_entries", true);
        //The topic opens the entry right away
        intent.putExtra(DiaryActivity.EXTRA_OPEN_DIARY_ID, memory.diaryId);
        intent.putExtra("topicId", memory.topicId);
        intent.putExtra("diaryTitle", memory.topicName);
        activity.startActivity(intent);
        if (onOpen != null) {
            onOpen.run();
        }
    }

    @Override
    public int getItemCount() {
        return memories.size();
    }
}
