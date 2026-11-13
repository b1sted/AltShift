package ru.b1sted.altshift.service;

import android.app.Notification;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

import ru.b1sted.altshift.R;
import ru.b1sted.altshift.storage.HotkeyRepository;
import ru.b1sted.altshift.util.PermissionHelper;

/**
 * Keeps the process unfrozen while AltShift waits for a detached physical
 * keyboard to reconnect.
 *
 * <p>Android 12+ freezes cached applications once the input method service is
 * unbound. A frozen process receives no {@code InputDeviceListener} callbacks,
 * so a pogo-pin or Bluetooth keyboard could reconnect without AltShift noticing.
 * This foreground service holds the process in the cached-but-active state
 * only for the duration of that wait.</p>
 */
public final class KeyboardWatchdogService extends Service {

    private static final String TAG = "AltShift";
    private static final int NOTIFICATION_ID = 3001;
    private static final String WORKER_THREAD_NAME = "AltShift-Watchdog";

    private static volatile boolean running;

    private final ExecutorService worker = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, WORKER_THREAD_NAME);
        thread.setDaemon(true);

        return thread;
    });

    /**
     * Starts the watchdog. The call is idempotent while the service is alive and
     * is always guarded against background-start restrictions.
     */
    public static void start(Context context) {
        if (context == null || running) {
            return;
        }

        Context appContext = context.getApplicationContext();
        if (appContext == null) {
            return;
        }

        try {
            appContext.startForegroundService(new Intent(
                    appContext, KeyboardWatchdogService.class));
        } catch (SecurityException exception) {
            Log.w(TAG, "Unable to start the keyboard watchdog service", exception);
        } catch (RuntimeException exception) {
            // On API 31+ the platform may throw
            // ForegroundServiceStartNotAllowedException, a RuntimeException.
            Log.w(TAG, "Background foreground-service start was denied", exception);
        }
    }

    /**
     * Starts the watchdog during the detach transition, before AltShift hands
     * the input over to the soft keyboard. The IME binding is still alive at
     * this point, which authorises the foreground-service start. The active IME
     * is intentionally not part of this pre-check; the post-switch reconcile
     * stops the service when AltShift remains active.
     */
    public static void startWhileDetached(Context context) {
        if (context == null) {
            return;
        }

        Context appContext = context.getApplicationContext();
        if (appContext == null) {
            return;
        }

        if (!canMonitorWhileDetached(appContext)) {
            stop(appContext);

            return;
        }

        start(appContext);
    }

    public static void stop(Context context) {
        if (context == null) {
            return;
        }

        Context appContext = context.getApplicationContext();
        if (appContext == null) {
            return;
        }

        try {
            appContext.stopService(new Intent(appContext, KeyboardWatchdogService.class));
        } catch (RuntimeException exception) {
            Log.w(TAG, "Unable to stop the keyboard watchdog service", exception);
        }
    }

    /**
     * Applies the current monitoring conditions. Prefer a background thread
     * because input-device and input-method reads perform Binder IPC.
     */
    public static void reconcile(Context context) {
        if (context == null) {
            return;
        }

        boolean keyboardPresent = KeyboardPresenceMonitor
                .isPhysicalKeyboardAvailable(context);

        reconcile(context, keyboardPresent);
    }

    /**
     * Applies the current monitoring conditions with an already resolved
     * keyboard state to avoid a duplicate device scan.
     */
    public static void reconcile(Context context, boolean keyboardPresent) {
        if (context == null) {
            return;
        }

        Context appContext = context.getApplicationContext();
        if (appContext == null) {
            return;
        }

        if (keyboardPresent || !isMonitoringRequired(appContext, false)) {
            stop(appContext);

            return;
        }

        start(appContext);
    }

    /**
     * Returns true only while AltShift is enabled but inactive, the physical
     * keyboard is detached, and at least one reconnect path is usable.
     */
    public static boolean isMonitoringRequired(Context context, boolean keyboardPresent) {
        if (context == null || keyboardPresent) {
            return false;
        }

        try {
            if (InputMethodSwitcher.isAltShiftActive(context)) {
                return false;
            }
        } catch (RuntimeException exception) {
            Log.w(TAG, "Unable to read the active input method", exception);

            return false;
        }

        return canMonitorWhileDetached(context, keyboardPresent);
    }

    /**
     * Checks every precondition except the active IME. This is used during the
     * hand-off, while AltShift is still bound and therefore still active.
     */
    public static boolean canMonitorWhileDetached(Context context) {
        if (context == null) {
            return false;
        }

        boolean keyboardPresent = KeyboardPresenceMonitor
                .isPhysicalKeyboardAvailable(context);

        return canMonitorWhileDetached(context, keyboardPresent);
    }

    private static boolean canMonitorWhileDetached(Context context, boolean keyboardPresent) {
        if (context == null || keyboardPresent) {
            return false;
        }

        try {
            HotkeyRepository repository = HotkeyRepository.getInstance(context);
            if (!repository.isOnboardingCompleted()
                    || !InputMethodSwitcher.isAltShiftEnabled(context)) {
                return false;
            }

            boolean automaticSwitch = repository.isAutoSwitchEnabled()
                    && InputMethodSwitcher.hasSecureSettingsPermission(context);
            boolean interactiveSwitch = PermissionHelper.canDrawOverlays(context);

            return automaticSwitch || interactiveSwitch;
        } catch (RuntimeException exception) {
            Log.w(TAG, "Unable to evaluate keyboard watchdog conditions", exception);

            return false;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();

        running = true;
        NotificationChannels.initialize(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);

        Notification notification = buildNotification();
        if (notification == null || !promoteToForeground(notification)) {
            stopSelf();

            return START_NOT_STICKY;
        }

        scheduleConditionCheck();

        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        running = false;
        worker.shutdownNow();

        try {
            stopForeground(STOP_FOREGROUND_REMOVE);
        } catch (RuntimeException exception) {
            Log.w(TAG, "Unable to remove the keyboard watchdog notification", exception);
        }

        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private Notification buildNotification() {
        try {
            Notification.Builder builder = new Notification.Builder(
                    this, NotificationChannels.KEYBOARD_WATCHDOG);
            builder.setSmallIcon(R.drawable.ic_notification_keyboard)
                    .setContentTitle(getString(R.string.notification_watchdog_title))
                    .setContentText(getString(R.string.notification_watchdog_text))
                    .setOngoing(true)
                    .setOnlyAlertOnce(true)
                    .setShowWhen(false)
                    .setCategory(Notification.CATEGORY_SERVICE);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                builder.setForegroundServiceBehavior(
                        Notification.FOREGROUND_SERVICE_IMMEDIATE);
            }

            return builder.build();
        } catch (RuntimeException exception) {
            Log.w(TAG, "Unable to build the keyboard watchdog notification", exception);

            return null;
        }
    }

    private boolean promoteToForeground(Notification notification) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            } else {
                startForeground(NOTIFICATION_ID, notification);
            }

            return true;
        } catch (SecurityException exception) {
            Log.w(TAG, "Foreground-service permission is missing", exception);
        } catch (RuntimeException exception) {
            Log.w(TAG, "Unable to promote the keyboard watchdog to foreground", exception);
        }

        return false;
    }

    private void scheduleConditionCheck() {
        try {
            worker.execute(this::stopIfNotRequired);
        } catch (RejectedExecutionException exception) {
            Log.w(TAG, "Watchdog condition check rejected during teardown", exception);
        }
    }

    private void stopIfNotRequired() {
        if (canMonitorWhileDetached(this)) {
            return;
        }

        Log.i(TAG, "Keyboard watchdog conditions are no longer met; stopping");
        stopSelf();
    }
}
