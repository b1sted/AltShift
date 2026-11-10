package ru.b1sted.altshift.service;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

import androidx.core.content.ContextCompat;

import ru.b1sted.altshift.R;

/**
 * Warns that AltShift is the only enabled keyboard. Detaching the hardware
 * keyboard in that state would leave the user without on-screen input.
 */
public final class KeyboardWarningNotification {

    private static final String TAG = "AltShift";
    private static final String CHANNEL_ID = "warning_channel";
    private static final int NOTIFICATION_ID = 2001;
    private static final int REQUEST_CODE = 2002;

    private KeyboardWarningNotification() {
    }

    /**
     * Posts a silent warning notification. Returns true when the notification
     * was handed to the system.
     */
    public static boolean show(Context context) {
        if (context == null) {
            return false;
        }

        if (!hasNotificationPermission(context)) {
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

            ensureChannel(context, manager);

            PendingIntent pendingIntent = createSettingsIntent(context);
            Notification.Builder builder = new Notification.Builder(context, CHANNEL_ID);
            builder.setSmallIcon(R.drawable.ic_notification_keyboard)
                    .setContentTitle(context.getString(
                            R.string.notification_no_keyboard_title))
                    .setContentText(context.getString(
                            R.string.notification_no_keyboard_text))
                    .setContentIntent(pendingIntent)
                    .setAutoCancel(true)
                    .setOnlyAlertOnce(true)
                    .setDefaults(0)
                    .setSound(null)
                    .setVibrate(null)
                    .setShowWhen(false)
                    .setCategory(Notification.CATEGORY_STATUS)
                    .setPriority(Notification.PRIORITY_LOW)
                    .addAction(new Notification.Action.Builder(
                            Icon.createWithResource(context, R.drawable.ic_notification_keyboard),
                            context.getString(R.string.notification_keyboard_settings),
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

    private static void ensureChannel(Context context, NotificationManager manager) {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_warning_channel_name),
                NotificationManager.IMPORTANCE_LOW);
        channel.setDescription(context.getString(
                R.string.notification_warning_channel_description));
        channel.setSound(null, null);
        channel.enableVibration(false);
        channel.setShowBadge(false);

        manager.createNotificationChannel(channel);
    }

    private static boolean hasNotificationPermission(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return true;
        }

        return ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
    }
}
