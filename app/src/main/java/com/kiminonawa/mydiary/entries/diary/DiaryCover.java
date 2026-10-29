package com.kiminonawa.mydiary.entries.diary;

import androidx.appcompat.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.facebook.drawee.generic.RoundingParams;
import com.facebook.drawee.view.SimpleDraweeView;
import com.kiminonawa.mydiary.R;
import com.kiminonawa.mydiary.entries.diary.area.AreaImage;
import com.kiminonawa.mydiary.shared.ScreenHelper;
import com.kiminonawa.mydiary.shared.gui.AspectRatioRelativeLayout;

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
    private final ImageView IV_tagAdd, IV_crop, IV_coverPhoto;
    private final ImageView IV_weather;
    private final TextView TV_weather;
    private final Listener listener;
    private Runnable pickAreaListener = null;
    //Editing: the pencil of the tags is always there, and the person can choose what part of the photo is seen
    private boolean editMode = false;

    private String savedTags = "";
    private List<String> tags = TagHelper.toList("");
    private String shownCover = null;
    private RectF shownCoverArea = null;

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
        IV_crop = (ImageView) rootView.findViewById(R.id.IV_diary_cover_crop);
        IV_coverPhoto = (ImageView) rootView.findViewById(R.id.IV_diary_cover_photo);
        IV_weather = (ImageView) rootView.findViewById(R.id.IV_diary_weather);
        TV_weather = (TextView) rootView.findViewById(R.id.TV_diary_weather);
        IV_crop.setOnClickListener(this);
        TV_tag.setOnClickListener(this);
        TV_tagMore.setOnClickListener(this);
        IV_tagAdd.setOnClickListener(this);
    }

    /**
     * Editing an entry (a new one or an old one): the pencil of the tags is always there (it opens the window to
     * write them) and the person can choose what part of the cover photo is seen. Only reading: nothing of that.
     */
    public void setEditMode(boolean editMode) {
        this.editMode = editMode;
        setTags(savedTags);
        refreshCropButton();
        //Editing: a photo can be chosen for the cover without putting it in the text
        IV_coverPhoto.setVisibility(editMode ? View.VISIBLE : View.GONE);
    }

    /**
     * Called when the button "choose a photo for the cover" is pressed (only shown when editing).
     */
    public void setPickPhotoListener(View.OnClickListener listener) {
        IV_coverPhoto.setOnClickListener(listener);
    }

    /**
     * Called when the button "choose what part is seen" of the cover is pressed
     * (only shown when editing and the cover has a photo).
     */
    public void setPickAreaListener(Runnable pickAreaListener) {
        this.pickAreaListener = pickAreaListener;
    }

    private void refreshCropButton() {
        IV_crop.setVisibility(editMode && shownCover != null ? View.VISIBLE : View.GONE);
    }

    /**
     * The weather in the top left corner: only the icon when reading, the icon and its name when editing.
     */
    public void setWeather(int weather) {
        IV_weather.setImageResource(DiaryInfoHelper.getWeatherResourceId(weather));
        IV_weather.setVisibility(View.VISIBLE);
        String[] names = context.getResources().getStringArray(R.array.weather_list);
        if (editMode && weather >= 0 && weather < names.length) {
            TV_weather.setText(names[weather]);
            TV_weather.setVisibility(View.VISIBLE);
        } else {
            TV_weather.setVisibility(View.GONE);
        }
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
     * "October 21" / "Octubre 21": the month first and then the day (no hour, no year), in every language.
     */
    public static String formatDate(Calendar calendar) {
        Locale locale = Locale.getDefault();
        String text = new SimpleDateFormat("MMMM d", locale).format(calendar.getTime());
        if (text.length() > 0) {
            //Spanish writes the months with a small letter: "octubre 21" -> "Octubre 21"
            text = text.substring(0, 1).toUpperCase(locale) + text.substring(1);
        }
        return text;
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
        this.savedTags = savedTags;
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
            //When editing, the pencil is there to change them
            IV_tagAdd.setVisibility(editMode ? View.VISIBLE : View.GONE);
        }
    }

    /**
     * @param path complete path of the photo file, or null when there is no photo (plain color)
     * @param area the part of the photo that is seen (fractions of the photo), or null for the center
     */
    public void setCover(String path, RectF area) {
        if (path == null || path.length() == 0) {
            shownCover = null;
            shownCoverArea = null;
            SDV_cover.setController(null);
            SDV_cover.setVisibility(View.GONE);
            V_overlay.setVisibility(View.GONE);
            refreshCropButton();
            return;
        }
        boolean sameArea = area == null ? shownCoverArea == null : area.equals(shownCoverArea);
        if (path.equals(shownCover) && sameArea) {
            refreshCropButton();
            return;
        }
        shownCover = path;
        shownCoverArea = area == null ? null : new RectF(area);
        Uri uri = path.startsWith("file:") || path.startsWith("content:") ? Uri.parse(path) : Uri.fromFile(new java.io.File(path));
        //The cover has a fixed shape (see AspectRatioRelativeLayout)
        int width = ScreenHelper.getScreenWidth(context);
        AreaImage.show(SDV_cover, uri, area, width, Math.round(width / AspectRatioRelativeLayout.COVER_RATIO));
        SDV_cover.setVisibility(View.VISIBLE);
        V_overlay.setVisibility(View.VISIBLE);
        refreshCropButton();
    }

    @Override
    public void onClick(View v) {
        if (v == IV_crop) {
            if (pickAreaListener != null) {
                pickAreaListener.run();
            }
        } else if (v == IV_tagAdd) {
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
