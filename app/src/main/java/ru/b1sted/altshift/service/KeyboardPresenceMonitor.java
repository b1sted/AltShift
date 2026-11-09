package ru.b1sted.altshift.service;

import android.content.Context;
import android.content.res.Configuration;
import android.hardware.input.InputManager;
import android.os.Build;
import android.util.Log;
import android.view.InputDevice;

/**
 * Detects whether a usable physical keyboard is currently attached.
 */
public final class KeyboardPresenceMonitor {

    private static final String TAG = "AltShift";

    private KeyboardPresenceMonitor() {
    }

    public static boolean isPhysicalKeyboardAvailable(final Context context) {
        if (context == null) {
            return false;
        }
        try {
            final Configuration configuration =
                    context.getResources().getConfiguration();
            final boolean reported = configuration.keyboard != Configuration.KEYBOARD_NOKEYS
                    && configuration.hardKeyboardHidden
                    == Configuration.HARDKEYBOARDHIDDEN_NO;
            if (reported) {
                return true;
            }
        } catch (final RuntimeException exception) {
            Log.w(TAG, "Unable to read keyboard configuration", exception);
        }
        return hasKeyboardInputDevice(context);
    }

    private static boolean hasKeyboardInputDevice(final Context context) {
        try {
            final InputManager manager =
                    (InputManager) context.getSystemService(Context.INPUT_SERVICE);
            if (manager == null) {
                return false;
            }
            final int[] deviceIds = manager.getInputDeviceIds();
            for (int index = 0; index < deviceIds.length; index++) {
                final InputDevice device = manager.getInputDevice(deviceIds[index]);
                if (device == null || device.isVirtual()) {
                    continue;
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    if (device.getKeyboardType() == InputDevice.KEYBOARD_TYPE_ALPHABETIC) {
                        return true;
                    }
                    continue;
                }
                final int sources = device.getSources();
                if ((sources & InputDevice.SOURCE_KEYBOARD) == InputDevice.SOURCE_KEYBOARD
                        && (sources & InputDevice.SOURCE_TOUCHSCREEN)
                        != InputDevice.SOURCE_TOUCHSCREEN) {
                    return true;
                }
            }
        } catch (final RuntimeException exception) {
            Log.w(TAG, "Unable to enumerate input devices", exception);
        }
        return false;
    }
}
