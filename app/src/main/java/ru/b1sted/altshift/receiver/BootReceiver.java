package ru.b1sted.altshift.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.util.Log;

import ru.b1sted.altshift.service.InputMethodSwitcher;
import ru.b1sted.altshift.service.KeyboardNotification;
import ru.b1sted.altshift.service.KeyboardSwitchService;
import ru.b1sted.altshift.storage.HotkeyRepository;

/**
 * Restores the auto-switch monitor after a device reboot.
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
                    && !Settings.canDrawOverlays(context)
                    && !KeyboardNotification.canPost(context)) {
                return;
            }

            KeyboardSwitchService.start(context);
        } catch (RuntimeException exception) {
            Log.w(TAG, "Unable to restore auto-switching", exception);
        }
    }
}
