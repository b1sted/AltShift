package ru.b1sted.altshift.model;

import java.util.Objects;

public record HotkeyBinding(
        int keyCode,
        boolean isCtrl,
        boolean isAlt,
        boolean isShift,
        boolean isMeta,
        String packageName,
        String appName) {

    public HotkeyBinding {
        if (keyCode <= 0) {
            throw new IllegalArgumentException("keyCode must be positive");
        }

        packageName = Objects.requireNonNull(packageName, "packageName");
        appName = Objects.requireNonNull(appName, "appName");
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

    public boolean conflictsWith(LanguageSwitchShortcut shortcut) {
        Objects.requireNonNull(shortcut, "shortcut");

        return matches(
                shortcut.keyCode(),
                shortcut.isCtrl(),
                shortcut.isAlt(),
                shortcut.isShift(),
                shortcut.isMeta());
    }
}
