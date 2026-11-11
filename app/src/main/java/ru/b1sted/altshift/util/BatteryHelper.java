package ru.b1sted.altshift.util;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;

/**
 * Resolves the battery optimization exemption used to survive Doze mode.
 */
public final class BatteryHelper {

    private static final String TAG = "AltShift";

    private BatteryHelper() {
    }

    public static boolean isIgnoringBatteryOptimizations(Context context) {
        if (context == null) {
            return false;
        }

        PowerManager powerManager = context.getSystemService(PowerManager.class);
        if (powerManager == null) {
            Log.w(TAG, "PowerManager is unavailable");

            return false;
        }

        try {
            return powerManager.isIgnoringBatteryOptimizations(context.getPackageName());
        } catch (RuntimeException exception) {
            Log.w(TAG, "Unable to read the battery optimization state", exception);

            return false;
        }
    }

    @SuppressLint("BatteryLife")
    public static boolean requestIgnoreBatteryOptimizations(Context context) {
        if (context == null) {
            return false;
        }

        Intent packageIntent = new Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:" + context.getPackageName()));
        if (tryStartActivity(context, packageIntent)) {
            return true;
        }

        Intent settingsIntent = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
        if (tryStartActivity(context, settingsIntent)) {
            return true;
        }

        Log.w(TAG, "No activity can handle battery optimization settings");

        return false;
    }

    private static boolean tryStartActivity(Context context, Intent intent) {
        if (!(context instanceof Activity)) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }

        try {
            context.startActivity(intent);

            return true;
        } catch (RuntimeException exception) {
            Log.w(TAG, "Cannot resolve activity for intent: " + intent, exception);

            return false;
        }
    }
}
