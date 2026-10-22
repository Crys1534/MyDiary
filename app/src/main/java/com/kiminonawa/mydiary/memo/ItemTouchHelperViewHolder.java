package com.kiminonawa.mydiary.memo;

/**
 * A row of the memo list that wants to know when it is picked up to be moved and when it is dropped.
 * (It replaces the interface that came from an abandoned library.)
 */
public interface ItemTouchHelperViewHolder {

    /**
     * The row was picked up.
     */
    void onItemSelected();

    /**
     * The row was dropped: back to the normal look.
     */
    void onItemClear();
}
