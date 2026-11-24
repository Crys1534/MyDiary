package com.kiminonawa.mydiary.main;

import android.content.Intent;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.h6ah4i.android.widget.advrecyclerview.draggable.DraggableItemAdapter;
import com.h6ah4i.android.widget.advrecyclerview.draggable.DraggableItemViewHolder;
import com.h6ah4i.android.widget.advrecyclerview.draggable.ItemDraggableRange;
import com.h6ah4i.android.widget.advrecyclerview.draggable.annotation.DraggableItemStateFlags;
import com.h6ah4i.android.widget.advrecyclerview.utils.AbstractDraggableItemViewHolder;
import com.kiminonawa.mydiary.R;
import com.kiminonawa.mydiary.contacts.ContactsActivity;
import com.kiminonawa.mydiary.db.DBManager;
import com.kiminonawa.mydiary.entries.DiaryActivity;
import com.kiminonawa.mydiary.main.topic.ITopic;
import com.kiminonawa.mydiary.memo.MemoActivity;
import com.kiminonawa.mydiary.shared.FileManager;
import com.kiminonawa.mydiary.entries.diary.area.AreaImage;
import com.kiminonawa.mydiary.shared.ViewTools;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by daxia on 2016/10/17.
 */

public class MainTopicAdapter extends RecyclerView.Adapter<MainTopicAdapter.TopicViewHolder> implements
        Filterable, DraggableItemAdapter<MainTopicAdapter.TopicViewHolder> {

    //The photo of the newest entry of each diary that has one (topic id -> path and area)
    private final java.util.Map<Long, Cover> covers = new java.util.HashMap<>();
    private android.graphics.Typeface handwriting;
    private final int[] tilts = {-4, 3, -2, 4, -3, 2};

    public static class Cover {
        public final String path;
        public final android.graphics.RectF area;

        public Cover(String path, android.graphics.RectF area) {
            this.path = path;
            this.area = area;
        }
    }

    public void setCovers(java.util.Map<Long, Cover> newCovers) {
        covers.clear();
        covers.putAll(newCovers);
        super.notifyDataSetChanged();
    }


    private List<ITopic> originalTopicList;
    private List<ITopic> filteredTopicList;
    private MainActivity activity;
    private TopicFilter topicFilter;
    private DBManager dbManager;

    public MainTopicAdapter(MainActivity activity, List<ITopic> topicList, DBManager dbManager) {
        this.activity = activity;
        this.dbManager = dbManager;

        this.originalTopicList = topicList;
        this.filteredTopicList = new ArrayList<>();

        topicFilter = new TopicFilter(this, originalTopicList);

        // MainTopicAdapter requires stable ID, and also
        // have to implement the getItemId() method appropriately.
        setHasStableIds(true);
    }


    public void notifyDataSetChanged(boolean clear) {
        if (clear) {
            filteredTopicList.clear();
            filteredTopicList.addAll(originalTopicList);
        }
        super.notifyDataSetChanged();
    }

    public List<ITopic> getList() {
        return filteredTopicList;
    }

    @Override
    public TopicViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.rv_topic_item, parent, false);
        return new TopicViewHolder(view);
    }

    @Override
    public long getItemId(int position) {
        return filteredTopicList.get(position).getId();
    }

    @Override
    public int getItemCount() {
        return filteredTopicList.size();
    }

    @Override
    public void onBindViewHolder(final TopicViewHolder holder, final int position) {
        final ITopic topic = filteredTopicList.get(position);
        float density = activity.getResources().getDisplayMetrics().density;

        //The notebook: its color, the darker spine on the left
        int color = topic.getColor();
        android.graphics.drawable.GradientDrawable cover = new android.graphics.drawable.GradientDrawable();
        cover.setColor(color);
        float small = 6 * density, big = 12 * density;
        cover.setCornerRadii(new float[]{small, small, big, big, big, big, small, small});
        android.graphics.drawable.GradientDrawable spine = new android.graphics.drawable.GradientDrawable();
        spine.setColor(0x38000000);
        spine.setCornerRadii(new float[]{small, small, 0, 0, 0, 0, small, small});
        holder.notebook.setBackground(new NotebookDrawable(cover, spine, (int) (10 * density)));

        //The photo taped on the cover, a little crooked (each notebook a bit different)
        holder.photo.setRotation(tilts[(int) (Math.abs(topic.getId()) % tilts.length)]);
        Cover photo = covers.get(topic.getId());
        if (photo != null) {
            holder.coverImage.setVisibility(View.VISIBLE);
            holder.icon.setVisibility(View.GONE);
            AreaImage.show(holder.coverImage, android.net.Uri.parse(FileManager.FILE_HEADER + photo.path), photo.area,
                    (int) (80 * density), (int) (68 * density));
        } else {
            //Without a photo: the color of the notebook and its icon
            holder.coverImage.setController(null);
            holder.coverImage.setVisibility(View.INVISIBLE);
            holder.icon.setVisibility(View.VISIBLE);
            holder.icon.setImageResource(topic.getIcon());
            holder.icon.setColorFilter(color);
        }

        //The label (the emojis of the name are kept)
        android.graphics.drawable.GradientDrawable label = new android.graphics.drawable.GradientDrawable();
        label.setColor(0xFFFBF5E8);
        label.setCornerRadius(3 * density);
        holder.label.setBackground(label);
        if (handwriting == null) {
            try {
                handwriting = android.graphics.Typeface.createFromAsset(activity.getAssets(), "fonts/nb_caveat.ttf");
            } catch (RuntimeException e) {
                handwriting = android.graphics.Typeface.DEFAULT_BOLD;
            }
        }
        holder.title.setTypeface(handwriting);
        holder.title.setText(topic.getTitle());
        holder.title.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, topic.getTitle().length() > 16 ? 16 : 20);
        holder.count.setText(String.valueOf(topic.getCount()));
        holder.itemView.setContentDescription(topic.getTitle() + ", " + topic.getCount());

        //Click event
        holder.notebook.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int at = holder.getAdapterPosition();
                if (at != RecyclerView.NO_POSITION) {
                    gotoTopic(filteredTopicList.get(at).getType(), at);
                }
            }
        });
        holder.menu.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int at = holder.getAdapterPosition();
                if (at != RecyclerView.NO_POSITION) {
                    showTopicMenu(v, at);
                }
            }
        });
    }

    /**
     * The "..." of a notebook: edit it or delete it.
     */
    private void showTopicMenu(View anchor, final int position) {
        android.widget.PopupMenu menu = new android.widget.PopupMenu(activity, anchor);
        menu.getMenu().add(0, 1, 0, R.string.cover_tags_edit);
        menu.getMenu().add(0, 2, 1, R.string.dialog_button_delete);
        menu.setOnMenuItemClickListener(new android.widget.PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(android.view.MenuItem item) {
                ITopic topic = filteredTopicList.get(position);
                if (item.getItemId() == 1) {
                    TopicDetailDialogFragment createTopicDialogFragment =
                            TopicDetailDialogFragment.newInstance(true, position, topic.getId(),
                                    topic.getTitle(), topic.getType(), topic.getColor());
                    createTopicDialogFragment.show(activity.getSupportFragmentManager(),
                            "createTopicDialogFragment");
                } else {
                    TopicDeleteDialogFragment topicDeleteDialogFragment =
                            TopicDeleteDialogFragment.newInstance(position, topic.getTitle());
                    topicDeleteDialogFragment.show(activity.getSupportFragmentManager(), "topicDeleteDialogFragment");
                }
                return true;
            }
        });
        menu.show();
    }

    /**
     * The notebook with its spine: only the left part is darker.
     */
    private static class NotebookDrawable extends android.graphics.drawable.LayerDrawable {
        private final int spineWidth;

        NotebookDrawable(android.graphics.drawable.Drawable cover, android.graphics.drawable.Drawable spine,
                         int spineWidth) {
            super(new android.graphics.drawable.Drawable[]{cover, spine});
            this.spineWidth = spineWidth;
        }

        @Override
        protected void onBoundsChange(android.graphics.Rect bounds) {
            super.onBoundsChange(bounds);
            getDrawable(1).setBounds(bounds.left, bounds.top, bounds.left + spineWidth, bounds.bottom);
        }
    }

    public void gotoTopic(final int type, final int position) {
        switch (type) {
            case ITopic.TYPE_CONTACTS:
                Intent goContactsPageIntent = new Intent(activity, ContactsActivity.class);
                goContactsPageIntent.putExtra("topicId", filteredTopicList.get(position).getId());
                goContactsPageIntent.putExtra("diaryTitle", filteredTopicList.get(position).getTitle());
                activity.startActivity(goContactsPageIntent);
                break;
            case ITopic.TYPE_DIARY:
                Intent goEntriesPageIntent = new Intent(activity, DiaryActivity.class);
                goEntriesPageIntent.putExtra("topicId", filteredTopicList.get(position).getId());
                goEntriesPageIntent.putExtra("diaryTitle", filteredTopicList.get(position).getTitle());
                goEntriesPageIntent.putExtra("has_entries", true);
                activity.startActivity(goEntriesPageIntent);
                break;
            case ITopic.TYPE_MEMO:
                Intent goMemoPageIntent = new Intent(activity, MemoActivity.class);
                goMemoPageIntent.putExtra("topicId", filteredTopicList.get(position).getId());
                goMemoPageIntent.putExtra("diaryTitle", filteredTopicList.get(position).getTitle());
                activity.startActivity(goMemoPageIntent);
                break;
        }
    }


    @Override
    public Filter getFilter() {
        return topicFilter;
    }

    @Override
    public boolean onCheckCanStartDrag(TopicViewHolder holder, int position, int x, int y) {

        // x, y --- relative from the itemView's top-left
        //A notebook is moved by holding it (not while searching)
        return !topicFilter.isFilter();
    }

    @Override
    public ItemDraggableRange onGetItemDraggableRange(TopicViewHolder holder, int position) {
        return null;
    }

    @Override
    public void onMoveItem(int fromPosition, int toPosition) {
        if (fromPosition == toPosition) {
            return;
        }

        //modify the original list
        final ITopic originalItem = originalTopicList.remove(fromPosition);
        originalTopicList.add(toPosition, originalItem);

        //Modify the filter list
        final ITopic filteredItem = filteredTopicList.remove(fromPosition);
        filteredTopicList.add(toPosition, filteredItem);

        //save the new topic order
        int orderNumber = originalTopicList.size();
        dbManager.opeDB();
        dbManager.deleteAllCurrentTopicOrder();
        for (ITopic topic : originalTopicList) {
            dbManager.insertTopicOrder(topic.getId(), --orderNumber);
        }
        dbManager.closeDB();
        notifyDataSetChanged(false);
    }

    @Override
    public boolean onCheckCanDrop(int draggingPosition, int dropPosition) {
        return true;
    }

    protected class TopicViewHolder extends AbstractDraggableItemViewHolder {

        final View notebook, photo, label, menu;
        final com.facebook.drawee.view.SimpleDraweeView coverImage;
        final ImageView icon;
        final TextView title, count;

        protected TopicViewHolder(View rootView) {
            super(rootView);
            notebook = rootView.findViewById(R.id.FL_topic_notebook);
            photo = rootView.findViewById(R.id.FL_topic_photo);
            label = rootView.findViewById(R.id.LL_topic_label);
            menu = rootView.findViewById(R.id.IV_topic_menu);
            coverImage = (com.facebook.drawee.view.SimpleDraweeView) rootView.findViewById(R.id.IV_topic_cover);
            icon = (ImageView) rootView.findViewById(R.id.IV_topic_icon);
            title = (TextView) rootView.findViewById(R.id.TV_topic_title);
            count = (TextView) rootView.findViewById(R.id.TV_topic_count);
        }
    }

    private static class TopicFilter extends Filter {

        private final MainTopicAdapter adapter;

        private final List<ITopic> originalList;

        private final List<ITopic> filteredList;

        private boolean isFilter = false;

        public boolean isFilter() {
            return isFilter;
        }

        public void setFilter(boolean filter) {
            isFilter = filter;
        }

        private TopicFilter(MainTopicAdapter adapter, List<ITopic> originalList) {
            super();
            this.adapter = adapter;
            this.originalList = originalList;
            this.filteredList = new ArrayList<>();
        }

        @Override
        protected FilterResults performFiltering(CharSequence constraint) {
            filteredList.clear();
            final FilterResults results = new FilterResults();

            if (constraint.length() == 0) {
                filteredList.addAll(originalList);
                isFilter = false;
            } else {
                final String filterPattern = constraint.toString().toLowerCase().trim();
                for (final ITopic topic : originalList) {
                    if (topic.getTitle().toLowerCase().contains(filterPattern)) {
                        filteredList.add(topic);
                    }
                }
                isFilter = true;
            }
            results.values = filteredList;
            results.count = filteredList.size();
            return results;
        }

        @Override
        protected void publishResults(CharSequence constraint, FilterResults results) {
            adapter.filteredTopicList.clear();
            adapter.filteredTopicList.addAll((ArrayList<ITopic>) results.values);
            adapter.notifyDataSetChanged(false);
        }
    }
}
