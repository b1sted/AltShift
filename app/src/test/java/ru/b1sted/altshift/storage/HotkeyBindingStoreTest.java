package ru.b1sted.altshift.storage;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.view.KeyEvent;

import org.junit.Test;

import ru.b1sted.altshift.model.HotkeyBinding;
import ru.b1sted.altshift.model.LanguageSwitchShortcut;

public final class HotkeyBindingStoreTest {

    private static final HotkeyBinding[] EMPTY = new HotkeyBinding[0];

    @Test
    public void upsertAppendsUnknownTrigger() {
        HotkeyBinding existing = binding(
                KeyEvent.KEYCODE_A, false, false, false, false, "com.one", "One");
        HotkeyBinding added = binding(
                KeyEvent.KEYCODE_B, false, false, false, false, "com.two", "Two");

        HotkeyBinding[] updated =
                HotkeyBindingStore.upsert(new HotkeyBinding[]{existing}, added);

        assertEquals(2, updated.length);
        assertSame(existing, updated[0]);
        assertSame(added, updated[1]);
    }

    @Test
    public void upsertReplacesBindingWithSameTrigger() {
        HotkeyBinding existing = binding(
                KeyEvent.KEYCODE_A, true, false, false, false, "com.one", "One");
        HotkeyBinding replacement = binding(
                KeyEvent.KEYCODE_A, true, false, false, false, "com.two", "Two");
        HotkeyBinding[] current = new HotkeyBinding[]{existing};

        HotkeyBinding[] updated = HotkeyBindingStore.upsert(current, replacement);

        assertEquals(1, updated.length);
        assertEquals(replacement, updated[0]);
        assertSame(existing, current[0]);
    }

    @Test
    public void upsertReturnsSameArrayForIdenticalBinding() {
        HotkeyBinding existing = binding(
                KeyEvent.KEYCODE_A, true, false, false, false, "com.one", "One");
        HotkeyBinding[] current = new HotkeyBinding[]{existing};

        assertSame(current, HotkeyBindingStore.upsert(current, existing));
    }

    @Test
    public void upsertTreatsEquivalentModifierKeysAsSameTrigger() {
        HotkeyBinding existing = binding(
                KeyEvent.KEYCODE_SHIFT_LEFT, false, true, true, false, "com.one", "One");
        HotkeyBinding replacement = binding(
                KeyEvent.KEYCODE_SHIFT_RIGHT, false, true, true, false, "com.two", "Two");

        HotkeyBinding[] updated =
                HotkeyBindingStore.upsert(new HotkeyBinding[]{existing}, replacement);

        assertEquals(1, updated.length);
        assertEquals(replacement, updated[0]);
    }

    @Test
    public void removeDeletesMatchingTriggerAndShiftsFollowingBindings() {
        HotkeyBinding first = binding(
                KeyEvent.KEYCODE_A, false, false, false, false, "com.one", "One");
        HotkeyBinding middle = binding(
                KeyEvent.KEYCODE_B, false, false, false, false, "com.two", "Two");
        HotkeyBinding last = binding(
                KeyEvent.KEYCODE_C, false, false, false, false, "com.three", "Three");
        HotkeyBinding[] current = new HotkeyBinding[]{first, middle, last};

        HotkeyBinding[] updated = HotkeyBindingStore.remove(current, middle);

        assertArrayEquals(new HotkeyBinding[]{first, last}, updated);
        assertEquals(3, current.length);
    }

    @Test
    public void removeReturnsSameArrayWhenTriggerIsMissing() {
        HotkeyBinding existing = binding(
                KeyEvent.KEYCODE_A, false, false, false, false, "com.one", "One");
        HotkeyBinding missing = binding(
                KeyEvent.KEYCODE_B, false, false, false, false, "com.two", "Two");
        HotkeyBinding[] current = new HotkeyBinding[]{existing};

        assertSame(current, HotkeyBindingStore.remove(current, missing));
    }

    @Test
    public void removeMatchesEquivalentModifierKeys() {
        HotkeyBinding existing = binding(
                KeyEvent.KEYCODE_ALT_LEFT, false, true, true, false, "com.one", "One");
        HotkeyBinding equivalent = binding(
                KeyEvent.KEYCODE_ALT_RIGHT, false, true, true, false, "com.two", "Two");

        HotkeyBinding[] updated =
                HotkeyBindingStore.remove(new HotkeyBinding[]{existing}, equivalent);

        assertEquals(0, updated.length);
    }

    @Test
    public void removeHandlesEmptyArray() {
        HotkeyBinding missing = binding(
                KeyEvent.KEYCODE_A, false, false, false, false, "com.one", "One");

        assertSame(EMPTY, HotkeyBindingStore.remove(EMPTY, missing));
    }

    @Test
    public void conflictsWithLanguageShortcutDetectsAnyBinding() {
        HotkeyBinding unrelated = binding(
                KeyEvent.KEYCODE_A, true, false, false, false, "com.one", "One");
        HotkeyBinding conflict = binding(
                KeyEvent.KEYCODE_SHIFT_RIGHT, false, true, true, false, "com.two", "Two");

        assertTrue(HotkeyBindingStore.conflictsWith(
                new HotkeyBinding[]{unrelated, conflict},
                LanguageSwitchShortcut.DEFAULT));
    }

    @Test
    public void conflictsWithLanguageShortcutIgnoresDifferentModifierMask() {
        HotkeyBinding unrelated = binding(
                KeyEvent.KEYCODE_SHIFT_LEFT, false, true, false, false, "com.one", "One");

        assertFalse(HotkeyBindingStore.conflictsWith(
                new HotkeyBinding[]{unrelated},
                LanguageSwitchShortcut.DEFAULT));
    }

    @Test
    public void conflictsWithTriggerArgumentsMatchesModifierOrder() {
        HotkeyBinding binding = binding(
                KeyEvent.KEYCODE_SHIFT_RIGHT, false, true, true, false, "com.one", "One");

        assertTrue(HotkeyBindingStore.conflictsWith(
                new HotkeyBinding[]{binding},
                KeyEvent.KEYCODE_ALT_LEFT, false, true, true, false));
        assertFalse(HotkeyBindingStore.conflictsWith(
                new HotkeyBinding[]{binding},
                KeyEvent.KEYCODE_ALT_LEFT, false, true, false, false));
    }

    private static HotkeyBinding binding(
            int keyCode,
            boolean ctrl,
            boolean alt,
            boolean shift,
            boolean meta,
            String packageName,
            String appName) {
        return new HotkeyBinding(keyCode, ctrl, alt, shift, meta, packageName, appName);
    }
}
