package com.kiminonawa.mydiary.main;

import android.net.Uri;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.RecyclerView;

import com.facebook.drawee.backends.pipeline.Fresco;
import com.facebook.drawee.view.SimpleDraweeView;
import com.facebook.imagepipeline.common.ResizeOptions;
import com.facebook.imagepipeline.common.RotationOptions;
import com.facebook.imagepipeline.request.ImageRequest;
import com.facebook.imagepipeline.request.ImageRequestBuilder;
import com.kiminonawa.mydiary.R;
import com.kiminonawa.mydiary.shared.FileManager;

/**
 * One card of an entry, the same one that the list of entries shows (rv_entries_item): the small picture, the
 * date with the tags, the title and the weather / mood / attachment icons.
 * "A day like today" and the results of the search draw their entries with it.
 */
public class EntryCardHolder extends RecyclerView.ViewHolder {

    public final TextView header, time, title, snippet;
    public final ImageView weather, mood, bookmark, attachment;
    public final SimpleDraweeView thumbnail;
    public final View card, status;

    public EntryCardHolder(View rootView, int themeColor) {
        super(rootView);
        header = (TextView) rootView.findViewById(R.id.TV_entries_item_header);
        time = (TextView) rootView.findViewById(R.id.TV_entries_item_time);
        title = (TextView) rootView.findViewById(R.id.TV_entries_item_title);
        snippet = (TextView) rootView.findViewById(R.id.TV_entries_item_snippet);
        weather = (ImageView) rootView.findViewById(R.id.IV_entries_item_weather);
        mood = (ImageView) rootView.findViewById(R.id.IV_entries_item_mood);
        bookmark = (ImageView) rootView.findViewById(R.id.IV_entries_item_bookmark);
        attachment = (ImageView) rootView.findViewById(R.id.IV_entries_item_attachment);
        thumbnail = (SimpleDraweeView) rootView.findViewById(R.id.SDV_entries_item_thumbnail);
        card = rootView.findViewById(R.id.RL_entries_item_content);
        status = rootView.findViewById(R.id.LL_entries_item_status);

        //The colors of the theme, like in the list of entries
        time.setTextColor(themeColor);
        title.setTextColor(themeColor);
        snippet.setTextColor(ColorUtils.setAlphaComponent(themeColor, 170));
        ((ImageView) rootView.findViewById(R.id.IV_entries_item_thumbnail_placeholder))
                .setColorFilter(ColorUtils.setAlphaComponent(themeColor, 40));
        weather.setColorFilter(themeColor);
        mood.setColorFilter(themeColor);
        bookmark.setColorFilter(themeColor);
        attachment.setColorFilter(themeColor);
    }

    /**
     * The small picture of the card; a soft square when the entry has no photo.
     */
    public void bindThumbnail(String thumbnailPath, int sizePx) {
        if (thumbnailPath != null) {
            thumbnail.setVisibility(View.VISIBLE);
            ImageRequest request = ImageRequestBuilder
                    .newBuilderWithSource(Uri.parse(FileManager.FILE_HEADER + thumbnailPath))
                    .setResizeOptions(new ResizeOptions(sizePx, sizePx))
                    .setRotationOptions(RotationOptions.autoRotate())
                    .build();
            thumbnail.setController(Fresco.newDraweeControllerBuilder()
                    .setOldController(thumbnail.getController())
                    .setImageRequest(request)
                    .build());
        } else {
            thumbnail.setController(null);
            thumbnail.setVisibility(View.GONE);
        }
    }
}
