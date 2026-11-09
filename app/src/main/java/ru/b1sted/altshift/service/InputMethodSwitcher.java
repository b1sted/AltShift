package ru.b1sted.altshift.service;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;

import androidx.core.content.ContextCompat;

import java.util.List;

import ru.b1sted.altshift.storage.HotkeyRepository;

/**
 * Resolves and applies input method changes through the secure settings API.
 */
public final class InputMethodSwitcher {

    public static final String ALTSHIFT_IME_ID =
            "ru.b1sted.altshift/.service.HardwareInputService";

    private static final String TAG = "AltShift";
    private static final String GBOARD_PACKAGE = "com.google.android.inputmethod.latin";

    private InputMethodSwitcher() {
    }

    public static boolean hasSecureSettingsPermission(Context context) {
        if (context == null) {
            return false;
        }

        return ContextCompat.checkSelfPermission(
                context, Manifest.permission.WRITE_SECURE_SETTINGS)
                == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean isAltShiftActive(Context context) {
        String active = readActiveInputMethod(context);
        if (TextUtils.isEmpty(active)) {
            return false;
        }

        if (isAltShiftId(active)) {
            return true;
        }

        ComponentName component = new ComponentName(context, HardwareInputService.class);

        return active.contains(component.flattenToString())
                || active.contains(component.flattenToShortString());
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

        if (isAltShiftActive(context)) {
            return true;
        }

        if (!isAltShiftEnabled(context)) {
            Log.w(TAG, "AltShift is not enabled in system settings");

            return false;
        }

        rememberCurrentSoftKeyboard(context);

        return setDefaultInputMethod(context, ALTSHIFT_IME_ID);
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

        String target = resolveSoftKeyboardId(context);
        if (target == null) {
            Log.w(TAG, "No soft keyboard is available for automatic switching");

            return false;
        }

        return setDefaultInputMethod(context, target);
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

        String previous = readPreviousInputMethod(context);
        if (isUsableSoftKeyboard(previous, methods)) {
            return previous;
        }

        String gboard = findGboard(methods);
        if (gboard != null) {
            return gboard;
        }

        for (int index = 0; index < methods.size(); index++) {
            String id = methods.get(index).getId();
            if (!isAltShiftId(id)) {
                return id;
            }
        }

        return null;
    }

    public static boolean setDefaultInputMethod(Context context, String imeId) {
        if (context == null || TextUtils.isEmpty(imeId)) {
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
            Log.w(TAG, "WRITE_SECURE_SETTINGS is required for automatic switching", exception);

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

    private static String findGboard(List<InputMethodInfo> methods) {
        for (int index = 0; index < methods.size(); index++) {
            InputMethodInfo info = methods.get(index);
            if (GBOARD_PACKAGE.equals(info.getPackageName())) {
                return info.getId();
            }
        }

        return null;
    }

    private static boolean isAltShiftId(String imeId) {
        if (TextUtils.isEmpty(imeId)) {
            return false;
        }

        return imeId.equals(ALTSHIFT_IME_ID) || imeId.contains("ru.b1sted.altshift");
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
