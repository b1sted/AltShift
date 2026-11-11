package ru.b1sted.altshift.ui;

import static org.junit.Assert.assertEquals;

import android.view.KeyEvent;

import org.junit.Test;

import ru.b1sted.altshift.model.LanguageSwitchShortcut;

public final class KeyLabelFormatterTest {

    @Test
    public void formatsDefaultLanguageShortcutOnce() {
        assertEquals("Alt+Shift", KeyLabelFormatter.format(LanguageSwitchShortcut.DEFAULT));
    }

    @Test
    public void formatsLetterWithModifier() {
        assertEquals("Ctrl+A",
                KeyLabelFormatter.format(KeyEvent.KEYCODE_A, true, false, false, false));
    }

    @Test
    public void formatsModifierOnlyShortcut() {
        assertEquals("Ctrl+Shift",
                KeyLabelFormatter.format(KeyEvent.KEYCODE_SHIFT_LEFT, true, false, true, false));
    }

    @Test
    public void formatsSingleModifier() {
        assertEquals("Meta",
                KeyLabelFormatter.format(KeyEvent.KEYCODE_META_LEFT, false, false, false, true));
    }

    @Test
    public void formatsPlainLettersAndDigits() {
        assertEquals("A",
                KeyLabelFormatter.format(KeyEvent.KEYCODE_A, false, false, false, false));
        assertEquals("7",
                KeyLabelFormatter.format(KeyEvent.KEYCODE_7, false, false, false, false));
    }
}
