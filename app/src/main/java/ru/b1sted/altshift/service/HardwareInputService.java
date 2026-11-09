package ru.b1sted.altshift.service;

import android.content.Intent;
import android.inputmethodservice.InputMethodService;
import android.util.Log;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

import java.util.Objects;

import ru.b1sted.altshift.model.HotkeyBinding;
import ru.b1sted.altshift.model.LanguageSwitchShortcut;
import ru.b1sted.altshift.storage.HotkeyRepository;

public final class HardwareInputService extends InputMethodService {

    private static final String TAG = "AltShift";

    private volatile HotkeyBinding[] bindings = new HotkeyBinding[0];
    private volatile LanguageSwitchShortcut languageShortcut = LanguageSwitchShortcut.DEFAULT;

    private HotkeyRepository repository;
    private boolean isRussian;

    @Override
    public void onCreate() {
        super.onCreate();
        repository = Objects.requireNonNull(
                HotkeyRepository.getInstance(this), "repository");
        reloadConfiguration();
    }

    @Override
    public void onStartInput(final EditorInfo attribute, final boolean restarting) {
        super.onStartInput(attribute, restarting);
        reloadConfiguration();
    }

    @Override
    public boolean onEvaluateInputViewShown() {
        super.onEvaluateInputViewShown();
        return false;
    }

    @Override
    public boolean onShowInputRequested(final int flags, final boolean configChange) {
        super.onShowInputRequested(flags, configChange);
        return false;
    }

    @Override
    public boolean onKeyDown(final int keyCode, final KeyEvent event) {
        if (event == null) {
            return false;
        }
        if (event.getRepeatCount() == 0) {
            if (isLanguageToggle(keyCode, event)) {
                isRussian = !isRussian;
                return true;
            }
            if (launchHotkeyApp(keyCode, event)) {
                return true;
            }
        }
        if (isRussian && !event.isCtrlPressed() && !event.isMetaPressed()) {
            final String mapped = TransliterationTable.lookup(
                    keyCode, event.isShiftPressed(), event.isCapsLockOn());
            if (mapped != null && commitMapped(mapped)) {
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    private void reloadConfiguration() {
        final HotkeyRepository local = repository;
        if (local == null) {
            return;
        }
        try {
            bindings = local.getBindings();
            languageShortcut = local.getLanguageShortcut();
        } catch (final RuntimeException exception) {
            Log.e(TAG, "Unable to reload configuration", exception);
        }
    }

    private boolean isLanguageToggle(final int keyCode, final KeyEvent event) {
        final LanguageSwitchShortcut shortcut = languageShortcut;
        return shortcut != null && shortcut.matches(
                keyCode,
                event.isCtrlPressed(),
                event.isAltPressed(),
                event.isShiftPressed(),
                event.isMetaPressed());
    }

    private boolean launchHotkeyApp(final int keyCode, final KeyEvent event) {
        final HotkeyBinding[] snapshot = bindings;
        for (int index = 0; index < snapshot.length; index++) {
            final HotkeyBinding binding = snapshot[index];
            if (!binding.matches(
                    keyCode,
                    event.isCtrlPressed(),
                    event.isAltPressed(),
                    event.isShiftPressed(),
                    event.isMetaPressed())) {
                continue;
            }
            launchApplication(binding);
            return true;
        }
        return false;
    }

    private void launchApplication(final HotkeyBinding binding) {
        try {
            final Intent intent = getPackageManager()
                    .getLaunchIntentForPackage(binding.packageName());
            if (intent == null) {
                Log.w(TAG, "No launch intent for " + binding.packageName());
                return;
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (final RuntimeException exception) {
            Log.e(TAG, "Unable to launch " + binding.packageName(), exception);
        }
    }

    private boolean commitMapped(final String mapped) {
        final String text = Objects.requireNonNull(mapped, "mapped");
        final InputConnection connection = getCurrentInputConnection();
        if (connection == null) {
            return false;
        }
        try {
            connection.commitText(text, 1);
            return true;
        } catch (final RuntimeException exception) {
            Log.e(TAG, "Unable to commit text", exception);
            return false;
        }
    }
}
