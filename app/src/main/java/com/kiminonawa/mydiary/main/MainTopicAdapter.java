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
import com.kiminonawa.mydiary.shared.TopicArt;
import com.kiminonawa.mydiary.entries.diary.area.AreaImage;
import com.kiminonawa.mydiary.shared.ViewTools;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by daxia on 2016/10/17.
 */

public class MainTopicAdapter extends RecyclerView.Adapter<MainTopicAdapter.TopicViewHolder> implements
        Filterable, DraggableItemAdapter<MainTopicAdapter.TopicViewHolder> {

    private static final int VIEW_NOTEBOOK = 0;
    //A category with categories inside: its notebook and a row with the ones inside
    private static final int VIEW_FOLDER = 1;

    //The categories that are inside another one: topic id -> the one they are inside of
    private final java.util.Map<Long, Long> parents = new java.util.HashMap<>();
    //What is inside each category of the main screen (in the order of the list)
    private final java.util.Map<Long, List<ITopic>> children = new java.util.HashMap<>();

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

    public void setParents(java.util.Map<Long, Long> newParents) {
        parents.clear();
        parents.putAll(newParents);
    }

    /**
     * The category this one is inside of, or null when it is on the main screen.
     */
    public ITopic parentOf(ITopic topic) {
        Long parentId = parents.get(topic.getId());
        if (parentId == null) {
            return null;
        }
        for (ITopic candidate : originalTopicList) {
            //Only one level: a category inside one that is itself inside another goes to the main screen
            if (candidate.getId() == parentId && candidate.getId() != topic.getId() && !parents.containsKey(candidate.getId())) {
                return candidate;
            }
        }
        return null;
    }

    public List<ITopic> childrenOf(ITopic topic) {
        List<ITopic> list = children.get(topic.getId());
        return list == null ? new ArrayList<ITopic>() : list;
    }

    /**
     * The categories of the main screen and what is inside each one, again.
     */
    private void rebuild() {
        children.clear();
        filteredTopicList.clear();
        for (ITopic topic : originalTopicList) {
            ITopic parent = parentOf(topic);
            if (parent == null) {
                filteredTopicList.add(topic);
            } else {
                List<ITopic> list = children.get(parent.getId());
                if (list == null) {
                    list = new ArrayList<>();
                    children.put(parent.getId(), list);
                }
                list.add(topic);
            }
        }
    }

    public void notifyDataSetChanged(boolean clear) {
        if (clear) {
            rebuild();
        }
        super.notifyDataSetChanged();
    }

    /**
     * All the categories (the ones inside others too): the positions of the dialogs of edit and delete are of this list.
     */
    public List<ITopic> getList() {
        return originalTopicList;
    }

    /**
     * A category with others inside takes the two columns.
     */
    public int getSpanSize(int viewType) {
        return viewType == VIEW_FOLDER ? 2 : 1;
    }

    @Override
    public int getItemViewType(int position) {
        if (topicFilter.isFilter()) {
            return VIEW_NOTEBOOK;
        }
        return childrenOf(filteredTopicList.get(position)).isEmpty() ? VIEW_NOTEBOOK : VIEW_FOLDER;
    }

    @Override
    public TopicViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(viewType == VIEW_FOLDER ? R.layout.rv_topic_folder : R.layout.rv_topic_item, parent, false);
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
        bindNotebook(holder.notebookViews, topic);
        if (holder.childrenRow != null) {
            List<ITopic> inside = childrenOf(topic);
            holder.insideTitle.setText(activity.getString(R.string.topic_folder_inside, inside.size()));
            boolean dark = (activity.getResources().getConfiguration().uiMode
                    & android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES;
            holder.insideTitle.setTextColor(dark ? 0xFFD8CCB8 : 0xFF6B5240);
            android.graphics.drawable.GradientDrawable shelf = new android.graphics.drawable.GradientDrawable();
            shelf.setColor(dark ? 0x14FFFFFF : 0x14000000);
            shelf.setCornerRadius(12 * activity.getResources().getDisplayMetrics().density);
            holder.shelf.setBackground(shelf);
            holder.childrenRow.setAdapter(new ChildrenAdapter(inside));
        }
        //Click event
        holder.notebookViews.notebook.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int at = holder.getAdapterPosition();
                if (at != RecyclerView.NO_POSITION) {
                    gotoTopic(filteredTopicList.get(at));
                }
            }
        });
    }

    /**
     * One notebook: its color, the photo taped on the cover, the label with the name and the "..." menu.
     */
    private void bindNotebook(final NotebookViews holder, final ITopic topic) {
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
        holder.title.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, topic.getTitle().length() > 16 ? 14 : 17);
        holder.count.setText(String.valueOf(topic.getCount()));
        holder.root.setContentDescription(topic.getTitle() + ", " + topic.getCount());

        holder.menu.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showTopicMenu(v, topic);
            }
        });
    }

    /**
     * The row with the categories inside another one.
     */
    private class ChildrenAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private final List<ITopic> inside;

        ChildrenAdapter(List<ITopic> inside) {
            this.inside = inside;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.rv_topic_item, parent, false);
            //A bit less than half of the screen: the next one peeks, so it can be seen that the row moves
            ViewGroup.LayoutParams params = view.getLayoutParams();
            params.width = (int) (activity.getResources().getDisplayMetrics().widthPixels * 0.44f);
            view.setLayoutParams(params);
            final NotebookViews views = new NotebookViews(view);
            RecyclerView.ViewHolder holder = new RecyclerView.ViewHolder(view) {
            };
            view.setTag(views);
            return holder;
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            final ITopic topic = inside.get(position);
            NotebookViews views = (NotebookViews) holder.itemView.getTag();
            bindNotebook(views, topic);
            views.notebook.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    gotoTopic(topic);
                }
            });
        }

        @Override
        public int getItemCount() {
            return inside.size();
        }
    }

    /**
     * The "..." of a notebook: edit, delete, put it inside another category, its photo and its banner.
     */
    private void showTopicMenu(View anchor, final ITopic topic) {
        final int position = originalTopicList.indexOf(topic);
        if (position < 0) {
            return;
        }
        final boolean inside = parentOf(topic) != null;
        final boolean hasCover = TopicArt.has(activity, topic.getType(), topic.getId(), TopicArt.COVER);
        final boolean hasBanner = TopicArt.has(activity, topic.getType(), topic.getId(), TopicArt.BANNER);
        final boolean canHaveBanner = topic.getType() != ITopic.TYPE_CONTACTS;
        android.widget.PopupMenu menu = new android.widget.PopupMenu(activity, anchor);
        menu.getMenu().add(0, 1, 0, R.string.cover_tags_edit);
        menu.getMenu().add(0, 3, 1, inside ? R.string.topic_move_other_folder : R.string.topic_move_into);
        if (inside) {
            menu.getMenu().add(0, 4, 2, R.string.topic_move_out);
        }
        menu.getMenu().add(0, 5, 3, R.string.topic_cover_choose);
        if (hasCover) {
            menu.getMenu().add(0, 6, 4, R.string.topic_cover_remove);
        }
        if (canHaveBanner) {
            menu.getMenu().add(0, 7, 5, R.string.topic_banner_choose);
            if (hasBanner) {
                menu.getMenu().add(0, 8, 6, R.string.topic_banner_remove);
            }
        }
        menu.getMenu().add(0, 2, 7, R.string.dialog_button_delete);
        menu.setOnMenuItemClickListener(new android.widget.PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(android.view.MenuItem item) {
                switch (item.getItemId()) {
                    case 1:
                        TopicDetailDialogFragment createTopicDialogFragment =
                                TopicDetailDialogFragment.newInstance(true, position, topic.getId(),
                                        topic.getTitle(), topic.getType(), topic.getColor());
                        createTopicDialogFragment.show(activity.getSupportFragmentManager(),
                                "createTopicDialogFragment");
                        break;
                    case 2:
                        TopicDeleteDialogFragment topicDeleteDialogFragment =
                                TopicDeleteDialogFragment.newInstance(position, topic.getTitle());
                        topicDeleteDialogFragment.show(activity.getSupportFragmentManager(), "topicDeleteDialogFragment");
                        break;
                    case 3:
                        chooseFolder(topic);
                        break;
                    case 4:
                        setParent(topic, 0);
                        break;
                    case 5:
                        activity.chooseTopicArt(topic, TopicArt.COVER);
                        break;
                    case 6:
                        activity.removeTopicArt(topic, TopicArt.COVER);
                        break;
                    case 7:
                        activity.chooseTopicArt(topic, TopicArt.BANNER);
                        break;
                    case 8:
                        activity.removeTopicArt(topic, TopicArt.BANNER);
                        break;
                }
                return true;
            }
        });
        menu.show();
    }

    /**
     * Put a category inside another one of the main screen.
     */
    private void chooseFolder(final ITopic topic) {
        if (!childrenOf(topic).isEmpty()) {
            //Only one level of folders
            android.widget.Toast.makeText(activity, R.string.topic_move_has_children, android.widget.Toast.LENGTH_LONG).show();
            return;
        }
        final List<ITopic> folders = new ArrayList<>();
        ITopic current = parentOf(topic);
        for (ITopic candidate : originalTopicList) {
            if (candidate.getId() != topic.getId() && parentOf(candidate) == null && candidate != current) {
                folders.add(candidate);
            }
        }
        if (folders.isEmpty()) {
            android.widget.Toast.makeText(activity, R.string.topic_move_no_other, android.widget.Toast.LENGTH_LONG).show();
            return;
        }
        String[] names = new String[folders.size()];
        for (int i = 0; i < names.length; i++) {
            names[i] = folders.get(i).getTitle();
        }
        new androidx.appcompat.app.AlertDialog.Builder(activity)
                .setTitle(activity.getString(R.string.topic_move_into_title, topic.getTitle()))
                .setItems(names, new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        setParent(topic, folders.get(which).getId());
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void setParent(ITopic topic, long parentId) {
        dbManager.opeDB();
        dbManager.setTopicParent(topic.getId(), parentId);
        dbManager.closeDB();
        if (parentId > 0) {
            parents.put(topic.getId(), parentId);
        } else {
            parents.remove(topic.getId());
        }
        notifyDataSetChanged(true);
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

    public void gotoTopic(ITopic topic) {
        switch (topic.getType()) {
            case ITopic.TYPE_CONTACTS:
                Intent goContactsPageIntent = new Intent(activity, ContactsActivity.class);
                goContactsPageIntent.putExtra("topicId", topic.getId());
                goContactsPageIntent.putExtra("diaryTitle", topic.getTitle());
                activity.startActivity(goContactsPageIntent);
                break;
            case ITopic.TYPE_DIARY:
                Intent goEntriesPageIntent = new Intent(activity, DiaryActivity.class);
                goEntriesPageIntent.putExtra("topicId", topic.getId());
                goEntriesPageIntent.putExtra("diaryTitle", topic.getTitle());
                goEntriesPageIntent.putExtra("has_entries", true);
                activity.startActivity(goEntriesPageIntent);
                break;
            case ITopic.TYPE_MEMO:
                Intent goMemoPageIntent = new Intent(activity, MemoActivity.class);
                goMemoPageIntent.putExtra("topicId", topic.getId());
                goMemoPageIntent.putExtra("diaryTitle", topic.getTitle());
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
        //The categories of the main screen in their new order
        final ITopic moved = filteredTopicList.remove(fromPosition);
        filteredTopicList.add(toPosition, moved);
        //All the list: each one followed by the ones inside it
        List<ITopic> ordered = new ArrayList<>();
        for (ITopic topic : filteredTopicList) {
            ordered.add(topic);
            ordered.addAll(childrenOf(topic));
        }
        for (ITopic topic : originalTopicList) {
            if (!ordered.contains(topic)) {
                ordered.add(topic);
            }
        }
        originalTopicList.clear();
        originalTopicList.addAll(ordered);

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

    /**
     * The views of one notebook.
     */
    private static class NotebookViews {
        final View root, notebook, photo, label, menu;
        final com.facebook.drawee.view.SimpleDraweeView coverImage;
        final ImageView icon;
        final TextView title, count;

        NotebookViews(View rootView) {
            root = rootView;
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

    protected class TopicViewHolder extends AbstractDraggableItemViewHolder {

        final NotebookViews notebookViews;
        //Only for a category with others inside
        final RecyclerView childrenRow;
        final TextView insideTitle;
        final View shelf;

        protected TopicViewHolder(View rootView) {
            super(rootView);
            View notebookRoot = rootView.findViewById(R.id.IN_topic_folder_notebook);
            notebookViews = new NotebookViews(notebookRoot != null ? notebookRoot : rootView);
            childrenRow = (RecyclerView) rootView.findViewById(R.id.RV_topic_folder_children);
            insideTitle = (TextView) rootView.findViewById(R.id.TV_topic_folder_inside);
            shelf = rootView.findViewById(R.id.LL_topic_folder_shelf);
            if (childrenRow != null) {
                childrenRow.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(
                        rootView.getContext(), androidx.recyclerview.widget.LinearLayoutManager.HORIZONTAL, false));
                childrenRow.setNestedScrollingEnabled(false);
            }
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
            if (isFilter) {
                //Searching: every category that matches, the ones inside others too
                adapter.filteredTopicList.clear();
                adapter.filteredTopicList.addAll((ArrayList<ITopic>) results.values);
                adapter.notifyDataSetChanged(false);
            } else {
                adapter.notifyDataSetChanged(true);
            }
        }
    }
}
