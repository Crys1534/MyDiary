package com.kiminonawa.mydiary.main;

import android.app.Activity;
import android.content.Intent;
import androidx.recyclerview.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.kiminonawa.mydiary.R;
import com.kiminonawa.mydiary.contacts.ContactsActivity;
import com.kiminonawa.mydiary.entries.DiaryActivity;
import com.kiminonawa.mydiary.main.topic.ITopic;
import com.kiminonawa.mydiary.memo.MemoActivity;
import com.kiminonawa.mydiary.shared.ThemeManager;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * The list that appears over the topics when something is written in the search box:
 * first the topics with that word in the name, then the notes (of every topic) with that word in the
 * title or in the text.
 */
public class SearchResultsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_ROW = 1;

    private static class Row {
        int kind = TYPE_ROW;
        String header;
        String title;
        String snippet;
        String info;
        //What to open
        int topicType;
        long topicId;
        String topicName;
        long diaryId = -1;
    }

    private final Activity activity;
    private final List<Row> rows = new ArrayList<>();
    private final DateFormat dateFormat = DateFormat.getDateInstance(DateFormat.MEDIUM);

    //Called when a row is opened (for example to close the dialog that shows this list)
    private Runnable onOpen = null;

    public SearchResultsAdapter(Activity activity) {
        this.activity = activity;
    }

    public void setOnOpenListener(Runnable onOpen) {
        this.onOpen = onOpen;
    }

    /**
     * "A day like today": the diaries written on this same day in other years, a title for every year.
     */
    public void setMemories(List<MemoriesHelper.Memory> memories) {
        rows.clear();
        int lastYear = Integer.MIN_VALUE;
        for (MemoriesHelper.Memory memory : memories) {
            if (memory.year != lastYear) {
                lastYear = memory.year;
                Row header = new Row();
                header.kind = TYPE_HEADER;
                header.header = memory.yearsAgo == 1
                        ? activity.getString(R.string.today_years_one, memory.year)
                        : activity.getString(R.string.today_years_many, memory.yearsAgo, memory.year);
                rows.add(header);
            }
            Row row = new Row();
            row.title = memory.title == null || memory.title.trim().length() == 0
                    ? activity.getString(R.string.diary_no_title) : memory.title.trim();
            row.snippet = memory.summary == null ? "" : memory.summary;
            row.info = memory.topicName;
            row.topicType = ITopic.TYPE_DIARY;
            row.topicId = memory.topicId;
            row.topicName = memory.topicName;
            row.diaryId = memory.diaryId;
            rows.add(row);
        }
        notifyDataSetChanged();
    }

    public void setResults(List<ITopic> topics, List<NoteSearch.Result> notes) {
        rows.clear();
        if (!topics.isEmpty()) {
            Row header = new Row();
            header.kind = TYPE_HEADER;
            header.header = activity.getString(R.string.search_section_topics);
            rows.add(header);
            for (ITopic topic : topics) {
                Row row = new Row();
                row.title = topic.getTitle();
                row.snippet = "";
                row.info = "";
                row.topicType = topic.getType();
                row.topicId = topic.getId();
                row.topicName = topic.getTitle();
                rows.add(row);
            }
        }
        if (!notes.isEmpty()) {
            Row header = new Row();
            header.kind = TYPE_HEADER;
            header.header = activity.getString(R.string.search_section_notes) + " (" + notes.size() + ")";
            rows.add(header);
            for (NoteSearch.Result note : notes) {
                Row row = new Row();
                row.title = note.title == null || note.title.trim().length() == 0
                        ? activity.getString(R.string.diary_no_title) : note.title.trim();
                row.snippet = note.snippet == null ? "" : note.snippet;
                row.info = note.topicName + "  •  " + dateFormat.format(new Date(note.time));
                row.topicType = ITopic.TYPE_DIARY;
                row.topicId = note.topicId;
                row.topicName = note.topicName;
                row.diaryId = note.diaryId;
                rows.add(row);
            }
        }
        if (rows.isEmpty()) {
            Row header = new Row();
            header.kind = TYPE_HEADER;
            header.header = activity.getString(R.string.search_no_results);
            rows.add(header);
        }
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return rows.get(position).kind;
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_HEADER) {
            return new HeaderHolder(inflater.inflate(R.layout.rv_search_header, parent, false));
        }
        return new RowHolder(inflater.inflate(R.layout.rv_search_result, parent, false));
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        final Row row = rows.get(position);
        if (holder instanceof HeaderHolder) {
            TextView header = ((HeaderHolder) holder).header;
            header.setText(row.header);
            header.setTextColor(ThemeManager.getInstance().getThemeMainColor(activity));
            return;
        }
        RowHolder rowHolder = (RowHolder) holder;
        rowHolder.title.setText(row.title);
        rowHolder.snippet.setText(row.snippet);
        rowHolder.snippet.setVisibility(row.snippet.length() > 0 ? View.VISIBLE : View.GONE);
        rowHolder.info.setText(row.info);
        rowHolder.info.setVisibility(row.info.length() > 0 ? View.VISIBLE : View.GONE);
        rowHolder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                open(row);
            }
        });
    }

    private void open(Row row) {
        Intent intent;
        switch (row.topicType) {
            case ITopic.TYPE_CONTACTS:
                intent = new Intent(activity, ContactsActivity.class);
                break;
            case ITopic.TYPE_MEMO:
                intent = new Intent(activity, MemoActivity.class);
                break;
            default:
                intent = new Intent(activity, DiaryActivity.class);
                intent.putExtra("has_entries", true);
                if (row.diaryId != -1) {
                    //The topic opens the note right away
                    intent.putExtra(DiaryActivity.EXTRA_OPEN_DIARY_ID, row.diaryId);
                }
                break;
        }
        intent.putExtra("topicId", row.topicId);
        intent.putExtra("diaryTitle", row.topicName);
        activity.startActivity(intent);
        if (onOpen != null) {
            onOpen.run();
        }
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    private static class HeaderHolder extends RecyclerView.ViewHolder {
        final TextView header;

        HeaderHolder(View itemView) {
            super(itemView);
            header = (TextView) itemView.findViewById(R.id.TV_search_header);
        }
    }

    private static class RowHolder extends RecyclerView.ViewHolder {
        final TextView title, snippet, info;

        RowHolder(View itemView) {
            super(itemView);
            title = (TextView) itemView.findViewById(R.id.TV_search_result_title);
            snippet = (TextView) itemView.findViewById(R.id.TV_search_result_snippet);
            info = (TextView) itemView.findViewById(R.id.TV_search_result_info);
        }
    }
}
