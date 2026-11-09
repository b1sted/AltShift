package ru.b1sted.altshift.model;

import android.view.KeyEvent;

public record LanguageSwitchShortcut(
        int keyCode,
        boolean isCtrl,
        boolean isAlt,
        boolean isShift,
        boolean isMeta) {

    public static final LanguageSwitchShortcut DEFAULT =
            new LanguageSwitchShortcut(KeyEvent.KEYCODE_SHIFT_LEFT, false, true, true, false);

    public LanguageSwitchShortcut {
        if (keyCode <= 0) {
            throw new IllegalArgumentException("keyCode must be positive");
        }
    }

    public boolean matches(
            int candidateKeyCode,
            boolean ctrl,
            boolean alt,
            boolean shift,
            boolean meta) {
        return KeyMatcher.matches(
                keyCode,
                isCtrl,
                isAlt,
                isShift,
                isMeta,
                candidateKeyCode,
                ctrl,
                alt,
                shift,
                meta);
    }
}
