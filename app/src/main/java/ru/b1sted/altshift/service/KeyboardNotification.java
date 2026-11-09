package ru.b1sted.altshift.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.util.Log;

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

    public static void showReconnected(final Context context) {
        if (context == null) {
            return;
        }
        try {
            final NotificationManager manager =
                    (NotificationManager) context.getSystemService(
                            Context.NOTIFICATION_SERVICE);
            if (manager == null) {
                return;
            }
            ensureChannel(context, manager);

            final Intent intent = new Intent(context, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            intent.putExtra(MainActivity.EXTRA_OPEN_IME_PICKER, true);
            final PendingIntent pendingIntent = PendingIntent.getActivity(
                    context,
                    0,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            final Notification.Builder builder =
                    new Notification.Builder(context, CHANNEL_ID);
            builder.setSmallIcon(R.drawable.ic_keyboard)
                    .setContentTitle(context.getString(
                            R.string.notification_keyboard_title))
                    .setContentText(context.getString(
                            R.string.notification_keyboard_text))
                    .setContentIntent(pendingIntent)
                    .setAutoCancel(true)
                    .setCategory(Notification.CATEGORY_STATUS)
                    .addAction(new Notification.Action.Builder(
                            Icon.createWithResource(context, R.drawable.ic_keyboard),
                            context.getString(R.string.notification_switch_back),
                            pendingIntent).build());
            manager.notify(NOTIFICATION_ID, builder.build());
        } catch (final RuntimeException exception) {
            Log.w(TAG, "Unable to post keyboard notification", exception);
        }
    }

    private static void ensureChannel(
            final Context context, final NotificationManager manager) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        final NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW);
        channel.setDescription(context.getString(
                R.string.notification_channel_description));
        manager.createNotificationChannel(channel);
    }
}
