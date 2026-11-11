package ru.b1sted.altshift.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.view.KeyEvent;

import org.junit.Test;

public final class ModelTest {

    @Test
    public void hotkeyMatchesExactModifierMask() {
        HotkeyBinding binding = new HotkeyBinding(
                KeyEvent.KEYCODE_A, true, false, true, false, "com.example", "Example");

        assertTrue(binding.matches(KeyEvent.KEYCODE_A, true, false, true, false));
        assertFalse(binding.matches(KeyEvent.KEYCODE_A, true, true, true, false));
        assertFalse(binding.matches(KeyEvent.KEYCODE_B, true, false, true, false));
    }

    @Test
    public void defaultLanguageShortcutIsAltShift() {
        LanguageSwitchShortcut shortcut = LanguageSwitchShortcut.DEFAULT;

        assertEquals(KeyEvent.KEYCODE_SHIFT_LEFT, shortcut.keyCode());
        assertFalse(shortcut.isCtrl());
        assertTrue(shortcut.isAlt());
        assertTrue(shortcut.isShift());
        assertFalse(shortcut.isMeta());
    }

    @Test
    public void modifierOnlyHotkeyMatchesEitherModifierOrder() {
        HotkeyBinding binding = new HotkeyBinding(
                KeyEvent.KEYCODE_SHIFT_LEFT, true, false, true, false,
                "com.example", "Example");

        assertTrue(binding.matches(KeyEvent.KEYCODE_CTRL_LEFT, true, false, true, false));
        assertTrue(binding.matches(KeyEvent.KEYCODE_SHIFT_RIGHT, true, false, true, false));
        assertFalse(binding.matches(KeyEvent.KEYCODE_A, true, false, true, false));
    }

    @Test
    public void languageShortcutMatchesEitherModifierOrder() {
        LanguageSwitchShortcut shortcut = LanguageSwitchShortcut.DEFAULT;

        assertTrue(shortcut.matches(KeyEvent.KEYCODE_ALT_LEFT, false, true, true, false));
        assertTrue(shortcut.matches(KeyEvent.KEYCODE_SHIFT_RIGHT, false, true, true, false));
        assertFalse(shortcut.matches(KeyEvent.KEYCODE_ALT_LEFT, false, true, false, false));
    }

    @Test(expected = NullPointerException.class)
    public void hotkeyRejectsNullPackageName() {
        new HotkeyBinding(KeyEvent.KEYCODE_A, false, false, false, false, null, "Example");
    }

    @Test(expected = IllegalArgumentException.class)
    public void hotkeyRejectsUnknownKeyCode() {
        new HotkeyBinding(0, false, false, false, false, "com.example", "Example");
    }

    @Test(expected = IllegalArgumentException.class)
    public void languageShortcutRejectsUnknownKeyCode() {
        new LanguageSwitchShortcut(0, false, false, false, false);
    }
}
