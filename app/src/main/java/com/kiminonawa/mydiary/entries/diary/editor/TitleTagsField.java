package com.kiminonawa.mydiary.entries.diary.editor;

import android.content.Context;
import android.graphics.PorterDuff;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;

import com.kiminonawa.mydiary.R;
import com.kiminonawa.mydiary.entries.diary.DiaryCover;
import com.kiminonawa.mydiary.entries.diary.TagHelper;

/**
 * The title and the tags of an entry while it is written. The cover shows them as they are typed.
 * The tags are not typed in a box: they are written in the small window of the pencil of the cover
 * ({@link #editTags}); the hidden box only keeps the text.
 */
public class TitleTagsField {

    private final EditText title;
    private final EditText tags;

    public TitleTagsField(View rootView, final DiaryCover cover, int lineColor) {
        title = (EditText) rootView.findViewById(R.id.EDT_diary_title);
        tags = (EditText) rootView.findViewById(R.id.EDT_diary_tags);
        title.getBackground().mutate().setColorFilter(lineColor, PorterDuff.Mode.SRC_ATOP);
        //The cover shows the title while it is written
        title.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                cover.setTitle(s.toString());
            }
        });
        tags.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                cover.setTags(TagHelper.normalize(s.toString()));
            }
        });
        tags.setVisibility(View.GONE);
    }

    public EditText getTitleBox() {
        return title;
    }

    public String getTitle() {
        return title.getText().toString();
    }

    public void setTitle(String text) {
        title.setText(text);
    }

    /**
     * @return the tags like ",school,trip," (what the database keeps)
     */
    public String getTags() {
        return TagHelper.normalize(tags.getText().toString());
    }

    /**
     * @param savedTags tags like ",school,trip," (what the database keeps)
     */
    public void setTags(String savedTags) {
        tags.setText(TagHelper.toDisplay(savedTags));
    }

    /**
     * The window to write the tags (the pencil of the cover).
     */
    public void editTags(Context context) {
        TagHelper.showEditDialog(context, getTags(), new TagHelper.Callback() {
            @Override
            public void onTags(String savedTags) {
                setTags(savedTags);
            }
        });
    }

    private abstract static class SimpleTextWatcher implements TextWatcher {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }
    }
}
