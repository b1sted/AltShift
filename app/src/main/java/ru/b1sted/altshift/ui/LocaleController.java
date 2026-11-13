package ru.b1sted.altshift.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.LocaleList;
import android.util.Log;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

import java.util.Locale;

import ru.b1sted.altshift.model.AppLanguage;
import ru.b1sted.altshift.service.KeyboardWatchdogService;
import ru.b1sted.altshift.service.NotificationChannels;

/**
 * Owns the per-app language selection and exposes a localized context for
 * components that live outside the AppCompat activity lifecycle.
 */
public final class LocaleController {

    private static final String TAG = "AltShift";

    private LocaleController() {
    }

    public static AppLanguage getSelected(Context context) {
        LocaleListCompat locales = AppCompatDelegate.getApplicationLocales();
        if (locales.isEmpty()) {
            return AppLanguage.FOLLOW_SYSTEM;
        }

        Locale language = locales.get(0);
        if (language == null) {
            return AppLanguage.FOLLOW_SYSTEM;
        }

        return AppLanguage.fromTag(language.toLanguageTag());
    }

    public static void applySelection(Context context, AppLanguage language) {
        if (context == null || language == null) {
            return;
        }

        LocaleListCompat locales = language == AppLanguage.FOLLOW_SYSTEM
                ? LocaleListCompat.getEmptyLocaleList()
                : LocaleListCompat.forLanguageTags(language.tag());

        if (Looper.myLooper() == Looper.getMainLooper()) {
            applyLocales(context, locales);

            return;
        }

        new Handler(Looper.getMainLooper()).post(() -> applyLocales(context, locales));
    }

    /**
     * Resolves a context that already carries the selected per-app locale on
     * Android 8.0 through 12. Android 13 and newer apply per-app locales to the
     * application, services, and receivers through the platform LocaleManager,
     * so those releases return the original context unchanged.
     *
     * <p>AltShift ships as a single APK, so runtime locale changes never need
     * Play Core language downloads or bundle language splits.</p>
     */
    @SuppressLint("AppBundleLocaleChanges")
    public static Context localizedContext(Context context) {
        if (context == null) {
            return null;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return context;
        }

        LocaleListCompat locales = AppCompatDelegate.getApplicationLocales();
        if (locales.isEmpty()) {
            return context;
        }

        Locale language = locales.get(0);
        if (language == null) {
            return context;
        }

        try {
            Configuration configuration = new Configuration(
                    context.getResources().getConfiguration());
            configuration.setLocales(new LocaleList(language));

            return context.createConfigurationContext(configuration);
        } catch (RuntimeException exception) {
            Log.w(TAG, "Unable to create a localized context", exception);

            return context;
        }
    }

    /**
     * Reapplies localized notification channel names and refreshes the posted
     * watchdog text. A cold start on Android 8.0 through 12 initializes the
     * channels before AppCompat has loaded the stored locales, so the dashboard
     * calls this once its activity is attached.
     */
    public static void refreshLocalizedNotifications(Context context) {
        if (context == null) {
            return;
        }

        Context appContext = context.getApplicationContext();
        if (appContext == null) {
            return;
        }

        NotificationChannels.initialize(appContext);
        KeyboardWatchdogService.refreshNotificationIfRunning(appContext);
    }

    private static void applyLocales(Context context, LocaleListCompat locales) {
        try {
            AppCompatDelegate.setApplicationLocales(locales);
        } catch (RuntimeException exception) {
            Log.w(TAG, "Unable to apply the application locales", exception);

            return;
        }

        refreshLocalizedNotifications(context);
    }
}
