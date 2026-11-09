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

    public static boolean isPhysicalKeyboardAvailable(Context context) {
        if (context == null) {
            return false;
        }

        try {
            Configuration configuration =
                    context.getResources().getConfiguration();
            boolean reported = configuration.keyboard != Configuration.KEYBOARD_NOKEYS
                    && configuration.hardKeyboardHidden
                    == Configuration.HARDKEYBOARDHIDDEN_NO;
            if (reported) {
                return true;
            }
        } catch (RuntimeException exception) {
            Log.w(TAG, "Unable to read keyboard configuration", exception);
        }

        return hasKeyboardInputDevice(context);
    }

    private static boolean hasKeyboardInputDevice(Context context) {
        try {
            InputManager manager =
                    (InputManager) context.getSystemService(Context.INPUT_SERVICE);
            if (manager == null) {
                return false;
            }

            int[] deviceIds = manager.getInputDeviceIds();
            for (int index = 0; index < deviceIds.length; index++) {
                InputDevice device = manager.getInputDevice(deviceIds[index]);
                if (isUsableKeyboard(device)) {
                    return true;
                }
            }
        } catch (RuntimeException exception) {
            Log.w(TAG, "Unable to enumerate input devices", exception);
        }

        return false;
    }

    private static boolean isUsableKeyboard(InputDevice device) {
        if (device == null || device.isVirtual()) {
            return false;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1 && !device.isEnabled()) {
            return false;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return device.getKeyboardType() == InputDevice.KEYBOARD_TYPE_ALPHABETIC;
        }

        int sources = device.getSources();

        return (sources & InputDevice.SOURCE_KEYBOARD) == InputDevice.SOURCE_KEYBOARD
                && (sources & InputDevice.SOURCE_TOUCHSCREEN)
                != InputDevice.SOURCE_TOUCHSCREEN;
    }
}
