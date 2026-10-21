package com.kiminonawa.mydiary.entries.entries;

import android.net.Uri;
import androidx.annotation.ColorInt;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.facebook.drawee.backends.pipeline.Fresco;
import com.facebook.drawee.view.SimpleDraweeView;
import com.facebook.imagepipeline.common.ResizeOptions;
import com.facebook.imagepipeline.common.RotationOptions;
import com.facebook.imagepipeline.request.ImageRequest;
import com.facebook.imagepipeline.request.ImageRequestBuilder;
import com.kiminonawa.mydiary.R;
import com.kiminonawa.mydiary.entries.diary.DiaryInfoHelper;
import com.kiminonawa.mydiary.entries.diary.TagHelper;
import com.kiminonawa.mydiary.shared.EditMode;
import com.kiminonawa.mydiary.shared.FileManager;
import com.kiminonawa.mydiary.shared.ThemeManager;
import com.kiminonawa.mydiary.shared.TimeTools;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Locale;

/**
 * Created by daxia on 2016/10/17.
 */

public class EntriesAdapter extends RecyclerView.Adapter<EntriesAdapter.EntriesViewHolder> implements EditMode {


    private List<EntriesEntity> entriesList;
    private EntriesFragment mFragment;
    //Only the month and the day, like "Oct 21" or "21 oct" (the hour is not shown any more)
    private SimpleDateFormat dateFormat;
    private String[] monthsFullName;
    private ThemeManager themeManager;

    private boolean isEditMode = false;
    //The thumbnail is 56dp
    private int thumbnailSizePx;

    public EntriesAdapter(EntriesFragment fragment, List<EntriesEntity> topicList) {
        this.mFragment = fragment;
        this.entriesList = topicList;
        this.monthsFullName = TimeTools.getInstance(mFragment.getActivity().getApplicationContext()).getMonthsFullName();
        this.dateFormat = new SimpleDateFormat(android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "MMMd"),
                Locale.getDefault());
        this.themeManager = ThemeManager.getInstance();
        this.thumbnailSizePx = (int) (56 * mFragment.getResources().getDisplayMetrics().density);
    }


    @Override
    public EntriesViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.rv_entries_item, parent, false);
        return new EntriesViewHolder(view, themeManager.getThemeDarkColor(mFragment.getActivity()));
    }

    @Override
    public int getItemCount() {
        return entriesList.size();
    }

    @Override
    public void onBindViewHolder(EntriesViewHolder holder, final int position) {

        Calendar calendar = Calendar.getInstance();
        calendar.setTime(entriesList.get(position).getCreateDate());

        if (showHeader(position)) {
            holder.getHeader().setVisibility(View.VISIBLE);
            holder.getHeader().setText(monthsFullName[calendar.get(Calendar.MONTH)]);
        } else {
            holder.getHeader().setVisibility(View.GONE);
        }

        //Month and day, then the tags (without #)
        String tagsText = TagHelper.toDisplay(entriesList.get(position).getTags());
        holder.getTVTime().setText(dateFormat.format(calendar.getTime())
                + (tagsText.length() > 0 ? "   " + tagsText : ""));
        holder.getTVTitle().setText(entriesList.get(position).getTitle());

        holder.getIVWeather().setImageResource(DiaryInfoHelper.getWeatherResourceId(entriesList.get(position).getWeatherId()));
        holder.getIVMood().setImageResource(DiaryInfoHelper.getMoodResourceId(entriesList.get(position).getMoodId()));

        //Small picture of the first photo
        String thumbnailPath = entriesList.get(position).getThumbnailPath();
        if (thumbnailPath != null) {
            holder.getSDVThumbnail().setVisibility(View.VISIBLE);
            ImageRequest request = ImageRequestBuilder
                    .newBuilderWithSource(Uri.parse(FileManager.FILE_HEADER + thumbnailPath))
                    .setResizeOptions(new ResizeOptions(thumbnailSizePx, thumbnailSizePx))
                    .setRotationOptions(RotationOptions.autoRotate())
                    .build();
            holder.getSDVThumbnail().setController(Fresco.newDraweeControllerBuilder()
                    .setOldController(holder.getSDVThumbnail().getController())
                    .setImageRequest(request)
                    .build());
        } else {
            holder.getSDVThumbnail().setController(null);
            holder.getSDVThumbnail().setVisibility(View.GONE);
        }

        if (entriesList.get(position).hasAttachment()) {
            holder.getIVAttachment().setVisibility(View.VISIBLE);
        } else {
            holder.getIVAttachment().setVisibility(View.GONE);
        }
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


    protected class EntriesViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener, View.OnLongClickListener {

        private TextView TV_entries_item_header;
        private TextView TV_entries_item_time, TV_entries_item_title;

        private ImageView IV_entries_item_thumbnail_placeholder;
        private ImageView IV_entries_item_weather, IV_entries_item_mood, IV_entries_item_bookmark, IV_entries_item_attachment;

        private RelativeLayout RL_entries_item_content;
        private SimpleDraweeView SDV_entries_item_thumbnail;

        protected EntriesViewHolder(View rootView, @ColorInt int color) {
            super(rootView);
            this.TV_entries_item_header = (TextView) rootView.findViewById(R.id.TV_entries_item_header);

            this.TV_entries_item_time = (TextView) rootView.findViewById(R.id.TV_entries_item_time);
            this.TV_entries_item_title = (TextView) rootView.findViewById(R.id.TV_entries_item_title);
            this.IV_entries_item_thumbnail_placeholder = (ImageView) rootView.findViewById(R.id.IV_entries_item_thumbnail_placeholder);

            this.IV_entries_item_weather = (ImageView) rootView.findViewById(R.id.IV_entries_item_weather);
            this.IV_entries_item_mood = (ImageView) rootView.findViewById(R.id.IV_entries_item_mood);
            this.IV_entries_item_bookmark = (ImageView) rootView.findViewById(R.id.IV_entries_item_bookmark);
            this.IV_entries_item_attachment = (ImageView) rootView.findViewById(R.id.IV_entries_item_attachment);

            this.RL_entries_item_content = (RelativeLayout) rootView.findViewById(R.id.RL_entries_item_content);
            this.SDV_entries_item_thumbnail = (SimpleDraweeView) rootView.findViewById(R.id.SDV_entries_item_thumbnail);
            this.itemView.setOnClickListener(this);
            this.itemView.setOnLongClickListener(this);

            initThemeColor(color);
        }

        private void initThemeColor(@ColorInt int color) {
            this.TV_entries_item_time.setTextColor(color);
            this.TV_entries_item_title.setTextColor(color);
            //Soft square in the color of the theme
            this.IV_entries_item_thumbnail_placeholder.setColorFilter(ColorUtils.setAlphaComponent(color, 40));

            this.IV_entries_item_weather.setColorFilter(color);
            this.IV_entries_item_mood.setColorFilter(color);
            this.IV_entries_item_bookmark.setColorFilter(color);
            this.IV_entries_item_attachment.setColorFilter(color);

        }

        public ImageView getIVWeather() {
            return IV_entries_item_weather;
        }

        public ImageView getIVMood() {
            return IV_entries_item_mood;
        }

        public ImageView getIVBookmark() {
            return IV_entries_item_bookmark;
        }

        public TextView getHeader() {
            return TV_entries_item_header;
        }

        public TextView getTVTime() {
            return TV_entries_item_time;
        }

        public TextView getTVTitle() {
            return TV_entries_item_title;
        }

        public ImageView getIVAttachment() {
            return IV_entries_item_attachment;
        }

        public SimpleDraweeView getSDVThumbnail() {
            return SDV_entries_item_thumbnail;
        }

        public RelativeLayout getRLContent() {
            return RL_entries_item_content;
        }

        @Override
        public void onClick(View v) {
            //single click to open diary
            DiaryViewerDialogFragment diaryViewerDialog =
                    DiaryViewerDialogFragment.newInstance(entriesList.get(getAdapterPosition()).getId(),
                            isEditMode);
            diaryViewerDialog.setTargetFragment(mFragment, 0);
            //Revert the icon
            if (isEditMode) {
                mFragment.setEditModeUI(isEditMode);
            }
            diaryViewerDialog.show(mFragment.getFragmentManager(), "diaryViewerDialog");
        }

        @Override
        public boolean onLongClick(View v) {
            //Long click is always going to edit diary
            DiaryViewerDialogFragment diaryViewerDialog =
                    DiaryViewerDialogFragment.newInstance(entriesList.get(getAdapterPosition()).getId(),
                            true);
            diaryViewerDialog.setTargetFragment(mFragment, 0);
            //Revert the icon
            if (isEditMode) {
                mFragment.setEditModeUI(isEditMode);
            }
            diaryViewerDialog.show(mFragment.getFragmentManager(), "diaryViewerDialog");
            return true;
        }
    }
}
