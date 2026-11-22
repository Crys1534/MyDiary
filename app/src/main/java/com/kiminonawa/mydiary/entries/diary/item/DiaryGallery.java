package com.kiminonawa.mydiary.entries.diary.item;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.facebook.drawee.view.SimpleDraweeView;
import com.kiminonawa.mydiary.R;
import com.kiminonawa.mydiary.entries.diary.area.AreaImage;
import com.kiminonawa.mydiary.shared.FileManager;
import com.kiminonawa.mydiary.shared.ThemeManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Several photos of a diary shown together in a design the person chooses:
 * 2×2 (4 photos), 3×2 (6 photos) or one big photo with two small ones beside it (3 photos).
 * <p>
 * Saved as JSON: {"l":"2x2","p":["file1.jpg","file2.jpg"]}. The photo files are in the folder of the entry, like the
 * other photos.
 */
public class DiaryGallery implements IDairyRow {

    public static final String LAYOUT_2X2 = "2x2";
    public static final String LAYOUT_3X2 = "3x2";
    public static final String LAYOUT_1_2 = "1+2";
    public static final String[] LAYOUTS = {LAYOUT_2X2, LAYOUT_3X2, LAYOUT_1_2};

    public interface Editor {
        /**
         * The empty place "+" of a gallery was touched: choose photos for it.
         */
        void addPhotosToGallery(DiaryGallery gallery);

        /**
         * The gallery changed (a photo was taken away, the design changed): the cover may change.
         */
        void onGalleryChanged();
    }

    public interface PhotoClick {
        void onPhotoClick(int indexInGallery);
    }

    private final Context context;
    private final LinearLayout root;
    private final Grid grid;
    private final LinearLayout bar;
    private final ImageView IV_delete;
    private final List<String> photos = new ArrayList<>();
    private String layout = LAYOUT_2X2;
    private File folder;
    private boolean editMode = false;
    private int position;
    private Editor editor;
    private PhotoClick photoClick;

    public DiaryGallery(Context context) {
        this.context = context;
        root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(6);
        params.bottomMargin = dp(6);
        root.setLayoutParams(params);
        grid = new Grid(context);
        root.addView(grid, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        //While writing: change the design, add photos, delete the gallery
        bar = new LinearLayout(context);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(bar, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        addBarButton(R.string.gallery_change_layout, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                chooseLayout(DiaryGallery.this.context, layout, new LayoutChosen() {
                    @Override
                    public void onLayoutChosen(String chosen) {
                        setLayout(chosen);
                        if (photos.size() > capacity(chosen)) {
                            android.widget.Toast.makeText(DiaryGallery.this.context,
                                    DiaryGallery.this.context.getString(R.string.gallery_too_many, capacity(chosen)),
                                    android.widget.Toast.LENGTH_LONG).show();
                        }
                        if (editor != null) {
                            editor.onGalleryChanged();
                        }
                    }
                });
            }
        });
        addBarButton(R.string.gallery_add_photos, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (editor != null) {
                    editor.addPhotosToGallery(DiaryGallery.this);
                }
            }
        });
        View space = new View(context);
        bar.addView(space, new LinearLayout.LayoutParams(0, 1, 1f));
        IV_delete = new ImageView(context);
        IV_delete.setId(R.id.IV_diary_gallery_delete);
        IV_delete.setImageResource(R.drawable.ic_delete_white_24dp);
        IV_delete.setBackgroundColor(ContextCompat.getColor(context, R.color.diary_photo_layout_delete_bg));
        IV_delete.setPadding(dp(6), dp(6), dp(6), dp(6));
        IV_delete.setContentDescription(context.getString(R.string.gallery_delete));
        LinearLayout.LayoutParams deleteLp = new LinearLayout.LayoutParams(dp(36), dp(36));
        deleteLp.topMargin = dp(6);
        bar.addView(IV_delete, deleteLp);
        setEditMode(false);
    }

    private int dp(float v) {
        return Math.round(v * context.getResources().getDisplayMetrics().density);
    }

    private void addBarButton(int text, View.OnClickListener listener) {
        TextView button = new TextView(context);
        button.setText(text);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        button.setTextColor(ThemeManager.getInstance().getThemeMainColor(context));
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(12), 0, dp(12), 0);
        GradientDrawable border = new GradientDrawable();
        border.setCornerRadius(dp(4));
        border.setStroke(Math.max(1, dp(1)), 0x55888888);
        button.setBackground(border);
        button.setOnClickListener(listener);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(40));
        lp.topMargin = dp(6);
        lp.rightMargin = dp(6);
        bar.addView(button, lp);
    }

    // ---------------------------------------------------------------- the designs

    /**
     * How many photos fit in a design.
     */
    public static int capacity(String layout) {
        if (LAYOUT_3X2.equals(layout)) {
            return 6;
        } else if (LAYOUT_1_2.equals(layout)) {
            return 3;
        }
        return 4;
    }

    private static int columns(String layout) {
        return LAYOUT_2X2.equals(layout) ? 2 : 3;
    }

    private static int rows(String layout) {
        return 2;
    }

    /**
     * The places of the photos, in squares of the design: {left, top, width, height}.
     */
    private static int[][] cells(String layout) {
        if (LAYOUT_3X2.equals(layout)) {
            return new int[][]{{0, 0, 1, 1}, {1, 0, 1, 1}, {2, 0, 1, 1}, {0, 1, 1, 1}, {1, 1, 1, 1}, {2, 1, 1, 1}};
        } else if (LAYOUT_1_2.equals(layout)) {
            return new int[][]{{0, 0, 2, 2}, {2, 0, 1, 1}, {2, 1, 1, 1}};
        }
        return new int[][]{{0, 0, 1, 1}, {1, 0, 1, 1}, {0, 1, 1, 1}, {1, 1, 1, 1}};
    }

    public static String layoutName(Context context, String layout) {
        if (LAYOUT_3X2.equals(layout)) {
            return context.getString(R.string.gallery_layout_3x2);
        } else if (LAYOUT_1_2.equals(layout)) {
            return context.getString(R.string.gallery_layout_1_2);
        }
        return context.getString(R.string.gallery_layout_2x2);
    }

    public interface LayoutChosen {
        void onLayoutChosen(String layout);
    }

    /**
     * A window with the three designs drawn small, to choose one.
     */
    public static void chooseLayout(final Context context, String current, final LayoutChosen callback) {
        final float d = context.getResources().getDisplayMetrics().density;
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Math.round(16 * d), Math.round(8 * d), Math.round(16 * d), 0);
        final AlertDialog[] dialog = new AlertDialog[1];
        int color = ThemeManager.getInstance().getThemeMainColor(context);
        for (final String layout : LAYOUTS) {
            LinearLayout option = new LinearLayout(context);
            option.setOrientation(LinearLayout.HORIZONTAL);
            option.setGravity(Gravity.CENTER_VERTICAL);
            option.setPadding(Math.round(8 * d), Math.round(8 * d), Math.round(8 * d), Math.round(8 * d));
            option.setMinimumHeight(Math.round(64 * d));
            if (layout.equals(current)) {
                GradientDrawable selected = new GradientDrawable();
                selected.setCornerRadius(8 * d);
                selected.setColor((color & 0x00FFFFFF) | 0x22000000);
                option.setBackground(selected);
            }
            Preview preview = new Preview(context, layout, color);
            option.addView(preview, new LinearLayout.LayoutParams(Math.round(72 * d), Math.round(48 * d)));
            TextView label = new TextView(context);
            label.setText(layoutName(context, layout));
            label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            label.setPadding(Math.round(16 * d), 0, 0, 0);
            option.addView(label);
            option.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (dialog[0] != null) {
                        dialog[0].dismiss();
                    }
                    callback.onLayoutChosen(layout);
                }
            });
            box.addView(option, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
        }
        dialog[0] = new AlertDialog.Builder(context)
                .setTitle(R.string.gallery_choose_layout)
                .setView(box)
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /**
     * A small drawing of a design (for the window where it is chosen).
     */
    private static class Preview extends View {
        private final String layout;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();

        Preview(Context context, String layout, int color) {
            super(context);
            this.layout = layout;
            paint.setColor(color);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            int cols = columns(layout), rows = rows(layout);
            float unit = Math.min(getWidth() / (float) cols, getHeight() / (float) rows);
            float left = (getWidth() - unit * cols) / 2f, top = (getHeight() - unit * rows) / 2f;
            float gap = getResources().getDisplayMetrics().density * 2;
            for (int[] c : cells(layout)) {
                rect.set(left + c[0] * unit + gap / 2, top + c[1] * unit + gap / 2,
                        left + (c[0] + c[2]) * unit - gap / 2, top + (c[1] + c[3]) * unit - gap / 2);
                canvas.drawRoundRect(rect, gap, gap, paint);
            }
        }
    }

    /**
     * The photos in their places. Every square of the design has the same size: the height follows the width.
     */
    private class Grid extends ViewGroup {
        Grid(Context context) {
            super(context);
        }

        private int gap() {
            return dp(3);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int width = MeasureSpec.getSize(widthMeasureSpec);
            int cols = columns(layout), rows = rows(layout);
            float unit = width / (float) cols;
            int height = Math.round(unit * rows);
            int[][] cells = cells(layout);
            for (int i = 0; i < getChildCount() && i < cells.length; i++) {
                int[] c = cells[i];
                int w = Math.round(c[2] * unit) - gap(), h = Math.round(c[3] * unit) - gap();
                getChildAt(i).measure(MeasureSpec.makeMeasureSpec(Math.max(1, w), MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(Math.max(1, h), MeasureSpec.EXACTLY));
            }
            setMeasuredDimension(width, height);
        }

        @Override
        protected void onLayout(boolean changed, int l, int t, int r, int b) {
            float unit = (r - l) / (float) columns(layout);
            int[][] cells = cells(layout);
            int half = gap() / 2;
            for (int i = 0; i < getChildCount() && i < cells.length; i++) {
                View child = getChildAt(i);
                int x = Math.round(cells[i][0] * unit) + half, y = Math.round(cells[i][1] * unit) + half;
                child.layout(x, y, x + child.getMeasuredWidth(), y + child.getMeasuredHeight());
            }
        }
    }

    /**
     * Makes the places of the photos again (after the design or the photos changed).
     */
    private void build() {
        grid.removeAllViews();
        int[][] cells = cells(layout);
        int width = DiaryItemHelper.getVisibleWidth(context);
        float unit = width / (float) columns(layout);
        for (int i = 0; i < cells.length; i++) {
            final int index = i;
            if (i < photos.size()) {
                FrameLayout cell = new FrameLayout(context);
                SimpleDraweeView photo = new SimpleDraweeView(context);
                photo.setBackgroundColor(0x22888888);
                cell.addView(photo, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT));
                Uri uri = uriOf(photos.get(i));
                if (uri != null) {
                    AreaImage.show(photo, uri, null, Math.round(cells[i][2] * unit), Math.round(cells[i][3] * unit));
                }
                photo.setContentDescription(context.getString(R.string.gallery_photo, i + 1));
                photo.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (editMode) {
                            askRemove(index);
                        } else if (photoClick != null) {
                            photoClick.onPhotoClick(index);
                        }
                    }
                });
                grid.addView(cell);
            } else {
                //An empty place: "+" while writing, nothing while reading
                TextView empty = new TextView(context);
                empty.setText("+");
                empty.setGravity(Gravity.CENTER);
                empty.setTextSize(TypedValue.COMPLEX_UNIT_SP, 30);
                empty.setTextColor(0xAA888888);
                GradientDrawable dashed = new GradientDrawable();
                dashed.setStroke(Math.max(1, dp(1.5f)), 0x88888888, dp(6), dp(4));
                dashed.setCornerRadius(dp(4));
                empty.setBackground(dashed);
                empty.setContentDescription(context.getString(R.string.gallery_add_photos));
                empty.setVisibility(editMode ? View.VISIBLE : View.INVISIBLE);
                empty.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (editMode && editor != null) {
                            editor.addPhotosToGallery(DiaryGallery.this);
                        }
                    }
                });
                grid.addView(empty);
            }
        }
        grid.requestLayout();
    }

    private void askRemove(final int index) {
        new AlertDialog.Builder(context)
                .setMessage(R.string.gallery_remove_photo)
                .setPositiveButton(R.string.gallery_remove_photo_yes, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (index < photos.size()) {
                            photos.remove(index);
                            build();
                            if (editor != null) {
                                editor.onGalleryChanged();
                            }
                        }
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private Uri uriOf(String fileName) {
        if (folder == null || fileName == null || fileName.length() == 0) {
            return null;
        }
        return Uri.parse(FileManager.FILE_HEADER + folder.getAbsolutePath() + "/" + fileName);
    }

    // ---------------------------------------------------------------- for the editor and the reader

    public void setFolder(File folder) {
        this.folder = folder;
        build();
    }

    public void setEditor(Editor editor) {
        this.editor = editor;
    }

    public void setPhotoClickListener(PhotoClick click) {
        this.photoClick = click;
    }

    public void setDeleteClickListener(View.OnClickListener listener) {
        IV_delete.setOnClickListener(listener);
    }

    public String getLayout() {
        return layout;
    }

    public void setLayout(String layout) {
        this.layout = isLayout(layout) ? layout : LAYOUT_2X2;
        build();
    }

    private static boolean isLayout(String layout) {
        for (String l : LAYOUTS) {
            if (l.equals(layout)) {
                return true;
            }
        }
        return false;
    }

    public List<String> getPhotos() {
        return new ArrayList<>(photos);
    }

    /**
     * The complete addresses of the photos ("file:///..."), in order.
     */
    public List<Uri> getPhotoUris() {
        List<Uri> uris = new ArrayList<>();
        for (String p : photos) {
            Uri uri = uriOf(p);
            if (uri != null) {
                uris.add(uri);
            }
        }
        return uris;
    }

    public int freePlaces() {
        return Math.max(0, capacity(layout) - photos.size());
    }

    /**
     * Puts photos in the empty places.
     *
     * @return the photos that did not fit
     */
    public List<String> addPhotos(List<String> fileNames) {
        List<String> rest = new ArrayList<>();
        for (String f : fileNames) {
            if (photos.size() < capacity(layout)) {
                photos.add(f);
            } else {
                rest.add(f);
            }
        }
        build();
        return rest;
    }

    /**
     * The photo files of a saved gallery (to copy them, to find the cover...).
     */
    public static List<String> photosOf(String content) {
        List<String> list = new ArrayList<>();
        try {
            JSONArray p = new JSONObject(content).getJSONArray("p");
            for (int i = 0; i < p.length(); i++) {
                String f = p.optString(i, "");
                if (f.length() > 0) {
                    list.add(f);
                }
            }
        } catch (Exception ignored) {
        }
        return list;
    }

    // ---------------------------------------------------------------- IDairyRow

    @Override
    public void setContent(String content) {
        photos.clear();
        String l = LAYOUT_2X2;
        try {
            JSONObject json = new JSONObject(content);
            l = json.optString("l", LAYOUT_2X2);
        } catch (Exception ignored) {
        }
        photos.addAll(photosOf(content));
        layout = isLayout(l) ? l : LAYOUT_2X2;
        //A design that does not hold all the photos keeps them all anyway: they are not lost
        build();
    }

    @Override
    public String getContent() {
        try {
            JSONObject json = new JSONObject();
            json.put("l", layout);
            JSONArray p = new JSONArray();
            for (String f : photos) {
                p.put(f);
            }
            json.put("p", p);
            return json.toString();
        } catch (Exception e) {
            return "";
        }
    }

    @Override
    public int getType() {
        return TYPE_GALLERY;
    }

    @Override
    public View getView() {
        return root;
    }

    @Override
    public void setEditMode(boolean isEditMode) {
        editMode = isEditMode;
        bar.setVisibility(isEditMode ? View.VISIBLE : View.GONE);
        build();
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
