package ru.b1sted.altshift.ui;

import static org.junit.Assert.assertEquals;

import android.content.res.Configuration;

import org.junit.Test;

import ru.b1sted.altshift.model.ThemeMode;

public final class ThemeControllerTest {

    @Test
    public void mapsPersistedThemeToConfigurationNightMode() {
        assertEquals(Configuration.UI_MODE_NIGHT_NO,
                ThemeController.nightModeConfiguration(ThemeMode.LIGHT));
        assertEquals(Configuration.UI_MODE_NIGHT_YES,
                ThemeController.nightModeConfiguration(ThemeMode.DARK));
        assertEquals(Configuration.UI_MODE_NIGHT_UNDEFINED,
                ThemeController.nightModeConfiguration(ThemeMode.FOLLOW_SYSTEM));
    }
}
