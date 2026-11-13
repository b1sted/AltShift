package ru.b1sted.altshift.service;

import android.app.Notification;
import android.app.NotificationManager;
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
 * Keeps the process unfrozen while AltShift waits for a physical keyboard or
 * for the user to return to AltShift from another input method.
 *
 * <p>Android 12+ freezes cached applications once the input method service is
 * unbound. A frozen process receives no {@code InputDeviceListener} callbacks,
 * so a pogo-pin or Bluetooth keyboard could reconnect without AltShift noticing.
 * This foreground service holds the process in the cached-but-active state
 * until AltShift becomes the active input method again.</p>
 */
public final class KeyboardWatchdogService extends Service {

    private static final String TAG = "AltShift";
    private static final int NOTIFICATION_ID = 3001;
    private static final String WORKER_THREAD_NAME = "AltShift-Watchdog";
    private static final String EXTRA_KEYBOARD_PRESENT = "keyboard_present";
    private static final String EXTRA_HANDOFF = "handoff";

    private static volatile boolean running;
    private static volatile boolean postedKeyboardPresent;

    private final ExecutorService worker = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, WORKER_THREAD_NAME);
        thread.setDaemon(true);

        return thread;
    });

    /**
     * Starts the watchdog and resolves the current keyboard state for the
     * status notification. The call is idempotent while the service is alive.
     */
    public static void start(Context context) {
        if (context == null) {
            return;
        }

        Context appContext = context.getApplicationContext();
        if (appContext == null) {
            return;
        }

        start(appContext, KeyboardPresenceMonitor.isPhysicalKeyboardAvailable(appContext));
    }

    /**
     * Starts the watchdog with an already resolved keyboard state. While the
     * service is already running the posted notification is refreshed only when
     * the keyboard state actually changed.
     */
    public static void start(Context context, boolean keyboardPresent) {
        if (context == null) {
            return;
        }

        Context appContext = context.getApplicationContext();
        if (appContext == null) {
            return;
        }

        startInternal(appContext, keyboardPresent, false);
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

        if (!isMonitoringAvailable(appContext)) {
            stop(appContext);

            return;
        }

        boolean keyboardPresent = KeyboardPresenceMonitor
                .isPhysicalKeyboardAvailable(appContext);

        startInternal(appContext, keyboardPresent, true);
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
     * keyboard state to avoid a duplicate device scan. The keyboard state only
     * selects the notification text; it never disables monitoring.
     */
    public static void reconcile(Context context, boolean keyboardPresent) {
        if (context == null) {
            return;
        }

        Context appContext = context.getApplicationContext();
        if (appContext == null) {
            return;
        }

        if (!isMonitoringRequired(appContext, keyboardPresent)) {
            stop(appContext);

            return;
        }

        start(appContext, keyboardPresent);
    }

    /**
     * Returns true while AltShift is enabled but inactive and at least one
     * reconnect or alert path is usable. The physical keyboard may be attached
     * or detached; the process must stay unfrozen in both states so it can
     * cancel stale alerts on disconnect and show the Dynamic Island again on
     * reconnect.
     */
    public static boolean isMonitoringRequired(Context context, boolean keyboardPresent) {
        if (context == null) {
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

        return isMonitoringAvailable(context);
    }

    /**
     * Checks the hard preconditions: onboarding is complete, AltShift is
     * enabled, and at least one automatic switch, overlay, or notification path
     * is usable. The active input method is checked separately.
     */
    public static boolean isMonitoringAvailable(Context context) {
        if (context == null) {
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
            boolean alertOnly = KeyboardNotification.canPost(context);

            return automaticSwitch || interactiveSwitch || alertOnly;
        } catch (RuntimeException exception) {
            Log.w(TAG, "Unable to evaluate keyboard watchdog conditions", exception);

            return false;
        }
    }

    private static void startInternal(
            Context appContext, boolean keyboardPresent, boolean handoff) {
        if (running) {
            updateNotificationIfChanged(appContext, keyboardPresent);

            return;
        }

        try {
            Intent intent = new Intent(appContext, KeyboardWatchdogService.class);
            intent.putExtra(EXTRA_KEYBOARD_PRESENT, keyboardPresent);
            intent.putExtra(EXTRA_HANDOFF, handoff);
            appContext.startForegroundService(intent);
        } catch (SecurityException exception) {
            Log.w(TAG, "Unable to start the keyboard watchdog service", exception);
        } catch (RuntimeException exception) {
            // On API 31+ the platform may throw
            // ForegroundServiceStartNotAllowedException, a RuntimeException.
            Log.w(TAG, "Background foreground-service start was denied", exception);
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

        boolean keyboardPresent = resolveKeyboardPresent(intent);
        Notification notification = buildNotification(this, keyboardPresent);
        if (notification == null || !promoteToForeground(notification)) {
            stopSelf();

            return START_NOT_STICKY;
        }

        postedKeyboardPresent = keyboardPresent;

        if (intent == null || !intent.getBooleanExtra(EXTRA_HANDOFF, false)) {
            scheduleConditionCheck();
        }

        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        running = false;
        postedKeyboardPresent = false;
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

    private boolean resolveKeyboardPresent(Intent intent) {
        if (intent != null && intent.hasExtra(EXTRA_KEYBOARD_PRESENT)) {
            return intent.getBooleanExtra(EXTRA_KEYBOARD_PRESENT, false);
        }

        return KeyboardPresenceMonitor.isPhysicalKeyboardAvailable(this);
    }

    private static void updateNotificationIfChanged(
            Context appContext, boolean keyboardPresent) {
        if (postedKeyboardPresent == keyboardPresent) {
            return;
        }

        try {
            NotificationManager manager =
                    appContext.getSystemService(NotificationManager.class);
            if (manager == null) {
                return;
            }

            Notification notification = buildNotification(appContext, keyboardPresent);
            if (notification == null) {
                return;
            }

            manager.notify(NOTIFICATION_ID, notification);
            postedKeyboardPresent = keyboardPresent;
        } catch (RuntimeException exception) {
            Log.w(TAG, "Unable to update the keyboard watchdog notification", exception);
        }
    }

    private static Notification buildNotification(
            Context context, boolean keyboardPresent) {
        int title = keyboardPresent
                ? R.string.notification_watchdog_monitoring_title
                : R.string.notification_watchdog_title;
        int text = keyboardPresent
                ? R.string.notification_watchdog_monitoring_text
                : R.string.notification_watchdog_text;

        try {
            Notification.Builder builder = new Notification.Builder(
                    context, NotificationChannels.KEYBOARD_WATCHDOG);
            builder.setSmallIcon(R.drawable.ic_notification_keyboard)
                    .setContentTitle(context.getString(title))
                    .setContentText(context.getString(text))
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
        if (isMonitoringRequired(this, false)) {
            return;
        }

        Log.i(TAG, "Keyboard watchdog conditions are no longer met; stopping");
        stopSelf();
    }
}
