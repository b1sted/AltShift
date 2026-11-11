package ru.b1sted.altshift.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import ru.b1sted.altshift.service.HardwareInputService;
import ru.b1sted.altshift.service.InputMethodSwitcher;
import ru.b1sted.altshift.service.KeyboardNotification;
import ru.b1sted.altshift.storage.HotkeyRepository;
import ru.b1sted.altshift.util.PermissionHelper;

/**
 * Restores the input-device monitor after a device reboot.
 */
public final class BootReceiver extends BroadcastReceiver {

    private static final String TAG = "AltShift";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null) {
            return;
        }

        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            return;
        }

        try {
            HotkeyRepository repository = HotkeyRepository.getInstance(context);
            if (!repository.isAutoSwitchEnabled()) {
                return;
            }

            if (!InputMethodSwitcher.hasSecureSettingsPermission(context)
                    && !PermissionHelper.canDrawOverlays(context)
                    && !KeyboardNotification.canPost(context)) {
                return;
            }

            HardwareInputService.startKeyboardMonitor(context);
        } catch (RuntimeException exception) {
            Log.w(TAG, "Unable to restore keyboard monitoring", exception);
        }
    }
}
