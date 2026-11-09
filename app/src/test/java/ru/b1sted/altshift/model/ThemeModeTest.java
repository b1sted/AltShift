package ru.b1sted.altshift.model;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class ThemeModeTest {

    @Test
    public void unknownKeysFallBackToSystem() {
        assertEquals(ThemeMode.FOLLOW_SYSTEM, ThemeMode.fromKey(null));
        assertEquals(ThemeMode.FOLLOW_SYSTEM, ThemeMode.fromKey("unsupported"));
    }

    @Test
    public void persistedKeysRoundTrip() {
        assertEquals(ThemeMode.FOLLOW_SYSTEM, ThemeMode.fromKey("system"));
        assertEquals(ThemeMode.LIGHT, ThemeMode.fromKey("light"));
        assertEquals(ThemeMode.DARK, ThemeMode.fromKey("dark"));
    }
}
