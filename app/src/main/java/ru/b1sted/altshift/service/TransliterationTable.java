package ru.b1sted.altshift.service;

import android.view.KeyEvent;

final class TransliterationTable {

    private static final int TABLE_SIZE = 320;
    private static final String[] LOWER = new String[TABLE_SIZE];
    private static final String[] UPPER = new String[TABLE_SIZE];
    private static final boolean[] LETTER = new boolean[TABLE_SIZE];

    static {
        putLetter(KeyEvent.KEYCODE_GRAVE, "ё", "Ё");
        putLetter(KeyEvent.KEYCODE_Q, "й", "Й");
        putLetter(KeyEvent.KEYCODE_W, "ц", "Ц");
        putLetter(KeyEvent.KEYCODE_E, "у", "У");
        putLetter(KeyEvent.KEYCODE_R, "к", "К");
        putLetter(KeyEvent.KEYCODE_T, "е", "Е");
        putLetter(KeyEvent.KEYCODE_Y, "н", "Н");
        putLetter(KeyEvent.KEYCODE_U, "г", "Г");
        putLetter(KeyEvent.KEYCODE_I, "ш", "Ш");
        putLetter(KeyEvent.KEYCODE_O, "щ", "Щ");
        putLetter(KeyEvent.KEYCODE_P, "з", "З");
        putLetter(KeyEvent.KEYCODE_LEFT_BRACKET, "х", "Х");
        putLetter(KeyEvent.KEYCODE_RIGHT_BRACKET, "ъ", "Ъ");
        putLetter(KeyEvent.KEYCODE_A, "ф", "Ф");
        putLetter(KeyEvent.KEYCODE_S, "ы", "Ы");
        putLetter(KeyEvent.KEYCODE_D, "в", "В");
        putLetter(KeyEvent.KEYCODE_F, "а", "А");
        putLetter(KeyEvent.KEYCODE_G, "п", "П");
        putLetter(KeyEvent.KEYCODE_H, "р", "Р");
        putLetter(KeyEvent.KEYCODE_J, "о", "О");
        putLetter(KeyEvent.KEYCODE_K, "л", "Л");
        putLetter(KeyEvent.KEYCODE_L, "д", "Д");
        putLetter(KeyEvent.KEYCODE_SEMICOLON, "ж", "Ж");
        putLetter(KeyEvent.KEYCODE_APOSTROPHE, "э", "Э");
        putLetter(KeyEvent.KEYCODE_Z, "я", "Я");
        putLetter(KeyEvent.KEYCODE_X, "ч", "Ч");
        putLetter(KeyEvent.KEYCODE_C, "с", "С");
        putLetter(KeyEvent.KEYCODE_V, "м", "М");
        putLetter(KeyEvent.KEYCODE_B, "и", "И");
        putLetter(KeyEvent.KEYCODE_N, "т", "Т");
        putLetter(KeyEvent.KEYCODE_M, "ь", "Ь");
        putLetter(KeyEvent.KEYCODE_COMMA, "б", "Б");
        putLetter(KeyEvent.KEYCODE_PERIOD, "ю", "Ю");

        putSymbol(KeyEvent.KEYCODE_SLASH, ".", ",");
        putSymbol(KeyEvent.KEYCODE_BACKSLASH, "\\", "/");

        putSymbol(KeyEvent.KEYCODE_1, "1", "!");
        putSymbol(KeyEvent.KEYCODE_2, "2", "\"");
        putSymbol(KeyEvent.KEYCODE_3, "3", "\u2116");
        putSymbol(KeyEvent.KEYCODE_4, "4", ";");
        putSymbol(KeyEvent.KEYCODE_5, "5", "%");
        putSymbol(KeyEvent.KEYCODE_6, "6", ":");
        putSymbol(KeyEvent.KEYCODE_7, "7", "?");
        putSymbol(KeyEvent.KEYCODE_8, "8", "*");
        putSymbol(KeyEvent.KEYCODE_9, "9", "(");
        putSymbol(KeyEvent.KEYCODE_0, "0", ")");
        putSymbol(KeyEvent.KEYCODE_MINUS, "-", "_");
        putSymbol(KeyEvent.KEYCODE_EQUALS, "=", "+");
    }

    private TransliterationTable() {
    }

    static String lookup(int keyCode, boolean shifted, boolean capsLock) {
        if (keyCode < 0 || keyCode >= TABLE_SIZE) {
            return null;
        }

        boolean effectiveShift = LETTER[keyCode] ? shifted ^ capsLock : shifted;

        return effectiveShift ? UPPER[keyCode] : LOWER[keyCode];
    }

    private static void putLetter(int keyCode, String lower, String upper) {
        if (keyCode <= 0 || keyCode >= TABLE_SIZE) {
            return;
        }

        LOWER[keyCode] = lower;
        UPPER[keyCode] = upper;
        LETTER[keyCode] = true;
    }

    private static void putSymbol(int keyCode, String unshifted, String shifted) {
        if (keyCode <= 0 || keyCode >= TABLE_SIZE) {
            return;
        }

        LOWER[keyCode] = unshifted;
        UPPER[keyCode] = shifted;
    }
}
