package ru.b1sted.altshift.ui;

import android.content.Context;

import androidx.appcompat.app.AppCompatDelegate;

import ru.b1sted.altshift.model.ThemeMode;
import ru.b1sted.altshift.storage.HotkeyRepository;

/**
 * Applies the persisted theme mode to the whole application.
 */
public final class ThemeController {

    private ThemeController() {
    }

    public static void apply(Context context) {
        HotkeyRepository repository = HotkeyRepository.getInstance(context);
        AppCompatDelegate.setDefaultNightMode(nightMode(repository.getThemeMode()));
    }

    public static void saveAndApply(Context context, ThemeMode mode) {
        HotkeyRepository repository = HotkeyRepository.getInstance(context);
        repository.saveThemeMode(mode);
        AppCompatDelegate.setDefaultNightMode(nightMode(mode));
    }

    private static int nightMode(ThemeMode mode) {
        if (mode == ThemeMode.DARK) {
            return AppCompatDelegate.MODE_NIGHT_YES;
        }

        if (mode == ThemeMode.LIGHT) {
            return AppCompatDelegate.MODE_NIGHT_NO;
        }

        return AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
    }
}
