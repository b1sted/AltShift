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

public final class HotkeyRepository {

    private static final String TAG = "AltShift";
    private static final String PREFS_NAME = "altshift_prefs";
    private static final String KEY_HOTKEYS = "hotkeys";
    private static final String KEY_LANGUAGE_SHORTCUT = "language_shortcut";
    private static final HotkeyBinding[] EMPTY_BINDINGS = new HotkeyBinding[0];

    private static volatile HotkeyRepository instance;

    private final SharedPreferences preferences;
    private final Object lock = new Object();

    private volatile HotkeyBinding[] bindings;
    private volatile LanguageSwitchShortcut languageShortcut;

    private HotkeyRepository(final Context context) {
        preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        bindings = readBindings(preferences.getString(KEY_HOTKEYS, null));
        languageShortcut = readLanguageShortcut(
                preferences.getString(KEY_LANGUAGE_SHORTCUT, null));
    }

    public static HotkeyRepository getInstance(final Context context) {
        final Context appContext = Objects.requireNonNull(context, "context")
                .getApplicationContext();
        HotkeyRepository local = instance;
        if (local != null) {
            return local;
        }
        synchronized (HotkeyRepository.class) {
            local = instance;
            if (local == null) {
                local = new HotkeyRepository(appContext);
                instance = local;
            }
        }
        return local;
    }

    public HotkeyBinding[] getBindings() {
        return bindings.clone();
    }

    public LanguageSwitchShortcut getLanguageShortcut() {
        return languageShortcut;
    }

    public void saveBinding(final HotkeyBinding binding) {
        Objects.requireNonNull(binding, "binding");
        synchronized (lock) {
            final HotkeyBinding[] current = bindings;
            final HotkeyBinding[] updated = upsertBinding(current, binding);
            persistBindings(updated);
            bindings = updated;
        }
    }

    public void deleteBinding(final HotkeyBinding binding) {
        Objects.requireNonNull(binding, "binding");
        synchronized (lock) {
            final HotkeyBinding[] current = bindings;
            final HotkeyBinding[] updated = removeBinding(current, binding);
            persistBindings(updated);
            bindings = updated;
        }
    }

    public void saveLanguageShortcut(final LanguageSwitchShortcut shortcut) {
        Objects.requireNonNull(shortcut, "shortcut");
        synchronized (lock) {
            if (!preferences.edit()
                    .putString(KEY_LANGUAGE_SHORTCUT, writeLanguageShortcut(shortcut))
                    .commit()) {
                throw new IllegalStateException("Unable to persist language shortcut");
            }
            languageShortcut = shortcut;
        }
    }

    private void persistBindings(final HotkeyBinding[] updated) {
        if (!preferences.edit().putString(KEY_HOTKEYS, writeBindings(updated)).commit()) {
            throw new IllegalStateException("Unable to persist hotkey bindings");
        }
    }

    private static HotkeyBinding[] upsertBinding(
            final HotkeyBinding[] current, final HotkeyBinding binding) {
        for (int index = 0; index < current.length; index++) {
            if (current[index].matches(
                    binding.keyCode(),
                    binding.isCtrl(),
                    binding.isAlt(),
                    binding.isShift(),
                    binding.isMeta())) {
                final HotkeyBinding[] updated = current.clone();
                updated[index] = binding;
                return updated;
            }
        }
        final HotkeyBinding[] updated = new HotkeyBinding[current.length + 1];
        System.arraycopy(current, 0, updated, 0, current.length);
        updated[current.length] = binding;
        return updated;
    }

    private static HotkeyBinding[] removeBinding(
            final HotkeyBinding[] current, final HotkeyBinding binding) {
        int matchIndex = -1;
        for (int index = 0; index < current.length; index++) {
            if (current[index].matches(
                    binding.keyCode(),
                    binding.isCtrl(),
                    binding.isAlt(),
                    binding.isShift(),
                    binding.isMeta())) {
                matchIndex = index;
                break;
            }
        }
        if (matchIndex < 0) {
            return current;
        }
        final HotkeyBinding[] updated = new HotkeyBinding[current.length - 1];
        System.arraycopy(current, 0, updated, 0, matchIndex);
        System.arraycopy(current, matchIndex + 1, updated, matchIndex,
                current.length - matchIndex - 1);
        return updated;
    }

    private static String writeBindings(final HotkeyBinding[] items) {
        final JSONArray array = new JSONArray();
        for (final HotkeyBinding item : items) {
            final JSONObject object = new JSONObject();
            try {
                object.put("keyCode", item.keyCode());
                object.put("ctrl", item.isCtrl());
                object.put("alt", item.isAlt());
                object.put("shift", item.isShift());
                object.put("meta", item.isMeta());
                object.put("packageName", item.packageName());
                object.put("appName", item.appName());
            } catch (final JSONException exception) {
                Log.e(TAG, "Unable to serialize hotkey binding", exception);
                continue;
            }
            array.put(object);
        }
        return array.toString();
    }

    private static HotkeyBinding[] readBindings(final String raw) {
        if (raw == null || raw.isEmpty()) {
            return EMPTY_BINDINGS;
        }
        try {
            final JSONArray array = new JSONArray(raw);
            final List<HotkeyBinding> result = new ArrayList<>(array.length());
            for (int index = 0; index < array.length(); index++) {
                final JSONObject object = array.optJSONObject(index);
                if (object == null) {
                    continue;
                }
                final int keyCode = object.optInt("keyCode", 0);
                final String packageName = object.optString("packageName", "");
                final String appName = object.optString("appName", "");
                if (keyCode <= 0 || packageName.isEmpty() || appName.isEmpty()) {
                    continue;
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
            return result.toArray(EMPTY_BINDINGS);
        } catch (final JSONException exception) {
            Log.e(TAG, "Unable to parse hotkey bindings", exception);
            return EMPTY_BINDINGS;
        }
    }

    private static String writeLanguageShortcut(final LanguageSwitchShortcut shortcut) {
        final JSONObject object = new JSONObject();
        try {
            object.put("keyCode", shortcut.keyCode());
            object.put("ctrl", shortcut.isCtrl());
            object.put("alt", shortcut.isAlt());
            object.put("shift", shortcut.isShift());
            object.put("meta", shortcut.isMeta());
        } catch (final JSONException exception) {
            Log.e(TAG, "Unable to serialize language shortcut", exception);
        }
        return object.toString();
    }

    private static LanguageSwitchShortcut readLanguageShortcut(final String raw) {
        if (raw == null || raw.isEmpty()) {
            return LanguageSwitchShortcut.DEFAULT;
        }
        try {
            final JSONObject object = new JSONObject(raw);
            final int keyCode = object.optInt("keyCode", 0);
            if (keyCode <= 0) {
                return LanguageSwitchShortcut.DEFAULT;
            }
            return new LanguageSwitchShortcut(
                    keyCode,
                    object.optBoolean("ctrl", false),
                    object.optBoolean("alt", true),
                    object.optBoolean("shift", true),
                    object.optBoolean("meta", false));
        } catch (final JSONException exception) {
            Log.e(TAG, "Unable to parse language shortcut", exception);
            return LanguageSwitchShortcut.DEFAULT;
        }
    }
}
