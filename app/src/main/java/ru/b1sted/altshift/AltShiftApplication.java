package ru.b1sted.altshift;

import android.app.Application;
import android.util.Log;

import ru.b1sted.altshift.service.NotificationChannels;
import ru.b1sted.altshift.ui.ThemeController;

public final class AltShiftApplication extends Application {

    private static final String TAG = "AltShift";

    @Override
    public void onCreate() {
        super.onCreate();

        try {
            NotificationChannels.initialize(this);
        } catch (RuntimeException exception) {
            Log.e(TAG, "Unable to initialize notification channels", exception);
        }

        try {
            ThemeController.apply(this);
        } catch (RuntimeException exception) {
            Log.e(TAG, "Unable to apply the persisted theme", exception);
        }
    }
}
