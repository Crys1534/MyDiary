package com.kiminonawa.mydiary.entries.diary;

import androidx.appcompat.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.text.format.DateFormat;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.facebook.drawee.backends.pipeline.Fresco;
import com.facebook.drawee.generic.RoundingParams;
import com.facebook.drawee.view.SimpleDraweeView;
import com.facebook.imagepipeline.common.ResizeOptions;
import com.facebook.imagepipeline.common.RotationOptions;
import com.facebook.imagepipeline.request.ImageRequest;
import com.facebook.imagepipeline.request.ImageRequestBuilder;
import com.kiminonawa.mydiary.R;
import com.kiminonawa.mydiary.shared.ScreenHelper;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * The cover of a diary (the colored part at the top of the page).
 * <p>
 * The photo chosen as the thumbnail is the background. Over it: the date (month and day) and the
 * title in the middle, the weather and the place in the top corners, and the tags in the bottom
 * right corner (only one tag; a "+" button when there are more, a pencil when there are none).
 */
public class DiaryCover implements View.OnClickListener {

    public interface Listener {
        /**
         * The pencil (no tags yet) or the "Edit" button of the tags list was pressed.
         */
        void onEditTagsRequested();
    }

    private final Context context;
    private final SimpleDraweeView SDV_cover;
    private final View V_overlay;
    private final TextView TV_date, TV_title, TV_tag, TV_tagMore;
    private final ImageView IV_tagAdd;
    private final Listener listener;

    private List<String> tags = TagHelper.toList("");
    private String shownCover = null;

    public DiaryCover(View rootView, Listener listener) {
        this.context = rootView.getContext();
        this.listener = listener;
        SDV_cover = (SimpleDraweeView) rootView.findViewById(R.id.SDV_diary_cover);
        V_overlay = rootView.findViewById(R.id.V_diary_cover_overlay);
        TV_date = (TextView) rootView.findViewById(R.id.TV_diary_date);
        TV_title = (TextView) rootView.findViewById(R.id.TV_diary_cover_title);
        TV_tag = (TextView) rootView.findViewById(R.id.TV_diary_cover_tag);
        TV_tagMore = (TextView) rootView.findViewById(R.id.TV_diary_cover_tag_more);
        IV_tagAdd = (ImageView) rootView.findViewById(R.id.IV_diary_cover_tag_add);
        TV_tag.setOnClickListener(this);
        TV_tagMore.setOnClickListener(this);
        IV_tagAdd.setOnClickListener(this);
    }

    /**
     * For the window that shows a diary: its two top corners are round, so the photo has to be round too.
     */
    public void setTopCornerRadius(int radiusPx) {
        SDV_cover.getHierarchy().setRoundingParams(
                RoundingParams.fromCornersRadii(radiusPx, radiusPx, 0, 0));
        GradientDrawable veil = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x55000000, 0x99000000});
        veil.setCornerRadii(new float[]{radiusPx, radiusPx, radiusPx, radiusPx, 0, 0, 0, 0});
        V_overlay.setBackground(veil);
    }

    /**
     * "October 21" / "21 de octubre": only the month and the day (no hour, no year).
     */
    public static String formatDate(Calendar calendar) {
        String pattern = DateFormat.getBestDateTimePattern(Locale.getDefault(), "MMMMd");
        return new SimpleDateFormat(pattern, Locale.getDefault()).format(calendar.getTime());
    }

    public void setDate(Calendar calendar) {
        TV_date.setText(formatDate(calendar));
    }

    public void setTitle(String title) {
        TV_title.setText(title == null ? "" : title.trim());
        TV_title.setVisibility(TV_title.getText().length() > 0 ? View.VISIBLE : View.GONE);
    }

    /**
     * @param savedTags tags like ",school,trip," (what the database has)
     */
    public void setTags(String savedTags) {
        tags = TagHelper.toList(savedTags);
        if (tags.isEmpty()) {
            TV_tag.setVisibility(View.GONE);
            TV_tagMore.setVisibility(View.GONE);
            IV_tagAdd.setVisibility(View.VISIBLE);
        } else {
            //Only the first tag is shown
            TV_tag.setText(tags.get(0));
            TV_tag.setVisibility(View.VISIBLE);
            TV_tagMore.setVisibility(tags.size() > 1 ? View.VISIBLE : View.GONE);
            IV_tagAdd.setVisibility(View.GONE);
        }
    }

    /**
     * @param path complete path of the photo file, or null when there is no photo (plain color)
     */
    public void setCover(String path) {
        if (path == null || path.length() == 0) {
            shownCover = null;
            SDV_cover.setController(null);
            SDV_cover.setVisibility(View.GONE);
            V_overlay.setVisibility(View.GONE);
            return;
        }
        if (path.equals(shownCover)) {
            return;
        }
        shownCover = path;
        Uri uri = path.startsWith("file:") || path.startsWith("content:") ? Uri.parse(path) : Uri.fromFile(new java.io.File(path));
        ImageRequest request = ImageRequestBuilder.newBuilderWithSource(uri)
                .setResizeOptions(new ResizeOptions(ScreenHelper.getScreenWidth(context),
                        ScreenHelper.dpToPixel(context.getResources(), 200)))
                .setRotationOptions(RotationOptions.autoRotate())
                .build();
        SDV_cover.setController(Fresco.newDraweeControllerBuilder()
                .setOldController(SDV_cover.getController())
                .setImageRequest(request)
                .build());
        SDV_cover.setVisibility(View.VISIBLE);
        V_overlay.setVisibility(View.VISIBLE);
    }

    @Override
    public void onClick(View v) {
        if (v == IV_tagAdd) {
            if (listener != null) {
                listener.onEditTagsRequested();
            }
        } else {
            showAllTags();
        }
    }

    private void showAllTags() {
        if (tags.isEmpty()) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(context)
                .setTitle(R.string.cover_tags_title)
                .setItems(tags.toArray(new String[0]), null)
                .setPositiveButton(R.string.dialog_button_ok, null);
        if (listener != null) {
            builder.setNeutralButton(R.string.cover_tags_edit, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    listener.onEditTagsRequested();
                }
            });
        }
        builder.show();
    }
}
