package com.kiminonawa.mydiary.entries.diary.area;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.exifinterface.media.ExifInterface;

import com.kiminonawa.mydiary.R;
import com.kiminonawa.mydiary.shared.BackgroundTask;
import com.kiminonawa.mydiary.shared.ThemeManager;

/**
 * Choose what part of a photo is seen. The photo file is not changed: the answer is only the rectangle
 * (see {@link PhotoArea}).
 */
public class AreaPickerActivity extends AppCompatActivity {

    /**
     * Complete path of the photo file.
     */
    public static final String EXTRA_PATH = "area_path";
    /**
     * Width / height of the frame (float).
     */
    public static final String EXTRA_RATIO = "area_ratio";
    /**
     * The title of the screen.
     */
    public static final String EXTRA_TITLE = "area_title";
    /**
     * In: the area chosen before ("l,t,r,b"), if any. Out: the area chosen ("l,t,r,b"), or "" for the center.
     */
    public static final String EXTRA_AREA = "area_rect";

    private static final int MAX_SIDE = 2048;

    private AreaPickerView areaPickerView;
    private boolean photoReady = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_area_picker);
        int mainColor = ThemeManager.getInstance().getThemeMainColor(this);
        getWindow().setStatusBarColor(ThemeManager.getInstance().getThemeDarkColor(this));
        TextView title = (TextView) findViewById(R.id.TV_area_title);
        title.setBackgroundColor(mainColor);
        title.setText(getIntent().getStringExtra(EXTRA_TITLE));
        areaPickerView = (AreaPickerView) findViewById(R.id.APV_area);

        Button cancel = (Button) findViewById(R.id.But_area_cancel);
        Button center = (Button) findViewById(R.id.But_area_center);
        Button done = (Button) findViewById(R.id.But_area_done);
        done.setBackgroundColor(mainColor);
        done.setTextColor(0xFFFFFFFF);
        cancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setResult(Activity.RESULT_CANCELED);
                finish();
            }
        });
        center.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                areaPickerView.resetToCenter();
            }
        });
        done.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!photoReady) {
                    return;
                }
                Intent result = new Intent();
                result.putExtra(EXTRA_AREA, PhotoArea.formatRect(areaPickerView.getArea()));
                setResult(Activity.RESULT_OK, result);
                finish();
            }
        });

        final float ratio = getIntent().getFloatExtra(EXTRA_RATIO, 1f);
        final RectF initial = PhotoArea.parseRect(getIntent().getStringExtra(EXTRA_AREA));
        new LoadPhotoTask(getIntent().getStringExtra(EXTRA_PATH)) {
            @Override
            protected void onPostExecute(Bitmap bitmap) {
                if (bitmap == null) {
                    Toast.makeText(AreaPickerActivity.this, R.string.toast_area_fail, Toast.LENGTH_LONG).show();
                    setResult(Activity.RESULT_CANCELED);
                    finish();
                    return;
                }
                photoReady = true;
                areaPickerView.setPhoto(bitmap, ratio, initial);
            }
        }.execute();
    }

    /**
     * Reads the photo (not bigger than the screen needs) and turns it the right way up.
     */
    private abstract static class LoadPhotoTask extends BackgroundTask<Void, Bitmap> {
        private final String path;

        LoadPhotoTask(String path) {
            this.path = path;
        }

        @Override
        protected Bitmap doInBackground(Void... params) {
            if (path == null) {
                return null;
            }
            try {
                BitmapFactory.Options bounds = new BitmapFactory.Options();
                bounds.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(path, bounds);
                int sample = 1;
                while (Math.max(bounds.outWidth, bounds.outHeight) / sample > MAX_SIDE) {
                    sample *= 2;
                }
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inSampleSize = sample;
                Bitmap bitmap = BitmapFactory.decodeFile(path, options);
                if (bitmap == null) {
                    return null;
                }
                int degrees = 0;
                try {
                    int orientation = new ExifInterface(path).getAttributeInt(
                            ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
                    if (orientation == ExifInterface.ORIENTATION_ROTATE_90) {
                        degrees = 90;
                    } else if (orientation == ExifInterface.ORIENTATION_ROTATE_180) {
                        degrees = 180;
                    } else if (orientation == ExifInterface.ORIENTATION_ROTATE_270) {
                        degrees = 270;
                    }
                } catch (Exception ignored) {
                    //No rotation information: the photo is shown as it is
                }
                if (degrees != 0) {
                    Matrix matrix = new Matrix();
                    matrix.postRotate(degrees);
                    Bitmap rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
                    if (rotated != bitmap) {
                        bitmap.recycle();
                    }
                    bitmap = rotated;
                }
                return bitmap;
            } catch (Exception | OutOfMemoryError e) {
                return null;
            }
        }
    }
}
