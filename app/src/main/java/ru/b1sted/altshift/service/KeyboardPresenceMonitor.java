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
    public static boolean isKeyboardReportedByConfiguration(Context context) {
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

    /**
     * Prefers the authoritative input-device scan and falls back to the
     * configuration flag, which some vendor ROMs update more reliably.
     */
    public static boolean isPhysicalKeyboardAvailable(Context context) {
        if (context == null) {
            return false;
        }

        if (hasKeyboardInputDevice(context)) {
            return true;
        }

        return isKeyboardReportedByConfiguration(context);
    }

    public static boolean hasKeyboardInputDevice(Context context) {
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
                if (device == null || device.isVirtual()) {
                    continue;
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1 && !device.isEnabled()) {
                    continue;
                }

                int sources = device.getSources();
                if ((sources & InputDevice.SOURCE_KEYBOARD) != InputDevice.SOURCE_KEYBOARD) {
                    continue;
                }

                if ((sources & InputDevice.SOURCE_TOUCHSCREEN) == InputDevice.SOURCE_TOUCHSCREEN) {
                    continue;
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                        && device.getKeyboardType() != InputDevice.KEYBOARD_TYPE_ALPHABETIC) {
                    continue;
                }

                return device;
            }
        } catch (RuntimeException exception) {
            Log.w(TAG, "Unable to enumerate input devices", exception);
        }

        return null;
    }
}
