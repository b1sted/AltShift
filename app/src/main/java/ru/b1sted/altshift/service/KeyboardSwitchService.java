package ru.b1sted.altshift.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.content.res.Configuration;
import android.hardware.input.InputManager;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.ServiceCompat;

import ru.b1sted.altshift.R;
import ru.b1sted.altshift.storage.HotkeyRepository;
import ru.b1sted.altshift.ui.MainActivity;

/**
 * Event-driven foreground monitor that keeps hardware keyboard switching seamless.
 */
public final class KeyboardSwitchService extends Service {

    public static final String ACTION_STOP =
            "ru.b1sted.altshift.action.STOP_AUTO_SWITCH";

    private static final String TAG = "AltShift";
    private static final String CHANNEL_ID = "auto_switch";
    private static final int NOTIFICATION_ID = 1002;

    private final InputManager.InputDeviceListener deviceListener =
            new InputManager.InputDeviceListener() {
                @Override
                public void onInputDeviceAdded(int deviceId) {
                    handleKeyboardStateChange();
                }

                @Override
                public void onInputDeviceRemoved(int deviceId) {
                    handleKeyboardStateChange();
                }

                @Override
                public void onInputDeviceChanged(int deviceId) {
                    handleKeyboardStateChange();
                }
            };

    private InputManager inputManager;
    private boolean listenerRegistered;
    private boolean keyboardPresent;

    public static void start(Context context) {
        if (context == null) {
            return;
        }

        Intent intent = new Intent(context, KeyboardSwitchService.class);
        try {
            context.startForegroundService(intent);
        } catch (RuntimeException exception) {
            Log.w(TAG, "Unable to start the auto-switch service", exception);
        }
    }

    public static void stop(Context context) {
        if (context == null) {
            return;
        }

        try {
            context.stopService(new Intent(context, KeyboardSwitchService.class));
        } catch (RuntimeException exception) {
            Log.w(TAG, "Unable to stop the auto-switch service", exception);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();

        keyboardPresent = KeyboardPresenceMonitor.isPhysicalKeyboardAvailable(this);
        startForegroundNotification();
        registerDeviceListener();
        reconcileKeyboardState();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopSelf();

            return START_NOT_STICKY;
        }

        if (!isAutoSwitchAllowed()) {
            stopSelf();

            return START_NOT_STICKY;
        }

        startForegroundNotification();
        reconcileKeyboardState();

        return START_STICKY;
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        handleKeyboardStateChange();
    }

    @Override
    public void onDestroy() {
        unregisterDeviceListener();

        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private boolean isAutoSwitchAllowed() {
        try {
            HotkeyRepository repository = HotkeyRepository.getInstance(this);

            return repository.isAutoSwitchEnabled()
                    && InputMethodSwitcher.hasSecureSettingsPermission(this);
        } catch (RuntimeException exception) {
            Log.e(TAG, "Unable to read the auto-switch setting", exception);

            return false;
        }
    }

    private void reconcileKeyboardState() {
        if (!isAutoSwitchAllowed()) {
            stopSelf();

            return;
        }

        boolean present = KeyboardPresenceMonitor.isPhysicalKeyboardAvailable(this);
        keyboardPresent = present;
        applyKeyboardState(present);
    }

    private void handleKeyboardStateChange() {
        if (!isAutoSwitchAllowed()) {
            stopSelf();

            return;
        }

        boolean present = KeyboardPresenceMonitor.isPhysicalKeyboardAvailable(this);
        if (present == keyboardPresent) {
            return;
        }

        keyboardPresent = present;
        applyKeyboardState(present);
    }

    private void applyKeyboardState(boolean present) {
        if (present) {
            if (!InputMethodSwitcher.switchToAltShift(this)) {
                Log.w(TAG, "Unable to switch to AltShift automatically");
            }

            return;
        }

        if (!InputMethodSwitcher.switchToSoftKeyboard(this)) {
            Log.w(TAG, "Unable to switch to the soft keyboard automatically");
        }
    }

    private void startForegroundNotification() {
        try {
            NotificationManager manager =
                    (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (manager == null) {
                return;
            }

            ensureChannel(manager);

            Notification notification = buildNotification();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceCompat.startForeground(
                        this,
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            } else {
                startForeground(NOTIFICATION_ID, notification);
            }
        } catch (RuntimeException exception) {
            Log.e(TAG, "Unable to start the auto-switch foreground service", exception);
            stopSelf();
        }
    }

    private Notification buildNotification() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_keyboard)
                .setContentTitle(getString(R.string.auto_switch_notification_title))
                .setContentText(getString(R.string.auto_switch_notification_text))
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setShowWhen(false)
                .setCategory(Notification.CATEGORY_SERVICE)
                .build();
    }

    private void ensureChannel(NotificationManager manager) {
        if (manager.getNotificationChannel(CHANNEL_ID) != null) {
            return;
        }

        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                getString(R.string.auto_switch_channel_name),
                NotificationManager.IMPORTANCE_LOW);
        channel.setDescription(getString(
                R.string.auto_switch_channel_description));
        channel.setShowBadge(false);

        manager.createNotificationChannel(channel);
    }

    private void registerDeviceListener() {
        try {
            inputManager = getSystemService(InputManager.class);
            if (inputManager == null) {
                return;
            }

            inputManager.registerInputDeviceListener(deviceListener, null);
            listenerRegistered = true;
        } catch (RuntimeException exception) {
            Log.e(TAG, "Unable to register the input device listener", exception);
        }
    }

    private void unregisterDeviceListener() {
        if (!listenerRegistered) {
            return;
        }

        listenerRegistered = false;

        try {
            InputManager manager = inputManager;
            if (manager != null) {
                manager.unregisterInputDeviceListener(deviceListener);
            }
        } catch (RuntimeException exception) {
            Log.w(TAG, "Unable to unregister the input device listener", exception);
        } finally {
            inputManager = null;
        }
    }
}
