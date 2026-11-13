package ru.b1sted.altshift.storage;

import java.util.Objects;

import ru.b1sted.altshift.model.HotkeyBinding;
import ru.b1sted.altshift.model.LanguageSwitchShortcut;

/**
 * Pure array transformations for the persisted hotkey bindings.
 *
 * <p>Kept free of Android and {@code org.json} dependencies so the binding
 * rules can be exercised directly on the JVM.
 */
public final class HotkeyBindingStore {

    private HotkeyBindingStore() {
    }

    public static HotkeyBinding[] upsert(
            HotkeyBinding[] current, HotkeyBinding binding) {
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(binding, "binding");

        for (int index = 0; index < current.length; index++) {
            if (!sharesTrigger(current[index], binding)) {
                continue;
            }

            if (current[index].equals(binding)) {
                return current;
            }

            HotkeyBinding[] updated = current.clone();
            updated[index] = binding;

            return updated;
        }

        HotkeyBinding[] updated = new HotkeyBinding[current.length + 1];
        System.arraycopy(current, 0, updated, 0, current.length);
        updated[current.length] = binding;

        return updated;
    }

    public static HotkeyBinding[] remove(
            HotkeyBinding[] current, HotkeyBinding binding) {
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(binding, "binding");

        int matchIndex = indexOfTrigger(current, binding);
        if (matchIndex < 0) {
            return current;
        }

        HotkeyBinding[] updated = new HotkeyBinding[current.length - 1];
        System.arraycopy(current, 0, updated, 0, matchIndex);
        System.arraycopy(current, matchIndex + 1, updated, matchIndex,
                current.length - matchIndex - 1);

        return updated;
    }

    public static boolean conflictsWith(
            HotkeyBinding[] bindings, LanguageSwitchShortcut shortcut) {
        Objects.requireNonNull(bindings, "bindings");
        Objects.requireNonNull(shortcut, "shortcut");

        for (int index = 0; index < bindings.length; index++) {
            if (bindings[index].conflictsWith(shortcut)) {
                return true;
            }
        }

        return false;
    }

    public static boolean conflictsWith(
            HotkeyBinding[] bindings,
            int keyCode,
            boolean ctrl,
            boolean alt,
            boolean shift,
            boolean meta) {
        Objects.requireNonNull(bindings, "bindings");

        for (int index = 0; index < bindings.length; index++) {
            if (bindings[index].matches(keyCode, ctrl, alt, shift, meta)) {
                return true;
            }
        }

        return false;
    }

    private static boolean sharesTrigger(
            HotkeyBinding current, HotkeyBinding candidate) {
        return current.matches(
                candidate.keyCode(),
                candidate.isCtrl(),
                candidate.isAlt(),
                candidate.isShift(),
                candidate.isMeta());
    }

    private static int indexOfTrigger(
            HotkeyBinding[] bindings, HotkeyBinding candidate) {
        for (int index = 0; index < bindings.length; index++) {
            if (sharesTrigger(bindings[index], candidate)) {
                return index;
            }
        }

        return -1;
    }
}
