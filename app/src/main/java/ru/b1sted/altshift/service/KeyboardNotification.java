package ru.b1sted.altshift.service;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.util.Log;

import ru.b1sted.altshift.R;
import ru.b1sted.altshift.ui.SwitchBackActivity;
import ru.b1sted.altshift.util.PermissionHelper;

/**
 * Posts a status notification when a deliberate switch attempt failed.
 */
public final class KeyboardNotification {

    private static final String TAG = "AltShift";
    private static final int NOTIFICATION_ID = 1001;
    private static final int REQUEST_CODE = 1002;

    private KeyboardNotification() {
    }

    public static void showInactive(Context context) {
        if (context == null) {
            return;
        }

        if (!PermissionHelper.hasNotificationPermission(context)) {
            Log.w(TAG, "Notification permission is not granted");

            return;
        }

        try {
            NotificationManager manager =
                    (NotificationManager) context.getSystemService(
                            Context.NOTIFICATION_SERVICE);
            if (manager == null) {
                return;
            }

            if (!manager.areNotificationsEnabled()) {
                Log.w(TAG, "Notifications are disabled for the application");

                return;
            }

            Intent intent = new Intent(context, SwitchBackActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);

            PendingIntent pendingIntent = PendingIntent.getActivity(
                    context,
                    REQUEST_CODE,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            Notification.Builder builder =
                    new Notification.Builder(context, NotificationChannels.KEYBOARD_ALERTS);
            builder.setSmallIcon(R.drawable.ic_notification_keyboard)
                    .setContentTitle(context.getString(
                            R.string.notification_inactive_title))
                    .setContentText(context.getString(
                            R.string.notification_inactive_text))
                    .setContentIntent(pendingIntent)
                    .setOngoing(true)
                    .setOnlyAlertOnce(true)
                    .setShowWhen(false)
                    .setCategory(Notification.CATEGORY_STATUS)
                    .setPriority(Notification.PRIORITY_HIGH)
                    .setDefaults(Notification.DEFAULT_ALL)
                    .addAction(new Notification.Action.Builder(
                            Icon.createWithResource(context, R.drawable.ic_notification_keyboard),
                            context.getString(R.string.notification_switch_back),
                            pendingIntent).build());

            manager.notify(NOTIFICATION_ID, builder.build());
        } catch (RuntimeException exception) {
            Log.w(TAG, "Unable to post keyboard notification", exception);
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
            Log.w(TAG, "Unable to cancel keyboard notification", exception);
        }
    }

    public static boolean canPost(Context context) {
        if (context == null || !PermissionHelper.hasNotificationPermission(context)) {
            return false;
        }

        try {
            NotificationManager manager =
                    (NotificationManager) context.getSystemService(
                            Context.NOTIFICATION_SERVICE);

            return manager != null && manager.areNotificationsEnabled();
        } catch (RuntimeException exception) {
            Log.w(TAG, "Unable to verify notification availability", exception);

            return false;
        }
    }
}
