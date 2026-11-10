package ru.b1sted.altshift.service;

import android.app.NotificationManager;
import android.content.Context;
import android.util.Log;

/**
 * Single source of truth for notification channel identifiers and legacy cleanup.
 */
public final class NotificationChannels {

    public static final String KEYBOARD_STATE = "keyboard_state_v3";
    public static final String KEYBOARD_WARNING = "warning_channel_v2";

    private static final String TAG = "AltShift";
    private static final String[] LEGACY_IDS = {
            "auto_switch",
            "keyboard_state",
            "keyboard_state_v2",
            "warning_channel"
    };

    private NotificationChannels() {
    }

    public static void deleteLegacy(Context context) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) {
            return;
        }

        try {
            for (int index = 0; index < LEGACY_IDS.length; index++) {
                manager.deleteNotificationChannel(LEGACY_IDS[index]);
            }
        } catch (RuntimeException exception) {
            Log.w(TAG, "Unable to delete legacy notification channels", exception);
        }
    }
}
