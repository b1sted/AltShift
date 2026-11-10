package ru.b1sted.altshift;

import android.app.Application;

import ru.b1sted.altshift.service.NotificationChannels;
import ru.b1sted.altshift.ui.ThemeController;

public final class AltShiftApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        NotificationChannels.deleteLegacy(this);
        ThemeController.apply(this);
    }
}
