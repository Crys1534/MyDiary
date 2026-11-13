package com.kiminonawa.mydiary.shared;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Build;
import android.text.Spannable;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.style.ForegroundColorSpan;
import android.text.style.MetricAffectingSpan;
import android.text.style.RelativeSizeSpan;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.core.view.ViewCompat;
import androidx.core.widget.CompoundButtonCompat;

import com.kiminonawa.mydiary.R;

import java.util.Calendar;
import java.util.Locale;
import java.util.Random;

/**
 * The "Esoteric" look of a category: black pages, white ink and blood red. Gothic letters for the titles (Unifraktur
 * Maguntia), Cinzel for the small labels and Cormorant Garamond for the text (the three fonts are in assets/fonts,
 * with their licenses). Only the look changes: nothing of what is saved is touched.
 * <p>
 * Every screen of the category calls here once its views exist; when the category has another theme nothing happens.
 */
public final class EsotericStyle {

    public static final int BLOOD = 0xFFB3122B;
    public static final int BLOOD_DARK = 0xFF5A1A24;
    public static final int RED_TEXT = 0xFFFF5A6A;
    public static final int RED_SOFT = 0xFFFF8A96;
    public static final int RED_GLOW = 0xFFFF3348;
    public static final int INK = 0xFFFFFFFF;
    public static final int INK_SOFT = 0xFFEDE6E2;
    public static final int BONE = 0xFFE8E0DC;
    public static final int GRAY = 0xFFA89EA1;
    public static final int GRAY_TEXT = 0xFFBDB3B6;
    public static final int HINT = 0xFF8F8588;
    public static final int CARD = 0xFF0E0A0C;
    public static final int LINE = 0xFF2A1A1E;
    public static final int BLACK = 0xFF000000;

    private static Typeface cinzel, cormorant, cormorantBold, cormorantItalic, fraktur;

    private EsotericStyle() {
    }

    public static boolean isOn() {
        return CategoryTheme.current() == CategoryTheme.ESOTERIC;
    }

    // ------------------------------------------------------------------ fonts

    private static Typeface load(Context context, String file) {
        try {
            return Typeface.createFromAsset(context.getApplicationContext().getAssets(), "fonts/" + file);
        } catch (Exception e) {
            return Typeface.SERIF;
        }
    }

    public static Typeface cinzel(Context c) {
        if (cinzel == null) cinzel = load(c, "eso_cinzel.ttf");
        return cinzel;
    }

    public static Typeface cormorant(Context c) {
        if (cormorant == null) cormorant = load(c, "eso_cormorant.ttf");
        return cormorant;
    }

    public static Typeface cormorantBold(Context c) {
        if (cormorantBold == null) cormorantBold = load(c, "eso_cormorant_bold.ttf");
        return cormorantBold;
    }

    public static Typeface cormorantItalic(Context c) {
        if (cormorantItalic == null) cormorantItalic = load(c, "eso_cormorant_italic.ttf");
        return cormorantItalic;
    }

    public static Typeface fraktur(Context c) {
        if (fraktur == null) fraktur = load(c, "eso_fraktur.ttf");
        return fraktur;
    }

    private static float dp(Context c, float v) {
        return v * c.getResources().getDisplayMetrics().density;
    }

    /**
     * Small letters with space between them (CINZEL).
     */
    public static void label(TextView tv, float sp, int color, float spacing) {
        if (tv == null) return;
        tv.setTypeface(cinzel(tv.getContext()));
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        tv.setTextColor(color);
        tv.setAllCaps(true);
        if (Build.VERSION.SDK_INT >= 21) {
            tv.setLetterSpacing(spacing);
        }
    }

    private static void gothic(TextView tv, float sp, int color) {
        if (tv == null) return;
        tv.setTypeface(fraktur(tv.getContext()));
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        tv.setTextColor(color);
        tv.setShadowLayer(dp(tv.getContext(), 9), 0, 0, 0x99FF283C);
    }

    private static void tint(View v, int color) {
        if (v instanceof ImageView) {
            ((ImageView) v).setColorFilter(color);
        }
    }

    // ------------------------------------------------------------------ drawables

    /**
     * Black with a thin line on the top or the bottom.
     */
    public static Drawable barBackground(Context c, boolean lineOnTop) {
        GradientDrawable black = new GradientDrawable();
        black.setColor(BLACK);
        GradientDrawable line = new GradientDrawable();
        line.setColor(BLOOD);
        LayerDrawable layers = new LayerDrawable(new Drawable[]{line, black});
        int px = Math.max(1, Math.round(dp(c, 1)));
        if (lineOnTop) {
            layers.setLayerInset(1, 0, px, 0, 0);
        } else {
            layers.setLayerInset(1, 0, 0, 0, px);
        }
        return layers;
    }

    /**
     * The card of one entry in the list: almost black, a dark red border and a thin red light on top.
     */
    public static Drawable cardBackground(Context c) {
        GradientDrawable card = new GradientDrawable();
        card.setColor(CARD);
        card.setStroke(Math.max(1, Math.round(dp(c, 1))), LINE);
        int px = Math.max(1, Math.round(dp(c, 1)));
        //the red light: a 1dp line just inside the top border
        return new LayerDrawable(new Drawable[]{card, new TopLineDrawable(0x55FF3C50, px)});
    }

    private static class TopLineDrawable extends Drawable {
        private final Paint p = new Paint();
        private final int h;

        TopLineDrawable(int color, int h) {
            p.setColor(color);
            this.h = h;
        }

        @Override
        public void draw(Canvas canvas) {
            Rect b = getBounds();
            canvas.drawRect(b.left + h, b.top + h, b.right - h, b.top + 2 * h, p);
        }

        @Override
        public void setAlpha(int alpha) {
        }

        @Override
        public void setColorFilter(ColorFilter cf) {
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }

    /**
     * A small frame (tag of an entry).
     */
    public static Drawable outline(Context c, int color) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(Color.TRANSPARENT);
        d.setStroke(Math.max(1, Math.round(dp(c, 1))), color);
        return d;
    }

    /**
     * The night behind the list: dark red light at the top that turns into black, and a big pentagram that can
     * hardly be seen.
     */
    public static class NightDrawable extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path star = new Path();
        private final float density;
        private final boolean sigil;

        public NightDrawable(Context c, boolean sigil) {
            density = c.getResources().getDisplayMetrics().density;
            this.sigil = sigil;
        }

        @Override
        public void draw(Canvas canvas) {
            Rect b = getBounds();
            float w = b.width(), h = b.height();
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(null);
            paint.setColor(0xFF070506);
            canvas.drawRect(b, paint);
            paint.setShader(new RadialGradient(b.left + w / 2f, b.top, Math.max(w, h * 0.7f),
                    new int[]{0xFF1E0A10, 0xFF070506, 0xFF000000}, new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
            canvas.drawRect(b, paint);
            paint.setShader(null);
            if (sigil) {
                float r = Math.min(w, h) * 0.52f;
                float cx = b.left + w / 2f, cy = b.top + h * 0.5f;
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(Math.max(1f, density * 1.3f));
                paint.setColor(0x1AFF2A40);
                canvas.drawCircle(cx, cy, r, paint);
                canvas.drawCircle(cx, cy, r * 0.9f, paint);
                pentagram(star, cx, cy, r * 0.9f);
                canvas.drawPath(star, paint);
            }
        }

        @Override
        public void setAlpha(int alpha) {
        }

        @Override
        public void setColorFilter(ColorFilter cf) {
        }

        @Override
        public int getOpacity() {
            return PixelFormat.OPAQUE;
        }
    }

    static void pentagram(Path path, float cx, float cy, float r) {
        path.reset();
        for (int i = 0; i < 5; i++) {
            //every second point of a pentagon, starting at the top
            double a = -Math.PI / 2 + i * 2 * (2 * Math.PI / 5);
            float x = cx + (float) Math.cos(a) * r, y = cy + (float) Math.sin(a) * r;
            if (i == 0) path.moveTo(x, y);
            else path.lineTo(x, y);
        }
        path.close();
    }

    /**
     * The black sheet of an entry: almost black, a bit lighter at the top, with a dark red shadow on its borders.
     */
    public static class BlackPageDrawable extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float density;

        public BlackPageDrawable(Context c) {
            density = c.getResources().getDisplayMetrics().density;
        }

        @Override
        public void draw(Canvas canvas) {
            Rect b = getBounds();
            paint.setShader(new LinearGradient(0, b.top, 0, b.bottom, 0xFF0B0809, 0xFF050304, Shader.TileMode.CLAMP));
            canvas.drawRect(b, paint);
            float edge = 46 * density;
            int red = 0x40780014, clear = 0x00780014;
            paint.setShader(new LinearGradient(b.left, 0, b.left + edge, 0, red, clear, Shader.TileMode.CLAMP));
            canvas.drawRect(b.left, b.top, b.left + edge, b.bottom, paint);
            paint.setShader(new LinearGradient(b.right, 0, b.right - edge, 0, red, clear, Shader.TileMode.CLAMP));
            canvas.drawRect(b.right - edge, b.top, b.right, b.bottom, paint);
            paint.setShader(null);
        }

        @Override
        public void setAlpha(int alpha) {
        }

        @Override
        public void setColorFilter(ColorFilter cf) {
        }

        @Override
        public int getOpacity() {
            return PixelFormat.OPAQUE;
        }
    }

    /**
     * The top of an entry (date and title): a dark red light in the middle that turns into black, a faint pentagram
     * behind the date and a red line at the bottom.
     */
    public static class CoverDrawable extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path star = new Path();
        private final float density;

        public CoverDrawable(Context c) {
            density = c.getResources().getDisplayMetrics().density;
        }

        @Override
        public void draw(Canvas canvas) {
            Rect b = getBounds();
            float w = b.width(), h = b.height();
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(BLACK);
            canvas.drawRect(b, paint);
            paint.setShader(new RadialGradient(b.left + w / 2f, b.top + h * 0.4f, Math.max(w, h) * 0.75f,
                    new int[]{0xFF3A0710, 0xFF12040A, 0xFF000000}, new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
            canvas.drawRect(b, paint);
            paint.setShader(null);
            float r = h * 0.48f;
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(Math.max(1f, density));
            paint.setColor(0x26FF2A40);
            canvas.drawCircle(b.left + w / 2f, b.top + h / 2f, r, paint);
            pentagram(star, b.left + w / 2f, b.top + h / 2f, r * 0.94f);
            canvas.drawPath(star, paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(BLOOD);
            canvas.drawRect(b.left, b.bottom - Math.max(1f, density), b.right, b.bottom, paint);
        }

        @Override
        public void setAlpha(int alpha) {
        }

        @Override
        public void setColorFilter(ColorFilter cf) {
        }

        @Override
        public int getOpacity() {
            return PixelFormat.OPAQUE;
        }
    }

    /**
     * Blood that drips under the top bar. It never catches touches.
     */
    public static class BloodDripView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private int builtFor = -1;

        public BloodDripView(Context context) {
            super(context);
            paint.setColor(BLOOD);
            setClickable(false);
            setFocusable(false);
        }

        private void build(int w) {
            float d = getResources().getDisplayMetrics().density;
            Random rnd = new Random(666);
            path.reset();
            float band = 4 * d;
            path.moveTo(0, 0);
            path.lineTo(w, 0);
            path.lineTo(w, band);
            float x = w;
            //from right to left: small waves and some long drops
            while (x > 0) {
                float step = (14 + rnd.nextFloat() * 34) * d;
                float nx = Math.max(0, x - step);
                boolean drop = rnd.nextInt(3) == 0;
                float len = drop ? (16 + rnd.nextFloat() * 36) * d : (3 + rnd.nextFloat() * 7) * d;
                float mid = (x + nx) / 2f;
                float half = Math.max(2.5f * d, Math.min(step / 2f, (drop ? 3.2f : 6f) * d));
                path.lineTo(mid + half * 1.6f, band);
                path.cubicTo(mid + half, band + len * 0.5f, mid + half, band + len, mid, band + len);
                path.cubicTo(mid - half, band + len, mid - half, band + len * 0.5f, mid - half * 1.6f, band);
                path.lineTo(nx, band);
                x = nx;
            }
            path.lineTo(0, 0);
            path.close();
            builtFor = w;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (getWidth() != builtFor) {
                build(getWidth());
            }
            canvas.drawPath(path, paint);
        }
    }

    // ------------------------------------------------------------------ the screens

    /**
     * The screen of the category: black top bar with the gothic name, the sections in Cinzel, blood under the bar
     * and the night behind the pages.
     */
    public static void styleCategoryScreen(Activity activity, View topBar, info.hoang8f.android.segmented.SegmentedGroup group,
                                           RadioButton[] buttons, TextView title, View pager, View shadow) {
        if (!isOn()) return;
        Context c = activity;
        try {
            Window window = activity.getWindow();
            if (Build.VERSION.SDK_INT >= 21) {
                window.setStatusBarColor(BLACK);
            }
            if (Build.VERSION.SDK_INT >= 23) {
                View decor = window.getDecorView();
                decor.setSystemUiVisibility(decor.getSystemUiVisibility() & ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
            }
        } catch (Exception ignored) {
        }
        topBar.setBackground(barBackground(c, false));
        group.setTintColor(BLOOD, INK);
        ColorStateList text = new ColorStateList(
                new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
                new int[]{INK, 0xFFD8CFCB});
        for (RadioButton b : buttons) {
            b.setTypeface(cinzel(c));
            b.setTextColor(text);
            b.setAllCaps(true);
            b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            if (Build.VERSION.SDK_INT >= 21) {
                b.setLetterSpacing(0.12f);
            }
        }
        gothic(title, 30, INK);
        title.setIncludeFontPadding(false);
        pager.setBackground(new NightDrawable(c, true));
        if (shadow != null) {
            shadow.setVisibility(View.GONE);
        }
        //the blood, just under the bar
        ViewGroup parent = (ViewGroup) topBar.getParent();
        if (parent instanceof RelativeLayout) {
            BloodDripView drip = new BloodDripView(c);
            RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, Math.round(dp(c, 60)));
            lp.addRule(RelativeLayout.BELOW, topBar.getId());
            parent.addView(drip, lp);
        }
    }

    /**
     * A bottom bar of the category (Entries, Calendar): black, red line on top, light icons, Cinzel letters.
     */
    public static void styleBottomBar(View bar, View shadow) {
        if (!isOn() || bar == null) return;
        bar.setBackground(barBackground(bar.getContext(), true));
        if (shadow != null) {
            shadow.setVisibility(View.GONE);
        }
        if (bar instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) bar;
            for (int i = 0; i < g.getChildCount(); i++) {
                View v = g.getChildAt(i);
                if (v instanceof ImageView) {
                    tint(v, BONE);
                } else if (v instanceof TextView) {
                    TextView tv = (TextView) v;
                    tv.setTypeface(cinzel(tv.getContext()));
                    tv.setTextColor(INK);
                    tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
                }
            }
        }
    }

    /**
     * The card of an entry (layout rv_entries_item_esoteric).
     */
    public static void styleCard(View root) {
        Context c = root.getContext();
        View card = root.findViewById(R.id.RL_entries_item_content);
        if (card != null) card.setBackground(cardBackground(c));
        label((TextView) root.findViewById(R.id.TV_entries_item_header), 14, RED_TEXT, 0.4f);
        TextView day = (TextView) root.findViewById(R.id.TV_entries_item_eso_day);
        if (day != null) {
            day.setTypeface(cormorantBold(c));
            day.setTextColor(INK);
            day.setIncludeFontPadding(false);
        }
        label((TextView) root.findViewById(R.id.TV_entries_item_eso_weekday), 11, RED_TEXT, 0.2f);
        label((TextView) root.findViewById(R.id.TV_entries_item_time), 11, GRAY, 0.12f);
        TextView title = (TextView) root.findViewById(R.id.TV_entries_item_title);
        if (title != null) {
            title.setTypeface(cormorantBold(c));
            title.setTextColor(INK);
            title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        }
        TextView snippet = (TextView) root.findViewById(R.id.TV_entries_item_snippet);
        if (snippet != null) {
            snippet.setTypeface(cormorantItalic(c));
            snippet.setTextColor(GRAY_TEXT);
        }
        tint(root.findViewById(R.id.IV_entries_item_weather), 0xFFC9C0BC);
        tint(root.findViewById(R.id.IV_entries_item_bookmark), RED_TEXT);
        tint(root.findViewById(R.id.IV_entries_item_attachment), 0xFFC9C0BC);
    }

    /**
     * The day of the entry in the card: "13" and "VIE".
     */
    public static void bindCardDay(View root, Calendar calendar) {
        TextView day = (TextView) root.findViewById(R.id.TV_entries_item_eso_day);
        TextView weekday = (TextView) root.findViewById(R.id.TV_entries_item_eso_weekday);
        if (day == null) return;
        day.setText(String.format(Locale.US, "%02d", calendar.get(Calendar.DAY_OF_MONTH)));
        String wd = calendar.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.SHORT, Locale.getDefault());
        if (wd != null) {
            wd = wd.replace(".", "");
            if (wd.length() > 3) wd = wd.substring(0, 3);
            weekday.setText(wd.toUpperCase(Locale.getDefault()));
        }
    }

    /**
     * The page of an entry, reading or writing (layout fragment_diary).
     */
    public static void stylePage(View root) {
        if (!isOn() || root == null) return;
        Context c = root.getContext();
        View page = root.findViewById(R.id.RL_diary_page);
        if (page != null) page.setBackground(new BlackPageDrawable(c));
        View content = root.findViewById(R.id.RL_diary_content);
        if (content != null) {
            content.setBackgroundColor(Color.TRANSPARENT);
            int side = Math.round(dp(c, 20));
            content.setPadding(side, content.getPaddingTop(), side, content.getPaddingBottom());
        }
        View info = root.findViewById(R.id.RL_diary_info);
        if (info != null) info.setBackground(new CoverDrawable(c));

        TextView month = (TextView) root.findViewById(R.id.TV_diary_month);
        if (month != null) {
            month.setVisibility(View.VISIBLE);
            label(month, 13, RED_TEXT, 0.5f);
        }
        TextView date = (TextView) root.findViewById(R.id.TV_diary_date);
        if (date != null) {
            date.setTypeface(cormorantBold(c));
            date.setTextSize(TypedValue.COMPLEX_UNIT_SP, 76);
            date.setTextColor(INK);
            date.setShadowLayer(dp(c, 16), 0, 0, 0xCCFF1E32);
        }
        View dayAndTime = root.findViewById(R.id.LL_diary_day_and_time);
        if (dayAndTime != null) dayAndTime.setVisibility(View.VISIBLE);
        label((TextView) root.findViewById(R.id.TV_diary_day), 12, BONE, 0.25f);
        label((TextView) root.findViewById(R.id.TV_diary_time), 12, BONE, 0.25f);
        gothic((TextView) root.findViewById(R.id.TV_diary_cover_title), 28, INK);

        for (int id : new int[]{R.id.TV_diary_weather, R.id.TV_diary_location}) {
            TextView tv = (TextView) root.findViewById(id);
            if (tv != null) {
                tv.setTypeface(cormorant(c));
                tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
                tv.setTextColor(0xFFC9C0BC);
            }
        }
        tint(root.findViewById(R.id.IV_diary_weather), 0xFFC9C0BC);
        tint(root.findViewById(R.id.IV_diary_location_name_icon), 0xFFC9C0BC);
        tint(root.findViewById(R.id.IV_diary_close_dialog), BONE);
        for (int id : new int[]{R.id.TV_diary_cover_tag, R.id.TV_diary_cover_tag_more}) {
            TextView tv = (TextView) root.findViewById(id);
            if (tv != null) {
                label(tv, 11, RED_SOFT, 0.14f);
                tv.setBackground(outline(c, 0xFF7A1A28));
                int px = Math.round(dp(c, 8)), py = Math.round(dp(c, 3));
                tv.setPadding(px, py, px, py);
            }
        }
        tint(root.findViewById(R.id.IV_diary_cover_tag_add), RED_SOFT);

        TextView titleEdit = (TextView) root.findViewById(R.id.EDT_diary_title);
        if (titleEdit != null) {
            titleEdit.setTypeface(fraktur(c));
            titleEdit.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);
            titleEdit.setTextColor(INK);
            titleEdit.setHintTextColor(HINT);
            ViewCompat.setBackgroundTintList(titleEdit, ColorStateList.valueOf(BLOOD));
        }
        gothic((TextView) root.findViewById(R.id.TV_diary_title_content), 30, INK);
        TextView hint = (TextView) root.findViewById(R.id.TV_diary_item_content_hint);
        if (hint != null) {
            hint.setTypeface(cormorantItalic(c));
            hint.setTextColor(HINT);
        }

        View bar = root.findViewById(R.id.LL_diary_edit_bar);
        if (bar != null) {
            bar.setBackground(barBackground(c, true));
            if (bar instanceof ViewGroup) {
                ViewGroup g = (ViewGroup) bar;
                for (int i = 0; i < g.getChildCount(); i++) {
                    tint(g.getChildAt(i), BONE);
                }
            }
        }
        View save = root.findViewById(R.id.IV_diary_save);
        if (save instanceof ImageView) {
            //"Seal": the save button is a red seal
            GradientDrawable seal = new GradientDrawable();
            seal.setShape(GradientDrawable.OVAL);
            seal.setColor(BLOOD);
            seal.setStroke(Math.round(dp(c, 1)), RED_GLOW);
            save.setBackground(seal);
            ((ImageView) save).setColorFilter(INK);
            save.setContentDescription(c.getString(R.string.esoteric_seal));
        }
        View shadow = root.findViewById(R.id.diary_edit_bar_shadow);
        if (shadow != null) shadow.setVisibility(View.GONE);
    }

    /**
     * The date of the cover: "NOVIEMBRE", a big "13" and "VIERNES · 03:00".
     */
    public static void coverDate(TextView month, TextView day, Calendar calendar) {
        if (!isOn() || month == null) return;
        month.setText(month.getText().toString().toUpperCase(Locale.getDefault()));
        View parent = (View) month.getParent();
        TextView date = (TextView) parent.findViewById(R.id.TV_diary_date);
        if (date != null) {
            date.setText(String.valueOf(calendar.get(Calendar.DAY_OF_MONTH)));
        }
        if (day != null) {
            day.setText(day.getText().toString().toUpperCase(Locale.getDefault()) + "  ·  ");
        }
    }

    /**
     * A text box of the entry: white ink, Cormorant letters, a bit bigger.
     */
    public static void styleText(EditText editText) {
        Context c = editText.getContext();
        editText.setTypeface(cormorant(c));
        editText.setTextColor(INK_SOFT);
        editText.setHintTextColor(HINT);
        editText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        editText.setLineSpacing(0f, 1.25f);
        editText.setLinkTextColor(RED_TEXT);
        if (Build.VERSION.SDK_INT >= 29) {
            //A red cursor
            GradientDrawable cursor = new GradientDrawable();
            cursor.setColor(RED_GLOW);
            cursor.setSize(Math.round(dp(c, 2)), 1);
            editText.setTextCursorDrawable(cursor);
        }
    }

    public static void styleCheckBox(CompoundButton box) {
        if (!isOn() || box == null) return;
        CompoundButtonCompat.setButtonTintList(box, new ColorStateList(
                new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
                new int[]{BLOOD, HINT}));
    }

    // ------------------------------------------------------------------ the gothic first letter

    /**
     * The first letter of a text, big, gothic and red (only to see it: nothing of this is saved).
     */
    private static class DropCapSpan extends MetricAffectingSpan {
        private final Typeface face;

        DropCapSpan(Typeface face) {
            this.face = face;
        }

        @Override
        public void updateDrawState(TextPaint tp) {
            apply(tp);
            tp.setColor(RED_GLOW);
            tp.setShadowLayer(14, 0, 0, 0x99FF1E32);
        }

        @Override
        public void updateMeasureState(TextPaint tp) {
            apply(tp);
        }

        private void apply(TextPaint tp) {
            tp.setTypeface(face);
        }
    }

    /**
     * Puts (or takes away when writing) the gothic red first letter on the first text of the entry.
     */
    public static void dropCap(LinearLayout items, boolean writing) {
        if (!isOn() || items == null) return;
        boolean first = true;
        for (int i = 0; i < items.getChildCount(); i++) {
            View v = items.getChildAt(i);
            if (!(v instanceof EditText)) {
                continue;
            }
            CharSequence cs = ((EditText) v).getText();
            if (!(cs instanceof Spannable)) continue;
            Spannable text = (Spannable) cs;
            for (Object s : text.getSpans(0, text.length(), DropCapSpan.class)) text.removeSpan(s);
            for (Object s : text.getSpans(0, text.length(), DropMarker.class)) text.removeSpan(s);
            if (first && !writing && text.length() > 1 && Character.isLetter(text.charAt(0))) {
                Context c = v.getContext();
                text.setSpan(new DropCapSpan(fraktur(c)), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                text.setSpan(new DropMarker(2.6f), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            first = false;
        }
    }

    private static class DropMarker extends RelativeSizeSpan {
        DropMarker(float size) {
            super(size);
        }
    }
}
