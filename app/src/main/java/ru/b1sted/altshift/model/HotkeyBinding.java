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
            final int candidateKeyCode,
            final boolean ctrl,
            final boolean alt,
            final boolean shift,
            final boolean meta) {
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
