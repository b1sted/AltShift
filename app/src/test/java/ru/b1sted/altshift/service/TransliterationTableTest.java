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
    public void capsLockInvertsShiftStateForLetters() {
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
    public void mapsNumberRowWithRussianShiftSymbols() {
        assertEquals("1", TransliterationTable.lookup(KeyEvent.KEYCODE_1, false, false));
        assertEquals("!", TransliterationTable.lookup(KeyEvent.KEYCODE_1, true, false));
        assertEquals("2", TransliterationTable.lookup(KeyEvent.KEYCODE_2, false, false));
        assertEquals("\"", TransliterationTable.lookup(KeyEvent.KEYCODE_2, true, false));
        assertEquals("3", TransliterationTable.lookup(KeyEvent.KEYCODE_3, false, false));
        assertEquals("\u2116", TransliterationTable.lookup(KeyEvent.KEYCODE_3, true, false));
        assertEquals("4", TransliterationTable.lookup(KeyEvent.KEYCODE_4, false, false));
        assertEquals(";", TransliterationTable.lookup(KeyEvent.KEYCODE_4, true, false));
        assertEquals("5", TransliterationTable.lookup(KeyEvent.KEYCODE_5, false, false));
        assertEquals("%", TransliterationTable.lookup(KeyEvent.KEYCODE_5, true, false));
        assertEquals("6", TransliterationTable.lookup(KeyEvent.KEYCODE_6, false, false));
        assertEquals(":", TransliterationTable.lookup(KeyEvent.KEYCODE_6, true, false));
        assertEquals("7", TransliterationTable.lookup(KeyEvent.KEYCODE_7, false, false));
        assertEquals("?", TransliterationTable.lookup(KeyEvent.KEYCODE_7, true, false));
        assertEquals("8", TransliterationTable.lookup(KeyEvent.KEYCODE_8, false, false));
        assertEquals("*", TransliterationTable.lookup(KeyEvent.KEYCODE_8, true, false));
        assertEquals("9", TransliterationTable.lookup(KeyEvent.KEYCODE_9, false, false));
        assertEquals("(", TransliterationTable.lookup(KeyEvent.KEYCODE_9, true, false));
        assertEquals("0", TransliterationTable.lookup(KeyEvent.KEYCODE_0, false, false));
        assertEquals(")", TransliterationTable.lookup(KeyEvent.KEYCODE_0, true, false));
        assertEquals("-", TransliterationTable.lookup(KeyEvent.KEYCODE_MINUS, false, false));
        assertEquals("_", TransliterationTable.lookup(KeyEvent.KEYCODE_MINUS, true, false));
        assertEquals("=", TransliterationTable.lookup(KeyEvent.KEYCODE_EQUALS, false, false));
        assertEquals("+", TransliterationTable.lookup(KeyEvent.KEYCODE_EQUALS, true, false));
    }

    @Test
    public void capsLockDoesNotAffectNumberRow() {
        assertEquals("2", TransliterationTable.lookup(KeyEvent.KEYCODE_2, false, true));
        assertEquals("\"", TransliterationTable.lookup(KeyEvent.KEYCODE_2, true, true));
        assertEquals("6", TransliterationTable.lookup(KeyEvent.KEYCODE_6, false, true));
        assertEquals(":", TransliterationTable.lookup(KeyEvent.KEYCODE_6, true, true));
    }

    @Test
    public void capsLockDoesNotAffectSymbols() {
        assertEquals(".", TransliterationTable.lookup(KeyEvent.KEYCODE_SLASH, false, true));
        assertEquals(",", TransliterationTable.lookup(KeyEvent.KEYCODE_SLASH, true, true));
    }

    @Test
    public void returnsNullForUnmappedOrOutOfRangeKeys() {
        assertNull(TransliterationTable.lookup(KeyEvent.KEYCODE_TAB, false, false));
        assertNull(TransliterationTable.lookup(-1, false, false));
        assertNull(TransliterationTable.lookup(10_000, false, false));
    }
}
