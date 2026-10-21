package com.kiminonawa.mydiary.shared;

import android.content.Context;
import android.content.DialogInterface;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.Handler;
import android.os.Looper;
import android.support.v7.app.AlertDialog;
import android.text.InputFilter;
import android.text.InputType;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;

import com.kiminonawa.mydiary.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Puts a name to the place where the phone is, using only what Android has built in
 * (no Google Places library, no internet service of our own).
 * <p>
 * 1. The phone's own address service (Geocoder) gives "City, Region, Country".
 * 2. If the phone can't do that, the coordinates are written instead.
 * 3. The person can always type the name of the place by hand.
 */
public class LocationHelper {

    public interface NameCallback {
        /**
         * @param name         the text to show as the place (never empty)
         * @param isAddress    true if it is a real name, false if only coordinates could be found
         */
        void onName(String name, boolean isAddress);
    }

    public interface TextCallback {
        void onText(String text);
    }

    private static final int MAX_NAME_LENGTH = 60;

    /**
     * Looks for the name of the place in the background; the answer comes back in the main thread.
     */
    public static void findPlaceName(Context context, final Location location, final NameCallback callback) {
        final Context appContext = context.getApplicationContext();
        final Handler mainHandler = new Handler(Looper.getMainLooper());
        new Thread(new Runnable() {
            @Override
            public void run() {
                String name = null;
                try {
                    if (Geocoder.isPresent()) {
                        Geocoder geocoder = new Geocoder(appContext, Locale.getDefault());
                        List<Address> addresses = geocoder.getFromLocation(
                                location.getLatitude(), location.getLongitude(), 1);
                        if (addresses != null && addresses.size() > 0) {
                            name = buildName(addresses.get(0));
                        }
                    }
                } catch (Exception e) {
                    //No network or no address service: the coordinates are used
                    e.printStackTrace();
                }
                final boolean isAddress = name != null && name.length() > 0;
                final String result = isAddress ? name : formatCoordinates(location);
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        callback.onName(result, isAddress);
                    }
                });
            }
        }).start();
    }

    /**
     * "City, Region, Country" without empty or repeated parts.
     */
    static String buildName(Address address) {
        List<String> parts = new ArrayList<>();
        String city = address.getLocality();
        if (city == null || city.length() == 0) {
            city = address.getSubAdminArea();
        }
        addPart(parts, city);
        addPart(parts, address.getAdminArea());
        addPart(parts, address.getCountryName());
        if (parts.isEmpty()) {
            addPart(parts, address.getFeatureName());
        }
        StringBuilder name = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) {
                name.append(", ");
            }
            name.append(parts.get(i));
        }
        return name.toString();
    }

    private static void addPart(List<String> parts, String part) {
        if (part == null) {
            return;
        }
        part = part.trim();
        if (part.length() == 0) {
            return;
        }
        if (!parts.isEmpty() && parts.get(parts.size() - 1).equalsIgnoreCase(part)) {
            return;
        }
        parts.add(part);
    }

    static String formatCoordinates(Location location) {
        return String.format(Locale.US, "%.4f, %.4f", location.getLatitude(), location.getLongitude());
    }

    /**
     * A small window to type the name of the place by hand.
     *
     * @param current text already written (can be empty)
     */
    public static void showEditDialog(Context context, String current, final TextCallback callback) {
        final EditText input = new EditText(context);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setSingleLine(true);
        input.setHint(R.string.location_edit_hint);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(MAX_NAME_LENGTH)});
        if (current != null) {
            input.setText(current);
            input.setSelection(input.getText().length());
        }
        int margin = ScreenHelper.dpToPixel(context.getResources(), 20);
        FrameLayout container = new FrameLayout(context);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        params.leftMargin = margin;
        params.rightMargin = margin;
        container.addView(input, params);

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(R.string.location_edit_title)
                .setView(container)
                .setPositiveButton(R.string.dialog_button_ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int which) {
                        callback.onText(input.getText().toString().trim());
                    }
                })
                .setNegativeButton(R.string.dialog_button_cancel, null)
                .create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        }
        dialog.show();
    }
}
