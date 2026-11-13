package ru.b1sted.altshift.storage;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import ru.b1sted.altshift.model.HotkeyBinding;
import ru.b1sted.altshift.model.LanguageSwitchShortcut;
import ru.b1sted.altshift.model.ThemeMode;

public final class HotkeyRepository {

    private static final String TAG = "AltShift";
    private static final String PREFS_NAME = "altshift_prefs";
    private static final String KEY_HOTKEYS = "hotkeys";
    private static final String KEY_LANGUAGE_SHORTCUT = "language_shortcut";
    private static final String KEY_ONBOARDING_COMPLETED = "onboarding_completed";
    private static final String KEY_AUTO_SWITCH_ENABLED = "auto_switch_enabled";
    private static final String KEY_PREVIOUS_INPUT_METHOD = "previous_input_method";
    private static final String KEY_THEME_MODE = "theme_mode";
    private static final HotkeyBinding[] EMPTY_BINDINGS = new HotkeyBinding[0];

    private static volatile HotkeyRepository instance;

    private final SharedPreferences preferences;
    private final Object lock = new Object();

    private volatile HotkeyBinding[] bindings;
    private volatile LanguageSwitchShortcut languageShortcut;
    private volatile boolean onboardingCompleted;
    private volatile boolean autoSwitchEnabled;
    private volatile String previousInputMethod;
    private volatile ThemeMode themeMode;

    private HotkeyRepository(Context context) {
        preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        bindings = readBindings(readString(preferences, KEY_HOTKEYS));
        languageShortcut = readLanguageShortcut(
                readString(preferences, KEY_LANGUAGE_SHORTCUT));
        onboardingCompleted = readBoolean(
                preferences, KEY_ONBOARDING_COMPLETED, false);
        autoSwitchEnabled = readBoolean(
                preferences, KEY_AUTO_SWITCH_ENABLED, true);
        previousInputMethod = readString(preferences, KEY_PREVIOUS_INPUT_METHOD);
        themeMode = ThemeMode.fromKey(readString(preferences, KEY_THEME_MODE));
    }

    public static HotkeyRepository getInstance(Context context) {
        Context appContext = Objects.requireNonNull(context, "context")
                .getApplicationContext();
        HotkeyRepository local = instance;

        if (local != null) {
            return local;
        }

        return createInstance(appContext);
    }

    private static synchronized HotkeyRepository createInstance(Context appContext) {
        if (instance == null) {
            instance = new HotkeyRepository(appContext);
        }

        return instance;
    }

    public HotkeyBinding[] getBindings() {
        return bindings;
    }

    public LanguageSwitchShortcut getLanguageShortcut() {
        return languageShortcut;
    }

    public boolean isOnboardingCompleted() {
        return onboardingCompleted;
    }

    public void setOnboardingCompleted(boolean completed) {
        if (onboardingCompleted == completed) {
            return;
        }

        synchronized (lock) {
            preferences.edit()
                    .putBoolean(KEY_ONBOARDING_COMPLETED, completed)
                    .apply();
            onboardingCompleted = completed;
        }
    }

    public boolean isAutoSwitchEnabled() {
        return autoSwitchEnabled;
    }

    public void saveAutoSwitchEnabled(boolean enabled) {
        if (autoSwitchEnabled == enabled) {
            return;
        }

        synchronized (lock) {
            preferences.edit()
                    .putBoolean(KEY_AUTO_SWITCH_ENABLED, enabled)
                    .apply();
            autoSwitchEnabled = enabled;
        }
    }

    public String getPreviousInputMethod() {
        return previousInputMethod;
    }

    public void savePreviousInputMethod(String imeId) {
        if (Objects.equals(previousInputMethod, imeId)) {
            return;
        }

        synchronized (lock) {
            SharedPreferences.Editor editor = preferences.edit();
            if (imeId == null || imeId.isEmpty()) {
                editor.remove(KEY_PREVIOUS_INPUT_METHOD);
            } else {
                editor.putString(KEY_PREVIOUS_INPUT_METHOD, imeId);
            }

            editor.apply();
            previousInputMethod = imeId;
        }
    }

    public ThemeMode getThemeMode() {
        return themeMode;
    }

    public void saveThemeMode(ThemeMode mode) {
        Objects.requireNonNull(mode, "mode");
        if (themeMode == mode) {
            return;
        }

        synchronized (lock) {
            preferences.edit()
                    .putString(KEY_THEME_MODE, mode.key())
                    .apply();
            themeMode = mode;
        }
    }

    public void saveBinding(HotkeyBinding binding) {
        Objects.requireNonNull(binding, "binding");

        synchronized (lock) {
            HotkeyBinding[] updated = HotkeyBindingStore.upsert(bindings, binding);
            if (updated == bindings) {
                return;
            }

            persistBindings(updated);
            bindings = updated;
        }
    }

    public void deleteBinding(HotkeyBinding binding) {
        Objects.requireNonNull(binding, "binding");

        synchronized (lock) {
            HotkeyBinding[] updated = HotkeyBindingStore.remove(bindings, binding);
            if (updated == bindings) {
                return;
            }

            persistBindings(updated);
            bindings = updated;
        }
    }

    public void saveLanguageShortcut(LanguageSwitchShortcut shortcut) {
        Objects.requireNonNull(shortcut, "shortcut");
        if (shortcut.equals(languageShortcut)) {
            return;
        }

        synchronized (lock) {
            preferences.edit()
                    .putString(KEY_LANGUAGE_SHORTCUT, writeLanguageShortcut(shortcut))
                    .apply();
            languageShortcut = shortcut;
        }
    }

    private void persistBindings(HotkeyBinding[] updated) {
        preferences.edit()
                .putString(KEY_HOTKEYS, writeBindings(updated))
                .apply();
    }

    private static String readString(SharedPreferences preferences, String key) {
        try {
            return preferences.getString(key, null);
        } catch (RuntimeException exception) {
            Log.w(TAG, "Ignoring an invalid preference value for " + key, exception);

            return null;
        }
    }

    private static boolean readBoolean(
            SharedPreferences preferences, String key, boolean fallback) {
        try {
            return preferences.getBoolean(key, fallback);
        } catch (RuntimeException exception) {
            Log.w(TAG, "Ignoring an invalid preference value for " + key, exception);

            return fallback;
        }
    }

    private static String writeBindings(HotkeyBinding[] items) {
        JSONArray array = new JSONArray();

        for (HotkeyBinding item : items) {
            JSONObject object = new JSONObject();
            try {
                object.put("keyCode", item.keyCode());
                object.put("ctrl", item.isCtrl());
                object.put("alt", item.isAlt());
                object.put("shift", item.isShift());
                object.put("meta", item.isMeta());
                object.put("packageName", item.packageName());
                object.put("appName", item.appName());
            } catch (JSONException exception) {
                Log.e(TAG, "Unable to serialize hotkey binding", exception);
                continue;
            }

            array.put(object);
        }

        return array.toString();
    }

    private static HotkeyBinding[] readBindings(String raw) {
        if (raw == null || raw.isEmpty()) {
            return EMPTY_BINDINGS;
        }

        try {
            JSONArray array = new JSONArray(raw);
            List<HotkeyBinding> result = new ArrayList<>(array.length());

            for (int index = 0; index < array.length(); index++) {
                addBinding(result, array.optJSONObject(index));
            }

            return result.toArray(EMPTY_BINDINGS);
        } catch (JSONException exception) {
            Log.e(TAG, "Unable to parse hotkey bindings", exception);

            return EMPTY_BINDINGS;
        }
    }

    private static void addBinding(List<HotkeyBinding> result, JSONObject object) {
        if (object == null) {
            return;
        }

        int keyCode = object.optInt("keyCode", 0);
        String packageName = object.optString("packageName", "");
        String appName = object.optString("appName", "");

        if (keyCode <= 0 || packageName.isEmpty() || appName.isEmpty()) {
            return;
        }

        result.add(new HotkeyBinding(
                keyCode,
                object.optBoolean("ctrl", false),
                object.optBoolean("alt", false),
                object.optBoolean("shift", false),
                object.optBoolean("meta", false),
                packageName,
                appName));
    }

    private static String writeLanguageShortcut(LanguageSwitchShortcut shortcut) {
        JSONObject object = new JSONObject();

        try {
            object.put("keyCode", shortcut.keyCode());
            object.put("ctrl", shortcut.isCtrl());
            object.put("alt", shortcut.isAlt());
            object.put("shift", shortcut.isShift());
            object.put("meta", shortcut.isMeta());
        } catch (JSONException exception) {
            Log.e(TAG, "Unable to serialize language shortcut", exception);
        }

        return object.toString();
    }

    private static LanguageSwitchShortcut readLanguageShortcut(String raw) {
        if (raw == null || raw.isEmpty()) {
            return LanguageSwitchShortcut.DEFAULT;
        }

        try {
            JSONObject object = new JSONObject(raw);
            int keyCode = object.optInt("keyCode", 0);

            if (keyCode <= 0) {
                return LanguageSwitchShortcut.DEFAULT;
            }

            return new LanguageSwitchShortcut(
                    keyCode,
                    object.optBoolean("ctrl", false),
                    object.optBoolean("alt", true),
                    object.optBoolean("shift", true),
                    object.optBoolean("meta", false));
        } catch (JSONException exception) {
            Log.e(TAG, "Unable to parse language shortcut", exception);

            return LanguageSwitchShortcut.DEFAULT;
        }
    }
}
