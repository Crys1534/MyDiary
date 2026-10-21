package com.kiminonawa.mydiary.shared;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.appcompat.app.AlertDialog;

import com.kiminonawa.mydiary.R;

/**
 * Created by daxia on 2016/11/23.
 * <p>
 * Only the location needs a permission now. The photos are taken by the camera app of the phone,
 * chosen with the picker of Android and saved in the private folder of this app,
 * so no camera / storage permission is needed (and Android 10+ does not give them anyway).
 */

public class PermissionHelper {


    /**
     * Permission
     */
    public static final int REQUEST_ACCESS_FINE_LOCATION_PERMISSION = 1;
    public static final int REQUEST_CAMERA_AND_WRITE_ES_PERMISSION = 2;
    public static final int REQUEST_WRITE_ES_PERMISSION = 3;

    /**
     * @return true when the feature can be used right now. When false, the permission was asked
     * and the answer comes in onRequestPermissionsResult.
     */
    public static boolean checkPermission(Fragment fragment, final int requestCode) {
        switch (requestCode) {
            case REQUEST_ACCESS_FINE_LOCATION_PERMISSION:
                if (!hasLocationPermission(fragment.getActivity())) {
                    //Both are asked: since Android 12 the person can allow only the approximate one
                    fragment.requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION},
                            requestCode);
                    return false;
                }
                break;
            default:
                //Camera and storage: nothing to ask (see the top of this class)
                break;
        }
        return true;
    }

    public static boolean checkPermission(Activity activity, final int requestCode) {
        //Nothing to ask (see the top of this class)
        return true;
    }

    /**
     * The exact location or the approximate one (any of them is enough to name the place).
     */
    public static boolean hasLocationPermission(Context context) {
        return ContextCompat.checkSelfPermission(context,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(context,
                Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * @return true if the person allowed at least one of the permissions that were asked.
     */
    public static boolean checkAnyPermissionResult(int[] grantResults) {
        for (int result : grantResults) {
            if (result == PackageManager.PERMISSION_GRANTED) {
                return true;
            }
        }
        return false;
    }

    public static boolean checkAllPermissionResult(int[] grantResults) {
        for (int result : grantResults) {
            if (result != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    public static void showAddPhotoDialog(Context context) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context)
                .setTitle(context.getString(R.string.diary_location_permission_title))
                .setMessage(context.getString(R.string.diary_photo_permission_content))
                .setPositiveButton(context.getString(R.string.dialog_button_ok), null);
        builder.show();
    }

    public static void showAccessDialog(Context context) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context)
                .setTitle(context.getString(R.string.diary_location_permission_title))
                .setMessage(context.getString(R.string.diary_location_permission_content))
                .setPositiveButton(context.getString(R.string.dialog_button_ok), null);
        builder.show();
    }

}
