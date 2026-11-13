package ru.b1sted.altshift.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.util.Log;

import ru.b1sted.altshift.service.HardwareInputService;
import ru.b1sted.altshift.service.InputMethodSwitcher;
import ru.b1sted.altshift.service.KeyboardNotification;
import ru.b1sted.altshift.service.KeyboardPresenceMonitor;
import ru.b1sted.altshift.service.KeyboardPromptOverlay;
import ru.b1sted.altshift.service.KeyboardWatchdogService;
import ru.b1sted.altshift.storage.HotkeyRepository;
import ru.b1sted.altshift.ui.SwitchBackActivity;
import ru.b1sted.altshift.util.PermissionHelper;

/**
 * Restores the input-device monitor after a reboot or screen unlock.
 *
 * <p>The {@link Intent#ACTION_USER_PRESENT} broadcast also wakes the process
 * when the platform has frozen the application in the background, keeping the
 * hardware input connection available immediately after unlock.</p>
 */
public final class BootReceiver extends BroadcastReceiver {

    private static final String TAG = "AltShift";

    private static BroadcastReceiver screenUnlockReceiver;

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent == null ? null : intent.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action)
                && !Intent.ACTION_USER_PRESENT.equals(action)) {
            return;
        }

        handleIntent(context, intent);
    }

    /**
     * Registers a process-local {@link Intent#ACTION_USER_PRESENT} receiver.
     *
     * <p>Android 8.0+ blocks implicit {@code USER_PRESENT} broadcasts for
     * manifest-declared receivers, so the manifest entry alone is never
     * delivered to a background application. The application process registers
     * this receiver on startup; the system then delivers the unlock broadcast
     * directly to the live process, without waiting for a manifest component to
     * launch it.</p>
     */
    public static void registerScreenUnlockReceiver(Context context) {
        if (context == null) {
            return;
        }

        Context appContext = context.getApplicationContext();
        if (appContext == null) {
            return;
        }

        synchronized (BootReceiver.class) {
            if (screenUnlockReceiver != null) {
                return;
            }

            BroadcastReceiver receiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context receiverContext, Intent receiverIntent) {
                    handleIntent(receiverContext, receiverIntent);
                }
            };

            try {
                IntentFilter filter = new IntentFilter(Intent.ACTION_USER_PRESENT);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    // USER_PRESENT is a protected broadcast sent by SystemUI,
                    // not by this package. The receiver must be exported for the
                    // platform to deliver it; third-party apps cannot spoof the
                    // protected action.
                    appContext.registerReceiver(
                            receiver, filter, Context.RECEIVER_EXPORTED);
                } else {
                    appContext.registerReceiver(receiver, filter);
                }

                screenUnlockReceiver = receiver;
            } catch (RuntimeException exception) {
                Log.w(TAG, "Unable to register the screen-unlock receiver", exception);
            }
        }
    }

    private static void handleIntent(Context context, Intent intent) {
        if (context == null || intent == null) {
            return;
        }

        String action = intent.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action)
                && !Intent.ACTION_USER_PRESENT.equals(action)) {
            return;
        }

        Log.d(TAG, "Handling " + action);

        Context appContext = context.getApplicationContext();

        try {
            HotkeyRepository repository = HotkeyRepository.getInstance(appContext);
            if (!repository.isOnboardingCompleted()) {
                return;
            }

            // Interactive recovery is independent from the auto-switch
            // preference: only autonomous switches require the preference.
            boolean canSwitch = repository.isAutoSwitchEnabled()
                    && InputMethodSwitcher.hasSecureSettingsPermission(appContext);
            boolean canPrompt = PermissionHelper.canDrawOverlays(appContext);
            boolean canNotify = KeyboardNotification.canPost(appContext);
            if (!canSwitch && !canPrompt && !canNotify) {
                return;
            }

            boolean keyboardPresent = KeyboardPresenceMonitor
                    .isPhysicalKeyboardAvailable(appContext);

            HardwareInputService.startKeyboardMonitor(appContext);
            KeyboardWatchdogService.reconcile(appContext, keyboardPresent);

            if (!keyboardPresent) {
                return;
            }

            if (canSwitch) {
                if (InputMethodSwitcher.switchToAltShift(appContext)) {
                    KeyboardPromptOverlay.dismissActive();
                } else {
                    Log.w(TAG, "Unable to restore AltShift after " + action);
                }

                return;
            }

            showReconnectPromptIfNeeded(appContext);
        } catch (RuntimeException exception) {
            Log.w(TAG, "Unable to reconcile the keyboard state after " + action, exception);
        }
    }

    /**
     * Without {@code WRITE_SECURE_SETTINGS} the switch cannot be automatic, so
     * the one-tap Dynamic Island capsule is surfaced whenever AltShift is not
     * the active input method. When it already is active, the hardware keyboard
     * is connected and the user is not trapped.
     */
    private static void showReconnectPromptIfNeeded(Context context) {
        if (InputMethodSwitcher.isAltShiftActive(context)) {
            return;
        }

        KeyboardNotification.cancel(context);
        KeyboardPromptOverlay.show(
                context,
                () -> SwitchBackActivity.start(context),
                () -> onPromptDismissed(context));
    }

    /**
     * A closed or timed-out prompt posts a standard reminder only while the
     * keyboard is still attached and AltShift remains inactive.
     */
    private static void onPromptDismissed(Context context) {
        if (!KeyboardPresenceMonitor.isPhysicalKeyboardAvailable(context)) {
            return;
        }

        if (InputMethodSwitcher.isAltShiftActive(context)) {
            return;
        }

        KeyboardNotification.showInactive(context);
    }
}
