package com.kiminonawa.mydiary.main;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.facebook.drawee.view.SimpleDraweeView;
import com.kiminonawa.mydiary.R;
import com.kiminonawa.mydiary.contacts.ContactsActivity;
import com.kiminonawa.mydiary.db.DBManager;
import com.kiminonawa.mydiary.entries.DiaryActivity;
import com.kiminonawa.mydiary.entries.diary.area.AreaImage;
import com.kiminonawa.mydiary.main.topic.Contacts;
import com.kiminonawa.mydiary.main.topic.Diary;
import com.kiminonawa.mydiary.main.topic.ITopic;
import com.kiminonawa.mydiary.main.topic.Memo;
import com.kiminonawa.mydiary.memo.MemoActivity;
import com.kiminonawa.mydiary.shared.BackgroundTask;
import com.kiminonawa.mydiary.shared.FileManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Inside a category: a row of small notebooks with the categories that are inside it (tap one to open it).
 */
public final class SubTopicRow {

    private SubTopicRow() {
    }

    /**
     * Fills the row (it stays hidden when nothing is inside this category).
     */
    public static void show(final Activity activity, final RecyclerView row, long topicId) {
        final List<ITopic> inside = read(activity, topicId);
        if (inside.isEmpty()) {
            row.setVisibility(View.GONE);
            row.setTag(R.id.RV_diary_subtopics, Boolean.FALSE);
            return;
        }
        row.setTag(R.id.RV_diary_subtopics, Boolean.TRUE);
        if (row.getLayoutManager() == null) {
            row.setLayoutManager(new LinearLayoutManager(activity, LinearLayoutManager.HORIZONTAL, false));
        }
        final RowAdapter adapter = new RowAdapter(activity, inside);
        row.setAdapter(adapter);
        row.setVisibility(View.VISIBLE);
        //Their photos, in the background
        final List<long[]> topics = new ArrayList<>();
        for (ITopic topic : inside) {
            topics.add(new long[]{topic.getId(), topic.getType()});
        }
        final Context context = activity.getApplicationContext();
        new BackgroundTask<Void, Map<Long, MainTopicAdapter.Cover>>() {
            @Override
            protected Map<Long, MainTopicAdapter.Cover> doInBackground(Void... params) {
                return TopicCovers.read(context, topics);
            }

            @Override
            protected void onPostExecute(Map<Long, MainTopicAdapter.Cover> covers) {
                if (!activity.isFinishing() && row.getAdapter() == adapter) {
                    adapter.covers.putAll(covers);
                    adapter.notifyDataSetChanged();
                }
            }
        }.execute();
    }

    /**
     * Shows the row only in some tabs (and only when something is inside).
     */
    public static void setShown(RecyclerView row, boolean shown) {
        boolean hasInside = Boolean.TRUE.equals(row.getTag(R.id.RV_diary_subtopics));
        row.setVisibility(shown && hasInside ? View.VISIBLE : View.GONE);
    }

    private static List<ITopic> read(Context context, long topicId) {
        List<ITopic> inside = new ArrayList<>();
        DBManager dbManager = new DBManager(context);
        dbManager.opeDB();
        try {
            Map<Long, Long> parents = dbManager.getTopicParents();
            //Only one level: a category that is itself inside another one shows no row
            if (parents.containsKey(topicId) || !parents.containsValue(topicId)) {
                return inside;
            }
            Cursor c = dbManager.selectTopic();
            try {
                for (int i = 0; i < c.getCount(); i++) {
                    c.moveToPosition(i);
                    long id = c.getLong(0);
                    Long parent = parents.get(id);
                    if (parent == null || parent != topicId) {
                        continue;
                    }
                    ITopic topic;
                    switch (c.getInt(2)) {
                        case ITopic.TYPE_CONTACTS:
                            topic = new Contacts(id, c.getString(1), c.getInt(5));
                            topic.setCount(dbManager.getContactsCountByTopicId(id));
                            break;
                        case ITopic.TYPE_MEMO:
                            topic = new Memo(id, c.getString(1), c.getInt(5));
                            topic.setCount(dbManager.getMemoCountByTopicId(id));
                            break;
                        default:
                            topic = new Diary(id, c.getString(1), c.getInt(5));
                            topic.setCount(dbManager.getDiaryCountByTopicId(id));
                            break;
                    }
                    inside.add(topic);
                }
            } finally {
                c.close();
            }
        } catch (RuntimeException e) {
            //Without the row the category still works
        } finally {
            dbManager.closeDB();
        }
        return inside;
    }

    private static void open(Activity activity, ITopic topic) {
        Class<?> screen;
        switch (topic.getType()) {
            case ITopic.TYPE_CONTACTS:
                screen = ContactsActivity.class;
                break;
            case ITopic.TYPE_MEMO:
                screen = MemoActivity.class;
                break;
            default:
                screen = DiaryActivity.class;
                break;
        }
        Intent intent = new Intent(activity, screen);
        intent.putExtra("topicId", topic.getId());
        intent.putExtra("diaryTitle", topic.getTitle());
        intent.putExtra("has_entries", true);
        activity.startActivity(intent);
    }

    private static class RowAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private final Activity activity;
        private final List<ITopic> inside;
        final Map<Long, MainTopicAdapter.Cover> covers = new HashMap<>();
        private final int[] tilts = {-4, 3, -2, 4, -3, 2};
        private android.graphics.Typeface handwriting;

        RowAdapter(Activity activity, List<ITopic> inside) {
            this.activity = activity;
            this.inside = inside;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.rv_subtopic_item, parent, false);
            return new RecyclerView.ViewHolder(view) {
            };
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            final ITopic topic = inside.get(position);
            View root = holder.itemView;
            float density = activity.getResources().getDisplayMetrics().density;
            View notebook = root.findViewById(R.id.FL_topic_notebook);
            notebook.setBackground(NotebookDrawable.of(topic.getColor(), density, 0.7f));
            root.findViewById(R.id.FL_topic_photo).setRotation(tilts[(int) (Math.abs(topic.getId()) % tilts.length)]);
            SimpleDraweeView cover = (SimpleDraweeView) root.findViewById(R.id.IV_topic_cover);
            ImageView icon = (ImageView) root.findViewById(R.id.IV_topic_icon);
            MainTopicAdapter.Cover photo = covers.get(topic.getId());
            if (photo != null) {
                cover.setVisibility(View.VISIBLE);
                icon.setVisibility(View.GONE);
                AreaImage.show(cover, Uri.parse(FileManager.FILE_HEADER + photo.path), photo.area,
                        (int) (50 * density), (int) (43 * density));
            } else {
                cover.setController(null);
                cover.setVisibility(View.INVISIBLE);
                icon.setVisibility(View.VISIBLE);
                icon.setImageResource(topic.getIcon());
                icon.setColorFilter(topic.getColor());
            }
            android.graphics.drawable.GradientDrawable label = new android.graphics.drawable.GradientDrawable();
            label.setColor(0xFFFBF5E8);
            label.setCornerRadius(3 * density);
            root.findViewById(R.id.LL_topic_label).setBackground(label);
            if (handwriting == null) {
                try {
                    handwriting = android.graphics.Typeface.createFromAsset(activity.getAssets(), "fonts/nb_caveat.ttf");
                } catch (RuntimeException e) {
                    handwriting = android.graphics.Typeface.DEFAULT_BOLD;
                }
            }
            TextView title = (TextView) root.findViewById(R.id.TV_topic_title);
            title.setTypeface(handwriting);
            title.setText(topic.getTitle());
            ((TextView) root.findViewById(R.id.TV_topic_count)).setText(String.valueOf(topic.getCount()));
            root.setContentDescription(topic.getTitle() + ", " + topic.getCount());
            notebook.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    open(activity, topic);
                }
            });
        }

        @Override
        public int getItemCount() {
            return inside.size();
        }
    }
}
