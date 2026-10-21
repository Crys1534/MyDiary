package com.kiminonawa.mydiary.entries.diary.item;

import android.view.View;

/**
 * Created by daxia on 2016/10/17.
 */

public interface IDairyRow {

    int TYPE_TEXT = 0;
    int TYPE_PHOTO = 1;
    int TYPE_WEB_BLOCK = 2;
    //A line of a checklist: "1" (done) or "0" (not done) followed by the text
    int TYPE_CHECKLIST = 3;
    //An audio that is played from a link (for example of Google Drive); the content is the link
    int TYPE_AUDIO = 4;

    void setContent(String content);

    String getContent();

    int getType();

    View getView();

    void setEditMode(boolean isEditMode);

    /**
     * For resort after add new item
     *
     * @param position
     */
    void setPosition(int position);

    /**
     * get position for auto save
     * @return
     */
    int getPosition();

}
