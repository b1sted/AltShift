package ru.b1sted.altshift.ui;

import android.view.KeyEvent;

import ru.b1sted.altshift.model.HotkeyBinding;
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
        builder.append(keyName(keyCode));
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
            default:
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
