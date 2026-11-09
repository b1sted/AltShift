package ru.b1sted.altshift.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import android.view.KeyEvent;

import org.junit.Test;

public final class TransliterationTableTest {

    @Test
    public void mapsLowercaseLetters() {
        assertEquals("й", TransliterationTable.lookup(KeyEvent.KEYCODE_Q, false, false));
        assertEquals("я", TransliterationTable.lookup(KeyEvent.KEYCODE_Z, false, false));
        assertEquals("ё", TransliterationTable.lookup(KeyEvent.KEYCODE_GRAVE, false, false));
    }

    @Test
    public void mapsUppercaseWithShift() {
        assertEquals("Й", TransliterationTable.lookup(KeyEvent.KEYCODE_Q, true, false));
        assertEquals("Ю", TransliterationTable.lookup(KeyEvent.KEYCODE_PERIOD, true, false));
    }

    @Test
    public void capsLockInvertsShiftState() {
        assertEquals("Й", TransliterationTable.lookup(KeyEvent.KEYCODE_Q, false, true));
        assertEquals("й", TransliterationTable.lookup(KeyEvent.KEYCODE_Q, true, true));
    }

    @Test
    public void mapsPunctuation() {
        assertEquals("б", TransliterationTable.lookup(KeyEvent.KEYCODE_COMMA, false, false));
        assertEquals(".", TransliterationTable.lookup(KeyEvent.KEYCODE_SLASH, false, false));
        assertEquals(",", TransliterationTable.lookup(KeyEvent.KEYCODE_SLASH, true, false));
    }

    @Test
    public void returnsNullForUnmappedOrOutOfRangeKeys() {
        assertNull(TransliterationTable.lookup(KeyEvent.KEYCODE_1, false, false));
        assertNull(TransliterationTable.lookup(-1, false, false));
        assertNull(TransliterationTable.lookup(10_000, false, false));
    }
}
