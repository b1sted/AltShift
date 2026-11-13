package ru.b1sted.altshift.ui;

import android.content.Context;
import android.content.res.Configuration;
import android.util.Log;
import android.view.ContextThemeWrapper;

import androidx.appcompat.app.AppCompatDelegate;

import ru.b1sted.altshift.R;
import ru.b1sted.altshift.model.ThemeMode;
import ru.b1sted.altshift.storage.HotkeyRepository;

/**
 * Applies the persisted theme mode to the whole application.
 */
public final class ThemeController {

    private static final String TAG = "AltShift";

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

    /**
     * Resolves a context that carries both the selected per-app locale and the
     * persisted night-mode override. Components outside the AppCompat activity
     * lifecycle, such as the Dynamic Island overlay and the IME fallback view,
     * must inflate through this context so Material 3 attributes resolve and
     * the in-app theme choice wins over the OS default.
     */
    public static Context themedContext(Context context) {
        if (context == null) {
            return null;
        }

        Context localized = LocaleController.localizedContext(context);
        if (localized == null) {
            return null;
        }

        ThemeMode mode = resolveThemeMode(context);
        if (mode == ThemeMode.FOLLOW_SYSTEM) {
            return new ContextThemeWrapper(localized, R.style.Theme_AltShift);
        }

        Configuration configuration = new Configuration(
                localized.getResources().getConfiguration());
        configuration.uiMode = (configuration.uiMode & ~Configuration.UI_MODE_NIGHT_MASK)
                | nightModeConfiguration(mode);

        return new ContextThemeWrapper(
                localized.createConfigurationContext(configuration),
                R.style.Theme_AltShift);
    }

    private static ThemeMode resolveThemeMode(Context context) {
        try {
            return HotkeyRepository.getInstance(context).getThemeMode();
        } catch (RuntimeException exception) {
            Log.w(TAG, "Unable to read the persisted theme mode", exception);

            return ThemeMode.FOLLOW_SYSTEM;
        }
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

    static int nightModeConfiguration(ThemeMode mode) {
        return switch (mode) {
            case LIGHT -> Configuration.UI_MODE_NIGHT_NO;
            case DARK -> Configuration.UI_MODE_NIGHT_YES;
            default -> Configuration.UI_MODE_NIGHT_UNDEFINED;
        };
    }
}
