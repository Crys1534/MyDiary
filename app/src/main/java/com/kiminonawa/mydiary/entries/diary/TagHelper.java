package com.kiminonawa.mydiary.entries.diary;

import android.content.Context;
import android.content.DialogInterface;
import android.widget.FrameLayout;

import androidx.appcompat.app.AlertDialog;

import com.kiminonawa.mydiary.R;
import com.kiminonawa.mydiary.shared.ScreenHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Tags of a diary, like #school or #trip.
 * <p>
 * What the person writes: "#School  trip, #fun"
 * What is saved in the database: ",school,trip,fun," (always lower case, no #, no repeated tags)
 * What is shown: "school trip fun" (no #)
 */
public class TagHelper {

    /**
     * Text written by the person -> text saved in the database ("" when there are no tags).
     */
    public static String normalize(String input) {
        List<String> tags = new ArrayList<>();
        if (input != null) {
            for (String part : input.split("[\\s,;]+")) {
                String tag = cleanTag(part);
                if (tag.length() > 0 && !tags.contains(tag)) {
                    tags.add(tag);
                }
            }
        }
        if (tags.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(",");
        for (String tag : tags) {
            sb.append(tag).append(",");
        }
        return sb.toString();
    }

    /**
     * Text saved in the database -> list of tags (without #).
     */
    public static List<String> toList(String saved) {
        List<String> tags = new ArrayList<>();
        if (saved != null) {
            for (String part : saved.split(",")) {
                if (part.length() > 0) {
                    tags.add(part);
                }
            }
        }
        return tags;
    }

    /**
     * Text saved in the database -> "school trip" ("" when there are no tags).
     */
    public static String toDisplay(String saved) {
        StringBuilder sb = new StringBuilder();
        for (String tag : toList(saved)) {
            if (sb.length() > 0) {
                sb.append(" ");
            }
            sb.append(tag);
        }
        return sb.toString();
    }

    /**
     * One tag as it is kept: lower case, only letters, numbers, _ and -.
     */
    public static String cleanTag(String raw) {
        String lower = raw.toLowerCase(Locale.getDefault());
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            if (Character.isLetterOrDigit(c) || c == '_' || c == '-') {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    public interface Callback {
        /**
         * @param savedTags what the person wrote, ready to be saved (",school,trip," or "" for no tags)
         */
        void onTags(String savedTags);
    }

    /**
     * The small window to write the tags of an entry (it opens with the pencil of the cover). Every tag is a chip:
     * a space, a comma or Enter finishes the word that is being written.
     *
     * @param currentSavedTags the tags the entry has now, like ",school,trip,"
     */
    public static void showEditDialog(Context context, String currentSavedTags, final Callback callback) {
        final TagEditorView editor = new TagEditorView(context, currentSavedTags);
        int margin = ScreenHelper.dpToPixel(context.getResources(), 16);
        FrameLayout container = new FrameLayout(context);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        params.leftMargin = margin;
        params.rightMargin = margin;
        container.addView(editor, params);
        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(R.string.cover_tags_title)
                .setMessage(R.string.tags_dialog_hint)
                .setView(container)
                .setPositiveButton(R.string.dialog_button_ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int which) {
                        callback.onTags(editor.getSavedTags());
                    }
                })
                .setNegativeButton(R.string.dialog_button_cancel, null)
                .create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        }
        dialog.show();
        editor.getInput().requestFocus();
    }
}
