package ru.b1sted.altshift.model;

import android.view.KeyEvent;

public final class KeyMatcher {

    private KeyMatcher() {
    }

    public static boolean matches(
            final int bindingKeyCode,
            final boolean bindingCtrl,
            final boolean bindingAlt,
            final boolean bindingShift,
            final boolean bindingMeta,
            final int eventKeyCode,
            final boolean eventCtrl,
            final boolean eventAlt,
            final boolean eventShift,
            final boolean eventMeta) {
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

    public static boolean isModifierKey(final int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_SHIFT_LEFT:
            case KeyEvent.KEYCODE_SHIFT_RIGHT:
            case KeyEvent.KEYCODE_ALT_LEFT:
            case KeyEvent.KEYCODE_ALT_RIGHT:
            case KeyEvent.KEYCODE_CTRL_LEFT:
            case KeyEvent.KEYCODE_CTRL_RIGHT:
            case KeyEvent.KEYCODE_META_LEFT:
            case KeyEvent.KEYCODE_META_RIGHT:
                return true;
            default:
                return false;
        }
    }

    public static boolean isModifierActive(
            final int keyCode,
            final boolean ctrl,
            final boolean alt,
            final boolean shift,
            final boolean meta) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_SHIFT_LEFT:
            case KeyEvent.KEYCODE_SHIFT_RIGHT:
                return shift;
            case KeyEvent.KEYCODE_ALT_LEFT:
            case KeyEvent.KEYCODE_ALT_RIGHT:
                return alt;
            case KeyEvent.KEYCODE_CTRL_LEFT:
            case KeyEvent.KEYCODE_CTRL_RIGHT:
                return ctrl;
            case KeyEvent.KEYCODE_META_LEFT:
            case KeyEvent.KEYCODE_META_RIGHT:
                return meta;
            default:
                return false;
        }
    }
}
