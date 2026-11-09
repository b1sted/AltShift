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
import android.util.Log;

import androidx.core.content.ContextCompat;

import ru.b1sted.altshift.R;
import ru.b1sted.altshift.ui.MainActivity;

/**
 * Posts a low-priority prompt when a physical keyboard becomes available again.
 */
public final class KeyboardNotification {

    private static final String TAG = "AltShift";
    private static final String CHANNEL_ID = "keyboard_state";
    private static final int NOTIFICATION_ID = 1001;

    private KeyboardNotification() {
    }

    public static void showReconnected(Context context) {
        if (context == null) {
            return;
        }

        if (!hasNotificationPermission(context)) {
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

            ensureChannel(context, manager);

            Intent intent = new Intent(context, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            intent.putExtra(MainActivity.EXTRA_OPEN_IME_PICKER, true);

            PendingIntent pendingIntent = PendingIntent.getActivity(
                    context,
                    0,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            Notification.Builder builder =
                    new Notification.Builder(context, CHANNEL_ID);
            builder.setSmallIcon(R.drawable.ic_notification_keyboard)
                    .setContentTitle(context.getString(
                            R.string.notification_keyboard_title))
                    .setContentText(context.getString(
                            R.string.notification_keyboard_text))
                    .setContentIntent(pendingIntent)
                    .setAutoCancel(true)
                    .setCategory(Notification.CATEGORY_STATUS)
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
        if (context == null) {
            return false;
        }

        return hasNotificationPermission(context);
    }

    private static void ensureChannel(Context context, NotificationManager manager) {
        NotificationChannel existing = manager.getNotificationChannel(CHANNEL_ID);
        if (existing != null) {
            return;
        }

        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW);
        channel.setDescription(context.getString(
                R.string.notification_channel_description));

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
