package com.kiminonawa.mydiary.main;

import android.app.Activity;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.kiminonawa.mydiary.R;
import com.kiminonawa.mydiary.contacts.ContactsActivity;
import com.kiminonawa.mydiary.entries.DiaryActivity;
import com.kiminonawa.mydiary.entries.diary.DiaryInfoHelper;
import com.kiminonawa.mydiary.entries.diary.TagHelper;
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
 * title or in the text. Every result is drawn as the card of the list of entries (small picture, date,
 * tags, title and icons).
 */
public class SearchResultsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_CARD = 1;

    private static class Row {
        int kind = TYPE_CARD;
        String header;
        //The first line (date and tags), the title and the line under it (diary and a piece of the text)
        String time = "";
        String title = "";
        String snippet = "";
        //Like in the list of entries
        boolean isNote;
        int weather;
        int mood;
        boolean hasAttachment;
        String thumbnailPath;
        //What to open
        int topicType;
        long topicId;
        String topicName;
        long diaryId = -1;
    }

    private final Activity activity;
    private final List<Row> rows = new ArrayList<>();
    private final DateFormat dateFormat = DateFormat.getDateInstance(DateFormat.MEDIUM);
    private final int thumbnailSizePx;
    private final int themeColor;
    private final String[] topicTypeNames;

    public SearchResultsAdapter(Activity activity) {
        this.activity = activity;
        this.thumbnailSizePx = (int) (82 * activity.getResources().getDisplayMetrics().density);
        this.themeColor = ThemeManager.getInstance().getThemeDarkColor(activity);
        this.topicTypeNames = activity.getResources().getStringArray(R.array.topic_type);
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
                row.time = topic.getType() >= 0 && topic.getType() < topicTypeNames.length
                        ? topicTypeNames[topic.getType()] : "";
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
                row.isNote = true;
                String tags = TagHelper.toDisplay(note.tags);
                row.time = dateFormat.format(new Date(note.time)) + (tags.length() > 0 ? "   " + tags : "");
                row.title = note.title == null || note.title.trim().length() == 0
                        ? activity.getString(R.string.diary_no_title) : note.title.trim();
                //The diary of the note and the piece of text around the word
                row.snippet = note.topicName
                        + (note.snippet != null && note.snippet.length() > 0 ? "  •  " + note.snippet : "");
                row.weather = note.weather;
                row.mood = note.mood;
                row.hasAttachment = note.hasAttachment;
                row.thumbnailPath = note.thumbnailPath;
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
        return new EntryCardHolder(inflater.inflate(R.layout.rv_entries_item, parent, false), themeColor);
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        final Row row = rows.get(position);
        if (holder instanceof HeaderHolder) {
            ((HeaderHolder) holder).header.setText(row.header);
            return;
        }
        EntryCardHolder card = (EntryCardHolder) holder;
        //The year titles of the list of entries do not exist here
        card.header.setVisibility(View.GONE);
        card.time.setText(row.time);
        card.title.setText(row.title);
        boolean showSnippet = row.snippet.length() > 0;
        card.snippet.setVisibility(showSnippet ? View.VISIBLE : View.GONE);
        card.snippet.setText(row.snippet);
        //With the extra line the title only has room for one line
        card.title.setMaxLines(showSnippet ? 1 : 2);
        if (row.isNote) {
            card.status.setVisibility(View.VISIBLE);
            card.weather.setImageResource(DiaryInfoHelper.getWeatherResourceId(row.weather));
            card.mood.setImageResource(DiaryInfoHelper.getMoodResourceId(row.mood));
            card.attachment.setVisibility(row.hasAttachment ? View.VISIBLE : View.GONE);
        } else {
            //A topic has no weather, mood or attachment
            card.status.setVisibility(View.GONE);
            card.attachment.setVisibility(View.GONE);
        }
        card.bindThumbnail(row.thumbnailPath, thumbnailSizePx);
        card.card.setOnClickListener(new View.OnClickListener() {
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
}
