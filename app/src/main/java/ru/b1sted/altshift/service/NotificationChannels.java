package ru.b1sted.altshift.service;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.util.Log;

import ru.b1sted.altshift.R;

/**
 * Single source of truth for notification channel identifiers and configuration.
 */
public final class NotificationChannels {

    public static final String KEYBOARD_ALERTS = "altshift_keyboard_alerts";
    public static final String SAFETY_WARNINGS = "altshift_safety_warnings";

    private static final String TAG = "AltShift";
    private static final String[] LEGACY_IDS = {
            "auto_switch",
            "keyboard_state",
            "keyboard_state_v2",
            "keyboard_state_v3",
            "warning_channel",
            "warning_channel_v2"
    };

    private NotificationChannels() {
    }

    public static void initialize(Context context) {
        if (context == null) {
            return;
        }

        try {
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            if (manager == null) {
                return;
            }

            deleteLegacy(manager);
            ensureKeyboardAlerts(context, manager);
            ensureSafetyWarnings(context, manager);
        } catch (RuntimeException exception) {
            Log.w(TAG, "Unable to initialize notification channels", exception);
        }
    }

    private static void deleteLegacy(NotificationManager manager) {
        for (int index = 0; index < LEGACY_IDS.length; index++) {
            manager.deleteNotificationChannel(LEGACY_IDS[index]);
        }
    }

    private static void ensureKeyboardAlerts(Context context, NotificationManager manager) {
        if (manager.getNotificationChannel(KEYBOARD_ALERTS) != null) {
            return;
        }

        NotificationChannel channel = new NotificationChannel(
                KEYBOARD_ALERTS,
                context.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription(context.getString(
                R.string.notification_channel_description));
        channel.enableVibration(true);

        manager.createNotificationChannel(channel);
    }

    private static void ensureSafetyWarnings(Context context, NotificationManager manager) {
        if (manager.getNotificationChannel(SAFETY_WARNINGS) != null) {
            return;
        }

        NotificationChannel channel = new NotificationChannel(
                SAFETY_WARNINGS,
                context.getString(R.string.notification_warning_channel_name),
                NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription(context.getString(
                R.string.notification_warning_channel_description));
        channel.enableVibration(true);

        manager.createNotificationChannel(channel);
    }
}
