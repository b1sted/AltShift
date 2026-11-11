package ru.b1sted.altshift.util;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

import androidx.core.content.ContextCompat;

/**
 * Defensive wrappers for permissions that OEM ROMs may reject or throw from.
 */
public final class PermissionHelper {

    private static final String TAG = "AltShift";

    private PermissionHelper() {
    }

    public static boolean canDrawOverlays(Context context) {
        if (context == null) {
            return false;
        }

        try {
            return Settings.canDrawOverlays(context);
        } catch (RuntimeException exception) {
            Log.w(TAG, "Unable to verify the overlay permission", exception);

            return false;
        }
    }

    public static boolean hasNotificationPermission(Context context) {
        if (context == null) {
            return false;
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return true;
        }

        try {
            return ContextCompat.checkSelfPermission(
                    context, Manifest.permission.POST_NOTIFICATIONS)
                    == PackageManager.PERMISSION_GRANTED;
        } catch (RuntimeException exception) {
            Log.w(TAG, "Unable to verify the notification permission", exception);

            return false;
        }
    }
}
