package ru.b1sted.altshift.ui;

import android.view.KeyEvent;

import ru.b1sted.altshift.model.HotkeyBinding;
import ru.b1sted.altshift.model.KeyMatcher;
import ru.b1sted.altshift.model.LanguageSwitchShortcut;

public final class KeyLabelFormatter {

    private KeyLabelFormatter() {
    }

    public static String format(HotkeyBinding binding) {
        return format(
                binding.keyCode(),
                binding.isCtrl(),
                binding.isAlt(),
                binding.isShift(),
                binding.isMeta());
    }

    public static String format(LanguageSwitchShortcut shortcut) {
        return format(
                shortcut.keyCode(),
                shortcut.isCtrl(),
                shortcut.isAlt(),
                shortcut.isShift(),
                shortcut.isMeta());
    }

    public static String format(
            int keyCode,
            boolean ctrl,
            boolean alt,
            boolean shift,
            boolean meta) {
        StringBuilder builder = new StringBuilder(24);

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

    private static String keyName(int keyCode) {
        return switch (keyCode) {
            case KeyEvent.KEYCODE_SHIFT_LEFT,
                 KeyEvent.KEYCODE_SHIFT_RIGHT -> "Shift";
            case KeyEvent.KEYCODE_ALT_LEFT,
                 KeyEvent.KEYCODE_ALT_RIGHT -> "Alt";
            case KeyEvent.KEYCODE_CTRL_LEFT,
                 KeyEvent.KEYCODE_CTRL_RIGHT -> "Ctrl";
            case KeyEvent.KEYCODE_META_LEFT,
                 KeyEvent.KEYCODE_META_RIGHT -> "Meta";
            case KeyEvent.KEYCODE_SPACE -> "Space";
            case KeyEvent.KEYCODE_ENTER -> "Enter";
            case KeyEvent.KEYCODE_TAB -> "Tab";
            case KeyEvent.KEYCODE_GRAVE -> "`";
            case KeyEvent.KEYCODE_MINUS -> "-";
            case KeyEvent.KEYCODE_EQUALS -> "=";
            case KeyEvent.KEYCODE_LEFT_BRACKET -> "[";
            case KeyEvent.KEYCODE_RIGHT_BRACKET -> "]";
            case KeyEvent.KEYCODE_BACKSLASH -> "\\";
            case KeyEvent.KEYCODE_SEMICOLON -> ";";
            case KeyEvent.KEYCODE_APOSTROPHE -> "'";
            case KeyEvent.KEYCODE_COMMA -> ",";
            case KeyEvent.KEYCODE_PERIOD -> ".";
            case KeyEvent.KEYCODE_SLASH -> "/";
            case KeyEvent.KEYCODE_DEL -> "Backspace";
            case KeyEvent.KEYCODE_FORWARD_DEL -> "Delete";
            case KeyEvent.KEYCODE_ESCAPE -> "Esc";
            case KeyEvent.KEYCODE_DPAD_UP -> "Up";
            case KeyEvent.KEYCODE_DPAD_DOWN -> "Down";
            case KeyEvent.KEYCODE_DPAD_LEFT -> "Left";
            case KeyEvent.KEYCODE_DPAD_RIGHT -> "Right";
            case KeyEvent.KEYCODE_MOVE_HOME -> "Home";
            case KeyEvent.KEYCODE_MOVE_END -> "End";
            case KeyEvent.KEYCODE_PAGE_UP -> "Page Up";
            case KeyEvent.KEYCODE_PAGE_DOWN -> "Page Down";
            default -> fallbackKeyName(keyCode);
        };
    }

    private static String fallbackKeyName(int keyCode) {
        if (keyCode >= KeyEvent.KEYCODE_A && keyCode <= KeyEvent.KEYCODE_Z) {
            return String.valueOf((char) ('A' + keyCode - KeyEvent.KEYCODE_A));
        }

        if (keyCode >= KeyEvent.KEYCODE_0 && keyCode <= KeyEvent.KEYCODE_9) {
            return String.valueOf((char) ('0' + keyCode - KeyEvent.KEYCODE_0));
        }

        String name = KeyEvent.keyCodeToString(keyCode);
        if (name == null) {
            return "Key " + keyCode;
        }

        if (name.startsWith("KEYCODE_")) {
            return name.substring(8).replace('_', ' ');
        }

        return name;
    }
}
