package ru.b1sted.altshift.ui;

import android.view.KeyEvent;

import ru.b1sted.altshift.model.HotkeyBinding;
import ru.b1sted.altshift.model.KeyMatcher;
import ru.b1sted.altshift.model.LanguageSwitchShortcut;

public final class KeyLabelFormatter {

    private KeyLabelFormatter() {
    }

    public static String format(final HotkeyBinding binding) {
        return format(
                binding.keyCode(),
                binding.isCtrl(),
                binding.isAlt(),
                binding.isShift(),
                binding.isMeta());
    }

    public static String format(final LanguageSwitchShortcut shortcut) {
        return format(
                shortcut.keyCode(),
                shortcut.isCtrl(),
                shortcut.isAlt(),
                shortcut.isShift(),
                shortcut.isMeta());
    }

    public static String format(
            final int keyCode,
            final boolean ctrl,
            final boolean alt,
            final boolean shift,
            final boolean meta) {
        final StringBuilder builder = new StringBuilder(24);
        if (ctrl) {
            builder.append("Ctrl+");
        }
        if (alt) {
            builder.append("Alt+");
        }
        if (shift) {
            builder.append("Shift+");
        }
        if (meta) {
            builder.append("Meta+");
        }
        if (KeyMatcher.isModifierKey(keyCode)
                && KeyMatcher.isModifierActive(keyCode, ctrl, alt, shift, meta)) {
            if (builder.length() > 0 && builder.charAt(builder.length() - 1) == '+') {
                builder.setLength(builder.length() - 1);
            }
        } else {
            builder.append(keyName(keyCode));
        }
        return builder.toString();
    }

    private static String keyName(final int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_SHIFT_LEFT:
            case KeyEvent.KEYCODE_SHIFT_RIGHT:
                return "Shift";
            case KeyEvent.KEYCODE_ALT_LEFT:
            case KeyEvent.KEYCODE_ALT_RIGHT:
                return "Alt";
            case KeyEvent.KEYCODE_CTRL_LEFT:
            case KeyEvent.KEYCODE_CTRL_RIGHT:
                return "Ctrl";
            case KeyEvent.KEYCODE_META_LEFT:
            case KeyEvent.KEYCODE_META_RIGHT:
                return "Meta";
            case KeyEvent.KEYCODE_SPACE:
                return "Space";
            case KeyEvent.KEYCODE_ENTER:
                return "Enter";
            case KeyEvent.KEYCODE_TAB:
                return "Tab";
            case KeyEvent.KEYCODE_GRAVE:
                return "`";
            case KeyEvent.KEYCODE_MINUS:
                return "-";
            case KeyEvent.KEYCODE_EQUALS:
                return "=";
            case KeyEvent.KEYCODE_LEFT_BRACKET:
                return "[";
            case KeyEvent.KEYCODE_RIGHT_BRACKET:
                return "]";
            case KeyEvent.KEYCODE_BACKSLASH:
                return "\\";
            case KeyEvent.KEYCODE_SEMICOLON:
                return ";";
            case KeyEvent.KEYCODE_APOSTROPHE:
                return "'";
            case KeyEvent.KEYCODE_COMMA:
                return ",";
            case KeyEvent.KEYCODE_PERIOD:
                return ".";
            case KeyEvent.KEYCODE_SLASH:
                return "/";
            case KeyEvent.KEYCODE_DEL:
                return "Backspace";
            case KeyEvent.KEYCODE_FORWARD_DEL:
                return "Delete";
            case KeyEvent.KEYCODE_ESCAPE:
                return "Esc";
            case KeyEvent.KEYCODE_DPAD_UP:
                return "Up";
            case KeyEvent.KEYCODE_DPAD_DOWN:
                return "Down";
            case KeyEvent.KEYCODE_DPAD_LEFT:
                return "Left";
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                return "Right";
            case KeyEvent.KEYCODE_MOVE_HOME:
                return "Home";
            case KeyEvent.KEYCODE_MOVE_END:
                return "End";
            case KeyEvent.KEYCODE_PAGE_UP:
                return "Page Up";
            case KeyEvent.KEYCODE_PAGE_DOWN:
                return "Page Down";
            default:
                if (keyCode >= KeyEvent.KEYCODE_A && keyCode <= KeyEvent.KEYCODE_Z) {
                    return String.valueOf(
                            (char) ('A' + keyCode - KeyEvent.KEYCODE_A));
                }
                if (keyCode >= KeyEvent.KEYCODE_0 && keyCode <= KeyEvent.KEYCODE_9) {
                    return String.valueOf(
                            (char) ('0' + keyCode - KeyEvent.KEYCODE_0));
                }
                final String name = KeyEvent.keyCodeToString(keyCode);
                if (name == null) {
                    return "Key " + keyCode;
                }
                if (name.startsWith("KEYCODE_")) {
                    return name.substring(8).replace('_', ' ');
                }
                return name;
        }
    }
}
