package com.kiminonawa.mydiary.entries.diary.item;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.kiminonawa.mydiary.R;
import com.kiminonawa.mydiary.shared.EsotericStyle;
import com.kiminonawa.mydiary.shared.ScreenHelper;
import com.kiminonawa.mydiary.shared.ThemeManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * A table inside a diary. The first row is the header (bold). While writing, under the table there are buttons to add
 * or take away rows and columns and to choose how wide each column is (in parts of the width of the page).
 * <p>
 * Saved as JSON: {"w":[30,40,30],"c":[["a","b","c"],["d","e","f"]]}, "w" the width of every column (it is a
 * proportion: they do not need to add 100) and "c" the text of every cell, row by row.
 */
public class DiaryTable implements IDairyRow {

    public static final int MAX_COLUMNS = 6;
    public static final int MAX_ROWS = 40;

    private final Context context;
    private final LinearLayout root;
    private final LinearLayout grid;
    private final LinearLayout controls;
    private final ImageView IV_delete;
    private final List<List<EditText>> cells = new ArrayList<>();
    private final List<Integer> widths = new ArrayList<>();
    private boolean editMode = false;
    private int position;

    public DiaryTable(Context context) {
        this.context = context;
        int dp6 = dp(6);
        root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp6;
        params.bottomMargin = dp6;
        root.setLayoutParams(params);

        grid = new LinearLayout(context);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setBackground(border(0, true));
        root.addView(grid, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        //The buttons while writing; they scroll sideways if the screen is narrow
        HorizontalScrollView scroll = new HorizontalScrollView(context);
        scroll.setHorizontalScrollBarEnabled(false);
        controls = new LinearLayout(context);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER_VERTICAL);
        scroll.addView(controls);
        root.addView(scroll, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        addControl(R.string.table_add_row, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                addRow();
            }
        });
        addControl(R.string.table_add_column, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                addColumn();
            }
        });
        addControl(R.string.table_remove_row, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                removeRow();
            }
        });
        addControl(R.string.table_remove_column, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                removeColumn();
            }
        });
        addControl(R.string.table_widths, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showWidthsDialog();
            }
        });
        IV_delete = new ImageView(context);
        IV_delete.setId(R.id.IV_diary_table_delete);
        IV_delete.setImageResource(R.drawable.ic_delete_white_24dp);
        IV_delete.setBackgroundColor(ContextCompat.getColor(context, R.color.diary_photo_layout_delete_bg));
        IV_delete.setPadding(dp6, dp6, dp6, dp6);
        IV_delete.setContentDescription(context.getString(R.string.table_delete));
        LinearLayout.LayoutParams deleteLp = new LinearLayout.LayoutParams(dp(36), dp(36));
        deleteLp.leftMargin = dp6;
        controls.addView(IV_delete, deleteLp);
        setContent(null);
        setEditMode(false);
    }

    private int dp(float v) {
        return ScreenHelper.dpToPixel(context.getResources(), v);
    }

    private int lineColor() {
        return EsotericStyle.isOn() ? 0xFF5A1A24 : 0x55888888;
    }

    private GradientDrawable border(int fill, boolean outer) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setStroke(Math.max(1, dp(outer ? 1.2f : 0.6f)), lineColor());
        if (outer) {
            d.setCornerRadius(dp(4));
        }
        return d;
    }

    private void addControl(int text, View.OnClickListener listener) {
        TextView button = new TextView(context);
        button.setText(text);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        int color = EsotericStyle.isOn() ? EsotericStyle.RED_TEXT : ThemeManager.getInstance().getThemeMainColor(context);
        button.setTextColor(color);
        button.setGravity(Gravity.CENTER);
        button.setMinHeight(dp(40));
        button.setPadding(dp(10), 0, dp(10), 0);
        button.setBackground(border(0, true));
        button.setOnClickListener(listener);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(40));
        lp.topMargin = dp(6);
        lp.rightMargin = dp(6);
        controls.addView(button, lp);
    }

    // ---------------------------------------------------------------- the cells

    private EditText newCell(String text, boolean header) {
        EditText cell = new EditText(context);
        cell.setText(text);
        cell.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        cell.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        cell.setPadding(dp(6), dp(6), dp(6), dp(6));
        cell.setMinHeight(dp(40));
        cell.setGravity(Gravity.TOP | Gravity.START);
        int textColor = EsotericStyle.isOn() ? EsotericStyle.INK_SOFT : ContextCompat.getColor(context, R.color.app_text);
        cell.setTextColor(textColor);
        cell.setHintTextColor(ContextCompat.getColor(context, R.color.edittext_hint));
        int headerFill = EsotericStyle.isOn() ? 0x33B3122B
                : (ThemeManager.getInstance().getThemeMainColor(context) & 0x00FFFFFF) | 0x26000000;
        cell.setBackground(border(header ? headerFill : 0, false));
        if (header) {
            cell.setTypeface(EsotericStyle.isOn() ? EsotericStyle.cormorantBold(context) : Typeface.DEFAULT_BOLD);
        } else if (EsotericStyle.isOn()) {
            cell.setTypeface(EsotericStyle.cormorant(context));
        }
        applyEditMode(cell);
        return cell;
    }

    private void applyEditMode(EditText cell) {
        cell.setFocusable(editMode);
        cell.setFocusableInTouchMode(editMode);
        cell.setCursorVisible(editMode);
        cell.setLongClickable(editMode);
        cell.setHint(editMode ? "…" : null);
    }

    private List<List<String>> currentTexts() {
        List<List<String>> texts = new ArrayList<>();
        for (List<EditText> row : cells) {
            List<String> r = new ArrayList<>();
            for (EditText c : row) {
                r.add(c.getText().toString());
            }
            texts.add(r);
        }
        return texts;
    }

    /**
     * Makes the views of the table again from the texts and the widths.
     */
    private void build(List<List<String>> texts) {
        grid.removeAllViews();
        cells.clear();
        int columns = widths.size();
        for (int r = 0; r < texts.size(); r++) {
            LinearLayout rowView = new LinearLayout(context);
            rowView.setOrientation(LinearLayout.HORIZONTAL);
            List<EditText> rowCells = new ArrayList<>();
            for (int c = 0; c < columns; c++) {
                String text = c < texts.get(r).size() ? texts.get(r).get(c) : "";
                EditText cell = newCell(text, r == 0);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                        LinearLayout.LayoutParams.MATCH_PARENT, Math.max(1, widths.get(c)));
                rowView.addView(cell, lp);
                rowCells.add(cell);
            }
            grid.addView(rowView, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
            cells.add(rowCells);
        }
    }

    private void addRow() {
        List<List<String>> texts = currentTexts();
        if (texts.size() >= MAX_ROWS) return;
        List<String> empty = new ArrayList<>();
        for (int i = 0; i < widths.size(); i++) empty.add("");
        texts.add(empty);
        build(texts);
        cells.get(cells.size() - 1).get(0).requestFocus();
    }

    private void removeRow() {
        List<List<String>> texts = currentTexts();
        if (texts.size() <= 1) return;
        texts.remove(texts.size() - 1);
        build(texts);
    }

    private void addColumn() {
        if (widths.size() >= MAX_COLUMNS) return;
        List<List<String>> texts = currentTexts();
        int average = 0;
        for (int w : widths) average += w;
        average = widths.isEmpty() ? 33 : Math.max(5, average / widths.size());
        widths.add(average);
        for (List<String> row : texts) row.add("");
        build(texts);
    }

    private void removeColumn() {
        if (widths.size() <= 1) return;
        List<List<String>> texts = currentTexts();
        widths.remove(widths.size() - 1);
        for (List<String> row : texts) {
            if (row.size() > widths.size()) row.remove(row.size() - 1);
        }
        build(texts);
    }

    /**
     * A window with one slider per column: how much of the width each one takes.
     */
    private void showWidthsDialog() {
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(8), dp(20), 0);
        int total = 0;
        for (int w : widths) total += w;
        final List<SeekBar> bars = new ArrayList<>();
        for (int i = 0; i < widths.size(); i++) {
            final TextView label = new TextView(context);
            label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            final int column = i + 1;
            final SeekBar bar = new SeekBar(context);
            bar.setMax(90);
            int percent = Math.round(widths.get(i) * 100f / Math.max(1, total));
            bar.setProgress(Math.max(5, Math.min(90, percent)));
            label.setText(context.getString(R.string.table_column_width, column, bar.getProgress()));
            bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (progress < 5) {
                        seekBar.setProgress(5);
                        return;
                    }
                    label.setText(context.getString(R.string.table_column_width, column, progress));
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                }
            });
            box.addView(label);
            box.addView(bar);
            bars.add(bar);
        }
        new AlertDialog.Builder(context)
                .setTitle(R.string.table_widths_title)
                .setView(box)
                .setPositiveButton(android.R.string.ok, new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        List<List<String>> texts = currentTexts();
                        for (int i = 0; i < bars.size() && i < widths.size(); i++) {
                            widths.set(i, Math.max(5, bars.get(i).getProgress()));
                        }
                        build(texts);
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    public void setDeleteClickListener(View.OnClickListener listener) {
        IV_delete.setOnClickListener(listener);
    }

    // ---------------------------------------------------------------- IDairyRow

    @Override
    public void setContent(String content) {
        widths.clear();
        List<List<String>> texts = new ArrayList<>();
        try {
            JSONObject json = new JSONObject(content);
            JSONArray w = json.getJSONArray("w");
            for (int i = 0; i < w.length() && i < MAX_COLUMNS; i++) {
                widths.add(Math.max(1, w.getInt(i)));
            }
            JSONArray rows = json.getJSONArray("c");
            for (int r = 0; r < rows.length() && r < MAX_ROWS; r++) {
                JSONArray row = rows.getJSONArray(r);
                List<String> t = new ArrayList<>();
                for (int c = 0; c < row.length(); c++) {
                    t.add(row.optString(c, ""));
                }
                texts.add(t);
            }
        } catch (Exception e) {
            widths.clear();
            texts.clear();
        }
        if (widths.isEmpty()) {
            //A new table: 3 columns of the same width, 3 rows
            for (int i = 0; i < 3; i++) widths.add(33);
        }
        if (texts.isEmpty()) {
            for (int r = 0; r < 3; r++) {
                List<String> t = new ArrayList<>();
                for (int c = 0; c < widths.size(); c++) t.add("");
                texts.add(t);
            }
        }
        build(texts);
    }

    @Override
    public String getContent() {
        try {
            JSONObject json = new JSONObject();
            JSONArray w = new JSONArray();
            for (int width : widths) w.put(width);
            JSONArray rows = new JSONArray();
            for (List<String> row : currentTexts()) {
                JSONArray r = new JSONArray();
                for (String cell : row) r.put(cell);
                rows.put(r);
            }
            json.put("w", w);
            json.put("c", rows);
            return json.toString();
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * The text of the table for the summaries and the search: the cells separated by spaces.
     */
    public static String plainText(String content) {
        StringBuilder sb = new StringBuilder();
        try {
            JSONArray rows = new JSONObject(content).getJSONArray("c");
            for (int r = 0; r < rows.length(); r++) {
                JSONArray row = rows.getJSONArray(r);
                for (int c = 0; c < row.length(); c++) {
                    String t = row.optString(c, "").trim();
                    if (t.length() > 0) {
                        if (sb.length() > 0) sb.append(" · ");
                        sb.append(t);
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return sb.toString();
    }

    @Override
    public int getType() {
        return TYPE_TABLE;
    }

    @Override
    public View getView() {
        return root;
    }

    @Override
    public void setEditMode(boolean isEditMode) {
        editMode = isEditMode;
        controls.setVisibility(isEditMode ? View.VISIBLE : View.GONE);
        for (List<EditText> row : cells) {
            for (EditText c : row) applyEditMode(c);
        }
    }

    @Override
    public void setPosition(int position) {
        this.position = position;
        IV_delete.setTag(position);
    }

    @Override
    public int getPosition() {
        return position;
    }
}
