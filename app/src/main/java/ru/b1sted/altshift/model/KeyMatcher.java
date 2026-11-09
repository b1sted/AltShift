package ru.b1sted.altshift.model;

import android.view.KeyEvent;

public final class KeyMatcher {

    private KeyMatcher() {
    }

    public static boolean matches(
            int bindingKeyCode,
            boolean bindingCtrl,
            boolean bindingAlt,
            boolean bindingShift,
            boolean bindingMeta,
            int eventKeyCode,
            boolean eventCtrl,
            boolean eventAlt,
            boolean eventShift,
            boolean eventMeta) {
        if (bindingCtrl != eventCtrl
                || bindingAlt != eventAlt
                || bindingShift != eventShift
                || bindingMeta != eventMeta) {
            return false;
        }

        if (bindingKeyCode == eventKeyCode) {
            return true;
        }

        return isModifierKey(bindingKeyCode)
                && isModifierKey(eventKeyCode)
                && isModifierActive(eventKeyCode, eventCtrl, eventAlt, eventShift, eventMeta);
    }

    public static boolean isModifierKey(int keyCode) {
        return switch (keyCode) {
            case KeyEvent.KEYCODE_SHIFT_LEFT,
                 KeyEvent.KEYCODE_SHIFT_RIGHT,
                 KeyEvent.KEYCODE_ALT_LEFT,
                 KeyEvent.KEYCODE_ALT_RIGHT,
                 KeyEvent.KEYCODE_CTRL_LEFT,
                 KeyEvent.KEYCODE_CTRL_RIGHT,
                 KeyEvent.KEYCODE_META_LEFT,
                 KeyEvent.KEYCODE_META_RIGHT -> true;
            default -> false;
        };
    }

    public static boolean isModifierActive(
            int keyCode,
            boolean ctrl,
            boolean alt,
            boolean shift,
            boolean meta) {
        return switch (keyCode) {
            case KeyEvent.KEYCODE_SHIFT_LEFT,
                 KeyEvent.KEYCODE_SHIFT_RIGHT -> shift;
            case KeyEvent.KEYCODE_ALT_LEFT,
                 KeyEvent.KEYCODE_ALT_RIGHT -> alt;
            case KeyEvent.KEYCODE_CTRL_LEFT,
                 KeyEvent.KEYCODE_CTRL_RIGHT -> ctrl;
            case KeyEvent.KEYCODE_META_LEFT,
                 KeyEvent.KEYCODE_META_RIGHT -> meta;
            default -> false;
        };
    }
}
