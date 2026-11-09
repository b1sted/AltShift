package ru.b1sted.altshift.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.view.KeyEvent;

import org.junit.Test;

public final class ModelTest {

    @Test
    public void hotkeyMatchesExactModifierMask() {
        final HotkeyBinding binding = new HotkeyBinding(
                KeyEvent.KEYCODE_A, true, false, true, false, "com.example", "Example");

        assertTrue(binding.matches(KeyEvent.KEYCODE_A, true, false, true, false));
        assertFalse(binding.matches(KeyEvent.KEYCODE_A, true, true, true, false));
        assertFalse(binding.matches(KeyEvent.KEYCODE_B, true, false, true, false));
    }

    @Test
    public void defaultLanguageShortcutIsAltShift() {
        final LanguageSwitchShortcut shortcut = LanguageSwitchShortcut.DEFAULT;

        assertEquals(KeyEvent.KEYCODE_SHIFT_LEFT, shortcut.keyCode());
        assertFalse(shortcut.isCtrl());
        assertTrue(shortcut.isAlt());
        assertTrue(shortcut.isShift());
        assertFalse(shortcut.isMeta());
    }

    @Test(expected = NullPointerException.class)
    public void hotkeyRejectsNullPackageName() {
        new HotkeyBinding(KeyEvent.KEYCODE_A, false, false, false, false, null, "Example");
    }

    @Test(expected = IllegalArgumentException.class)
    public void hotkeyRejectsUnknownKeyCode() {
        new HotkeyBinding(0, false, false, false, false, "com.example", "Example");
    }
}
