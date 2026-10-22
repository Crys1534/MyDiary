package com.kiminonawa.mydiary.entries.diary.editor;

import android.app.ProgressDialog;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.fragment.app.Fragment;

import com.kiminonawa.mydiary.R;
import com.kiminonawa.mydiary.shared.LocationHelper;
import com.kiminonawa.mydiary.shared.PermissionHelper;

import java.util.List;

import static android.content.Context.LOCATION_SERVICE;

/**
 * The place of an entry (the pin and the text on the cover).
 * <p>
 * Reading: the pin and the text are only there when the entry has a place.
 * Writing: the text ("No location" when empty) can be tapped; a small window lets the person type the place or press
 * "Use my location" to let the phone find it (the permission is asked, and the name comes from the address service
 * of Android). It is used by the page that writes a new entry and by the window that edits an old one.
 */
public class LocationField {

    private static final int GPS_TIMEOUT = 20 * 1000;

    private final Fragment host;
    private final TextView text;
    private final View pin;
    private final String noLocation;
    private final boolean editable;
    private boolean hasLocation = false;

    private LocationManager locationManager;
    private ProgressDialog progressDialog;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Location fix = null;
    private boolean permissionJustGranted = false;

    /**
     * @param editable true when the person writes the entry (the text can be tapped)
     */
    public LocationField(Fragment host, View rootView, boolean editable) {
        this.host = host;
        this.editable = editable;
        this.text = (TextView) rootView.findViewById(R.id.TV_diary_location);
        this.pin = rootView.findViewById(R.id.IV_diary_location_name_icon);
        this.noLocation = host.getString(R.string.diary_no_location);
        if (editable) {
            locationManager = (LocationManager) host.getActivity().getSystemService(LOCATION_SERVICE);
            progressDialog = new ProgressDialog(host.getActivity());
            progressDialog.setMessage(host.getString(R.string.process_dialog_loading));
            progressDialog.setCancelable(false);
            progressDialog.setProgressStyle(android.R.style.Widget_ProgressBar);
            text.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showWriteDialog();
                }
            });
        }
        refresh();
    }

    /**
     * @return the place, or "" when the entry has none
     */
    public String getName() {
        String name = text.getText().toString();
        return hasLocation && !noLocation.equals(name) ? name : "";
    }

    public void setName(String name) {
        hasLocation = name != null && name.length() > 0;
        text.setText(hasLocation ? name : noLocation);
        refresh();
    }

    private void refresh() {
        if (!hasLocation) {
            text.setText(noLocation);
        }
        //Only reading: without a place there is no pin and no "No location" on the cover.
        //Writing: they stay, the person can tap the text to write a place.
        int visibility = (editable || hasLocation) ? View.VISIBLE : View.GONE;
        pin.setVisibility(visibility);
        text.setVisibility(visibility);
    }

    private void showWriteDialog() {
        LocationHelper.showEditDialog(host.getActivity(), getName(), new LocationHelper.TextCallback() {
            @Override
            public void onText(String name) {
                setName(name);
            }
        }, new Runnable() {
            @Override
            public void run() {
                useMyLocation();
            }
        });
    }

    /**
     * Asks the phone where it is (after asking the permission if needed).
     */
    public void useMyLocation() {
        if (!PermissionHelper.checkPermission(host, PermissionHelper.REQUEST_ACCESS_FINE_LOCATION_PERMISSION)) {
            return;
        }
        if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                || locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            startListening();
        } else {
            Toast.makeText(host.getActivity(), host.getString(R.string.toast_location_not_open), Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Call it from onRequestPermissionsResult of the fragment.
     *
     * @return true when the answer was about the location
     */
    public boolean onRequestPermissionsResult(int requestCode, int[] grantResults) {
        if (requestCode != PermissionHelper.REQUEST_ACCESS_FINE_LOCATION_PERMISSION) {
            return false;
        }
        //The exact or the approximate location are both fine
        if (grantResults.length > 0 && PermissionHelper.checkAnyPermissionResult(grantResults)) {
            permissionJustGranted = true;
        } else {
            PermissionHelper.showAccessDialog(host.getActivity());
        }
        return true;
    }

    /**
     * Call it from onResume of the fragment (the permission window has just closed).
     */
    public void onResume() {
        if (permissionJustGranted) {
            permissionJustGranted = false;
            startListening();
        }
    }

    /**
     * Call it from onStop of the fragment.
     */
    public void onStop() {
        stopListening();
        handler.removeCallbacksAndMessages(null);
        if (progressDialog != null) {
            progressDialog.dismiss();
        }
    }

    private void startListening() {
        progressDialog.show();
        boolean started = false;
        List<String> providers = locationManager.getAllProviders();
        String[] kinds = {LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER};
        for (String kind : kinds) {
            if (providers.contains(kind)) {
                try {
                    locationManager.requestLocationUpdates(kind, 0, 0, locationListener);
                    started = true;
                } catch (SecurityException e) {
                    //This kind of location is not allowed (for example, the person allowed only the approximate one)
                    e.printStackTrace();
                } catch (IllegalArgumentException e) {
                    e.printStackTrace();
                }
            }
        }
        if (started) {
            //Waiting for the location: max timeout
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    onFixed();
                }
            }, GPS_TIMEOUT);
        } else {
            progressDialog.dismiss();
            Toast.makeText(host.getActivity(), host.getString(R.string.toast_location_not_open), Toast.LENGTH_LONG).show();
        }
    }

    private void stopListening() {
        if (locationManager == null) {
            return;
        }
        try {
            locationManager.removeUpdates(locationListener);
        } catch (SecurityException e) {
            e.printStackTrace();
        }
    }

    /**
     * Main thread: the phone found where it is, or the waiting time ended.
     */
    private void onFixed() {
        stopListening();
        handler.removeCallbacksAndMessages(null);
        final Location location = fix;
        fix = null;
        if (!host.isAdded()) {
            progressDialog.dismiss();
            return;
        }
        if (location == null) {
            Toast.makeText(host.getActivity(), host.getString(R.string.toast_location_timeout), Toast.LENGTH_LONG).show();
            progressDialog.dismiss();
            return;
        }
        LocationHelper.findPlaceName(host.getActivity(), location, new LocationHelper.NameCallback() {
            @Override
            public void onName(String name, boolean isAddress) {
                progressDialog.dismiss();
                if (!host.isAdded()) {
                    return;
                }
                if (!isAddress) {
                    Toast.makeText(host.getActivity(), host.getString(R.string.toast_geocoder_fail), Toast.LENGTH_LONG).show();
                }
                setName(name);
            }
        });
    }

    private final LocationListener locationListener = new LocationListener() {
        @Override
        public void onLocationChanged(Location location) {
            fix = new Location(location);
            onFixed();
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {
        }

        @Override
        public void onProviderEnabled(String provider) {
        }

        @Override
        public void onProviderDisabled(String provider) {
        }
    };
}
