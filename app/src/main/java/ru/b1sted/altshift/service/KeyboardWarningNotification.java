package ru.b1sted.altshift.service;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.provider.Settings;
import android.util.Log;

import ru.b1sted.altshift.R;
import ru.b1sted.altshift.ui.LocaleController;
import ru.b1sted.altshift.util.PermissionHelper;

/**
 * Warns that AltShift is the only enabled keyboard. Detaching the hardware
 * keyboard in that state would leave the user without on-screen input.
 */
public final class KeyboardWarningNotification {

    private static final String TAG = "AltShift";
    private static final int NOTIFICATION_ID = 2001;
    private static final int REQUEST_CODE = 2002;

    private KeyboardWarningNotification() {
    }

    /**
     * Posts an audible warning notification. Returns true when the notification
     * was handed to the system.
     */
    public static boolean show(Context context) {
        if (context == null) {
            return false;
        }

        if (!PermissionHelper.hasNotificationPermission(context)) {
            Log.w(TAG, "Notification permission is not granted");

            return false;
        }

        try {
            NotificationManager manager =
                    (NotificationManager) context.getSystemService(
                            Context.NOTIFICATION_SERVICE);
            if (manager == null) {
                return false;
            }

            if (!manager.areNotificationsEnabled()) {
                Log.w(TAG, "Notifications are disabled for the application");

                return false;
            }

            Context localized = LocaleController.localizedContext(context);
            PendingIntent pendingIntent = createSettingsIntent(context);
            Notification.Builder builder =
                    new Notification.Builder(localized, NotificationChannels.SAFETY_WARNINGS);
            builder.setSmallIcon(R.drawable.ic_notification_keyboard)
                    .setContentTitle(localized.getString(
                            R.string.notification_no_keyboard_title))
                    .setContentText(localized.getString(
                            R.string.notification_no_keyboard_text))
                    .setContentIntent(pendingIntent)
                    .setOngoing(true)
                    .setOnlyAlertOnce(true)
                    .setShowWhen(false)
                    .setCategory(Notification.CATEGORY_STATUS)
                    .setPriority(Notification.PRIORITY_HIGH)
                    .setDefaults(Notification.DEFAULT_ALL)
                    .addAction(new Notification.Action.Builder(
                            Icon.createWithResource(localized, R.drawable.ic_notification_keyboard),
                            localized.getString(R.string.notification_keyboard_settings),
                            pendingIntent).build());

            manager.notify(NOTIFICATION_ID, builder.build());

            return true;
        } catch (RuntimeException exception) {
            Log.w(TAG, "Unable to post the single-keyboard warning", exception);

            return false;
        }
    }

    public static void cancel(Context context) {
        if (context == null) {
            return;
        }

        try {
            NotificationManager manager =
                    (NotificationManager) context.getSystemService(
                            Context.NOTIFICATION_SERVICE);
            if (manager != null) {
                manager.cancel(NOTIFICATION_ID);
            }
        } catch (RuntimeException exception) {
            Log.w(TAG, "Unable to cancel the single-keyboard warning", exception);
        }
    }

    private static PendingIntent createSettingsIntent(Context context) {
        Intent intent = new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        return PendingIntent.getActivity(
                context,
                REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
