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

    /**
     * Cheap configuration-only check. Safe to call on the main thread because it
     * performs no Binder IPC.
     */
    public static boolean isPhysicalKeyboardReported(Context context) {
        if (context == null) {
            return false;
        }

        try {
            Configuration configuration = context.getResources().getConfiguration();

            return configuration.keyboard != Configuration.KEYBOARD_NOKEYS
                    && configuration.hardKeyboardHidden == Configuration.HARDKEYBOARDHIDDEN_NO;
        } catch (RuntimeException exception) {
            Log.w(TAG, "Unable to read keyboard configuration", exception);

            return false;
        }
    }

    public static boolean isPhysicalKeyboardAvailable(Context context) {
        if (context == null) {
            return false;
        }

        if (isPhysicalKeyboardReported(context)) {
            return true;
        }

        return findPhysicalKeyboard(context) != null;
    }

    /**
     * Resolves the first usable physical keyboard. Performs Binder IPC, so call
     * it off the main thread whenever possible.
     */
    public static InputDevice findPhysicalKeyboard(Context context) {
        if (context == null) {
            return null;
        }

        try {
            InputManager manager =
                    (InputManager) context.getSystemService(Context.INPUT_SERVICE);
            if (manager == null) {
                return null;
            }

            int[] deviceIds = manager.getInputDeviceIds();
            if (deviceIds == null) {
                return null;
            }

            for (int index = 0; index < deviceIds.length; index++) {
                InputDevice device = manager.getInputDevice(deviceIds[index]);
                if (isUsableKeyboard(device)) {
                    return device;
                }
            }
        } catch (RuntimeException exception) {
            Log.w(TAG, "Unable to enumerate input devices", exception);
        }

        return null;
    }

    /**
     * Accepts any enabled, non-virtual alphabetic keyboard. Vendor names, bus
     * types, and product identifiers are deliberately ignored so USB, Bluetooth,
     * and pogo-pin keyboards behave identically across all Android devices.
     */
    private static boolean isUsableKeyboard(InputDevice device) {
        if (device == null || device.isVirtual()) {
            return false;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1 && !device.isEnabled()) {
            return false;
        }

        int sources = device.getSources();
        if ((sources & InputDevice.SOURCE_KEYBOARD) != InputDevice.SOURCE_KEYBOARD) {
            return false;
        }

        if ((sources & InputDevice.SOURCE_TOUCHSCREEN) == InputDevice.SOURCE_TOUCHSCREEN) {
            return false;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return device.getKeyboardType() == InputDevice.KEYBOARD_TYPE_ALPHABETIC;
        }

        return true;
    }
}
