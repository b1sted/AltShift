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

    @Test
    public void appHotkeyConflictsWithMatchingLanguageShortcut() {
        HotkeyBinding binding = new HotkeyBinding(
                KeyEvent.KEYCODE_SHIFT_RIGHT, false, true, true, false,
                "com.example", "Example");

        assertTrue(binding.conflictsWith(LanguageSwitchShortcut.DEFAULT));
    }

    @Test
    public void appHotkeyDoesNotConflictWithAnotherLanguageShortcut() {
        HotkeyBinding binding = new HotkeyBinding(
                KeyEvent.KEYCODE_A, true, false, false, false,
                "com.example", "Example");

        assertFalse(binding.conflictsWith(LanguageSwitchShortcut.DEFAULT));
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

    @Test
    public void modifierKeyDetectionRejectsNonModifierKeys() {
        assertTrue(KeyMatcher.isModifierKey(KeyEvent.KEYCODE_SHIFT_LEFT));
        assertTrue(KeyMatcher.isModifierKey(KeyEvent.KEYCODE_SHIFT_RIGHT));
        assertTrue(KeyMatcher.isModifierKey(KeyEvent.KEYCODE_ALT_LEFT));
        assertTrue(KeyMatcher.isModifierKey(KeyEvent.KEYCODE_ALT_RIGHT));
        assertTrue(KeyMatcher.isModifierKey(KeyEvent.KEYCODE_CTRL_LEFT));
        assertTrue(KeyMatcher.isModifierKey(KeyEvent.KEYCODE_CTRL_RIGHT));
        assertTrue(KeyMatcher.isModifierKey(KeyEvent.KEYCODE_META_LEFT));
        assertTrue(KeyMatcher.isModifierKey(KeyEvent.KEYCODE_META_RIGHT));

        assertFalse(KeyMatcher.isModifierKey(KeyEvent.KEYCODE_A));
        assertFalse(KeyMatcher.isModifierKey(KeyEvent.KEYCODE_SPACE));
    }

    @Test
    public void modifierActivationIgnoresNonModifierKeyCodes() {
        assertFalse(KeyMatcher.isModifierActive(
                KeyEvent.KEYCODE_A, true, true, true, true));
        assertFalse(KeyMatcher.isModifierActive(
                KeyEvent.KEYCODE_SPACE, true, true, true, true));
    }

    @Test
    public void modifierActivationTracksEachModifierFlag() {
        assertTrue(KeyMatcher.isModifierActive(
                KeyEvent.KEYCODE_CTRL_LEFT, true, false, false, false));
        assertTrue(KeyMatcher.isModifierActive(
                KeyEvent.KEYCODE_ALT_RIGHT, false, true, false, false));
        assertTrue(KeyMatcher.isModifierActive(
                KeyEvent.KEYCODE_SHIFT_RIGHT, false, false, true, false));
        assertTrue(KeyMatcher.isModifierActive(
                KeyEvent.KEYCODE_META_LEFT, false, false, false, true));

        assertFalse(KeyMatcher.isModifierActive(
                KeyEvent.KEYCODE_CTRL_LEFT, false, false, false, false));
        assertFalse(KeyMatcher.isModifierActive(
                KeyEvent.KEYCODE_ALT_RIGHT, false, false, false, false));
        assertFalse(KeyMatcher.isModifierActive(
                KeyEvent.KEYCODE_SHIFT_RIGHT, false, false, false, false));
        assertFalse(KeyMatcher.isModifierActive(
                KeyEvent.KEYCODE_META_LEFT, false, false, false, false));
    }

    @Test
    public void modifierOnlyHotkeyRejectsDifferentModifierMask() {
        HotkeyBinding binding = new HotkeyBinding(
                KeyEvent.KEYCODE_SHIFT_LEFT, false, true, true, false,
                "com.example", "Example");

        assertFalse(binding.matches(
                KeyEvent.KEYCODE_ALT_LEFT, false, true, false, false));
    }

    @Test
    public void modifierBindingDoesNotMatchRegularKeyEvent() {
        HotkeyBinding binding = new HotkeyBinding(
                KeyEvent.KEYCODE_SHIFT_LEFT, false, true, true, false,
                "com.example", "Example");

        assertFalse(binding.matches(
                KeyEvent.KEYCODE_A, false, true, true, false));
    }

    @Test
    public void regularBindingDoesNotMatchModifierKeyEvent() {
        HotkeyBinding binding = new HotkeyBinding(
                KeyEvent.KEYCODE_A, false, true, true, false,
                "com.example", "Example");

        assertFalse(binding.matches(
                KeyEvent.KEYCODE_ALT_LEFT, false, true, true, false));
    }

    @Test
    public void languageShortcutConflictRequiresMatchingModifierMask() {
        HotkeyBinding binding = new HotkeyBinding(
                KeyEvent.KEYCODE_SHIFT_LEFT, false, true, false, false,
                "com.example", "Example");

        assertFalse(binding.conflictsWith(LanguageSwitchShortcut.DEFAULT));
    }

    @Test
    public void languageShortcutConflictMatchesEquivalentModifierOrder() {
        HotkeyBinding binding = new HotkeyBinding(
                KeyEvent.KEYCODE_ALT_RIGHT, false, true, true, false,
                "com.example", "Example");

        assertTrue(binding.conflictsWith(LanguageSwitchShortcut.DEFAULT));
    }
}
