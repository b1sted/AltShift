package ru.b1sted.altshift.service;

import android.view.KeyEvent;

final class TransliterationTable {

    private static final int TABLE_SIZE = 320;
    private static final String[] LOWER = new String[TABLE_SIZE];
    private static final String[] UPPER = new String[TABLE_SIZE];

    static {
        put(KeyEvent.KEYCODE_GRAVE, "ё", "Ё");
        put(KeyEvent.KEYCODE_Q, "й", "Й");
        put(KeyEvent.KEYCODE_W, "ц", "Ц");
        put(KeyEvent.KEYCODE_E, "у", "У");
        put(KeyEvent.KEYCODE_R, "к", "К");
        put(KeyEvent.KEYCODE_T, "е", "Е");
        put(KeyEvent.KEYCODE_Y, "н", "Н");
        put(KeyEvent.KEYCODE_U, "г", "Г");
        put(KeyEvent.KEYCODE_I, "ш", "Ш");
        put(KeyEvent.KEYCODE_O, "щ", "Щ");
        put(KeyEvent.KEYCODE_P, "з", "З");
        put(KeyEvent.KEYCODE_LEFT_BRACKET, "х", "Х");
        put(KeyEvent.KEYCODE_RIGHT_BRACKET, "ъ", "Ъ");
        put(KeyEvent.KEYCODE_A, "ф", "Ф");
        put(KeyEvent.KEYCODE_S, "ы", "Ы");
        put(KeyEvent.KEYCODE_D, "в", "В");
        put(KeyEvent.KEYCODE_F, "а", "А");
        put(KeyEvent.KEYCODE_G, "п", "П");
        put(KeyEvent.KEYCODE_H, "р", "Р");
        put(KeyEvent.KEYCODE_J, "о", "О");
        put(KeyEvent.KEYCODE_K, "л", "Л");
        put(KeyEvent.KEYCODE_L, "д", "Д");
        put(KeyEvent.KEYCODE_SEMICOLON, "ж", "Ж");
        put(KeyEvent.KEYCODE_APOSTROPHE, "э", "Э");
        put(KeyEvent.KEYCODE_Z, "я", "Я");
        put(KeyEvent.KEYCODE_X, "ч", "Ч");
        put(KeyEvent.KEYCODE_C, "с", "С");
        put(KeyEvent.KEYCODE_V, "м", "М");
        put(KeyEvent.KEYCODE_B, "и", "И");
        put(KeyEvent.KEYCODE_N, "т", "Т");
        put(KeyEvent.KEYCODE_M, "ь", "Ь");
        put(KeyEvent.KEYCODE_COMMA, "б", "Б");
        put(KeyEvent.KEYCODE_PERIOD, "ю", "Ю");
        put(KeyEvent.KEYCODE_SLASH, ".", ",");
        put(KeyEvent.KEYCODE_BACKSLASH, "\\", "/");

        put(KeyEvent.KEYCODE_1, "1", "!");
        put(KeyEvent.KEYCODE_2, "2", "\"");
        put(KeyEvent.KEYCODE_3, "3", "\u2116");
        put(KeyEvent.KEYCODE_4, "4", ";");
        put(KeyEvent.KEYCODE_5, "5", "%");
        put(KeyEvent.KEYCODE_6, "6", ":");
        put(KeyEvent.KEYCODE_7, "7", "?");
        put(KeyEvent.KEYCODE_8, "8", "*");
        put(KeyEvent.KEYCODE_9, "9", "(");
        put(KeyEvent.KEYCODE_0, "0", ")");
        put(KeyEvent.KEYCODE_MINUS, "-", "_");
        put(KeyEvent.KEYCODE_EQUALS, "=", "+");
    }

    private TransliterationTable() {
    }

    static String lookup(int keyCode, boolean shifted, boolean capsLock) {
        if (keyCode < 0 || keyCode >= TABLE_SIZE) {
            return null;
        }

        return (shifted ^ capsLock) ? UPPER[keyCode] : LOWER[keyCode];
    }

    private static void put(int keyCode, String lower, String upper) {
        if (keyCode <= 0 || keyCode >= TABLE_SIZE) {
            return;
        }

        LOWER[keyCode] = lower;
        UPPER[keyCode] = upper;
    }
}
