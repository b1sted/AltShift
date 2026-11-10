package ru.b1sted.altshift.service;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;

import java.util.List;

import ru.b1sted.altshift.storage.HotkeyRepository;

/**
 * Resolves and applies input method changes through the secure settings API.
 */
public final class InputMethodSwitcher {

    private static final String ALTSHIFT_PACKAGE = "ru.b1sted.altshift";

    public static final String ALTSHIFT_IME_ID =
            ALTSHIFT_PACKAGE + "/.service.HardwareInputService";

    private static final String TAG = "AltShift";
    private static final Object SWITCH_LOCK = new Object();

    private InputMethodSwitcher() {
    }

    /**
     * Always queries the live permission state. Revocation through ADB must be
     * visible immediately, so this value is never cached.
     */
    public static boolean hasSecureSettingsPermission(Context context) {
        if (context == null) {
            return false;
        }

        try {
            return context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS)
                    == PackageManager.PERMISSION_GRANTED;
        } catch (RuntimeException exception) {
            Log.w(TAG, "Unable to verify WRITE_SECURE_SETTINGS", exception);

            return false;
        }
    }

    public static boolean isAltShiftActive(Context context) {
        return isAltShiftId(readActiveInputMethod(context));
    }

    public static String readActiveInputMethod(Context context) {
        if (context == null) {
            return null;
        }

        try {
            return Settings.Secure.getString(
                    context.getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);
        } catch (RuntimeException exception) {
            Log.w(TAG, "Unable to read the active input method", exception);

            return null;
        }
    }

    public static boolean switchToAltShift(Context context) {
        if (context == null || !hasSecureSettingsPermission(context)) {
            return false;
        }

        synchronized (SWITCH_LOCK) {
            if (isAltShiftActive(context)) {
                return true;
            }

            if (!isAltShiftEnabled(context)) {
                Log.w(TAG, "AltShift is not enabled in system settings");

                return false;
            }

            rememberCurrentSoftKeyboard(context);
            Log.i(TAG, "Switching IME to AltShift");

            return setDefaultInputMethodLocked(context, ALTSHIFT_IME_ID);
        }
    }

    /**
     * Returns true when at least one enabled input method is not AltShift.
     * Without such a method, detaching the hardware keyboard would leave the user
     * with no way to type on screen.
     */
    public static boolean hasAlternativeInputMethod(Context context) {
        if (context == null) {
            return false;
        }

        List<InputMethodInfo> methods = readEnabledInputMethods(context);
        if (methods == null) {
            return false;
        }

        for (int index = 0; index < methods.size(); index++) {
            if (!isAltShiftId(methods.get(index).getId())) {
                return true;
            }
        }

        return false;
    }

    public static boolean isAltShiftEnabled(Context context) {
        List<InputMethodInfo> methods = readEnabledInputMethods(context);
        if (methods == null) {
            return false;
        }

        for (int index = 0; index < methods.size(); index++) {
            if (isAltShiftId(methods.get(index).getId())) {
                return true;
            }
        }

        return false;
    }

    public static boolean switchToSoftKeyboard(Context context) {
        if (context == null || !hasSecureSettingsPermission(context)) {
            return false;
        }

        synchronized (SWITCH_LOCK) {
            String target = resolveSoftKeyboardId(context);
            if (target == null) {
                Log.w(TAG, "No soft keyboard is available for automatic switching");

                return false;
            }

            String active = readActiveInputMethod(context);
            if (target.equals(active)) {
                return true;
            }

            Log.i(TAG, "Switching IME to soft keyboard: " + target);

            return setDefaultInputMethodLocked(context, target);
        }
    }

    public static void rememberCurrentSoftKeyboard(Context context) {
        if (context == null) {
            return;
        }

        String active = readActiveInputMethod(context);
        if (TextUtils.isEmpty(active) || isAltShiftId(active)) {
            return;
        }

        try {
            HotkeyRepository.getInstance(context).savePreviousInputMethod(active);
        } catch (RuntimeException exception) {
            Log.w(TAG, "Unable to remember the previous input method", exception);
        }
    }

    public static String resolveSoftKeyboardId(Context context) {
        if (context == null) {
            return null;
        }

        List<InputMethodInfo> methods = readEnabledInputMethods(context);
        if (methods == null || methods.isEmpty()) {
            return null;
        }

        // Selection order comes exclusively from the platform and the user's
        // previous choice; no vendor or OEM package is ever preferred.
        String previous = readPreviousInputMethod(context);
        if (isUsableSoftKeyboard(previous, methods)) {
            return previous;
        }

        for (int index = 0; index < methods.size(); index++) {
            String id = methods.get(index).getId();
            if (!isAltShiftId(id)) {
                return id;
            }
        }

        return null;
    }

    private static boolean setDefaultInputMethodLocked(Context context, String imeId) {
        if (!hasSecureSettingsPermission(context)) {
            Log.w(TAG, "WRITE_SECURE_SETTINGS is not granted; skipping the IME update");

            return false;
        }

        try {
            boolean updated = Settings.Secure.putString(
                    context.getContentResolver(),
                    Settings.Secure.DEFAULT_INPUT_METHOD,
                    imeId);
            if (!updated) {
                Log.w(TAG, "Unable to update the default input method");
            }

            return updated;
        } catch (SecurityException exception) {
            Log.w(TAG, "WRITE_SECURE_SETTINGS was revoked during the IME update", exception);

            return false;
        } catch (RuntimeException exception) {
            Log.e(TAG, "Unable to update the default input method", exception);

            return false;
        }
    }

    private static String readPreviousInputMethod(Context context) {
        try {
            return HotkeyRepository.getInstance(context).getPreviousInputMethod();
        } catch (RuntimeException exception) {
            Log.w(TAG, "Unable to read the previous input method", exception);

            return null;
        }
    }

    private static boolean isUsableSoftKeyboard(
            String imeId, List<InputMethodInfo> methods) {
        if (TextUtils.isEmpty(imeId) || isAltShiftId(imeId)) {
            return false;
        }

        for (int index = 0; index < methods.size(); index++) {
            if (imeId.equals(methods.get(index).getId())) {
                return true;
            }
        }

        return false;
    }

    private static boolean isAltShiftId(String imeId) {
        if (TextUtils.isEmpty(imeId)) {
            return false;
        }

        return imeId.equals(ALTSHIFT_IME_ID)
                || imeId.startsWith(ALTSHIFT_PACKAGE + "/");
    }

    private static List<InputMethodInfo> readEnabledInputMethods(Context context) {
        try {
            InputMethodManager manager = context.getSystemService(InputMethodManager.class);
            if (manager == null) {
                return null;
            }

            return manager.getEnabledInputMethodList();
        } catch (RuntimeException exception) {
            Log.e(TAG, "Unable to query enabled input methods", exception);

            return null;
        }
    }
}
