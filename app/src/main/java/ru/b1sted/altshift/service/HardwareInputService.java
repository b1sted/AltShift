package ru.b1sted.altshift.service;

import android.content.ComponentName;
import android.content.Intent;
import android.content.res.Configuration;
import android.hardware.input.InputManager;
import android.inputmethodservice.InputMethodService;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;

import java.util.Objects;

import ru.b1sted.altshift.databinding.ViewImeFallbackBinding;
import ru.b1sted.altshift.model.HotkeyBinding;
import ru.b1sted.altshift.model.LanguageSwitchShortcut;
import ru.b1sted.altshift.storage.HotkeyRepository;

public final class HardwareInputService extends InputMethodService {

    private static final String TAG = "AltShift";

    private final InputManager.InputDeviceListener deviceListener =
            new InputManager.InputDeviceListener() {
                @Override
                public void onInputDeviceAdded(final int deviceId) {
                    handleKeyboardStateChange();
                }

                @Override
                public void onInputDeviceRemoved(final int deviceId) {
                    handleKeyboardStateChange();
                }

                @Override
                public void onInputDeviceChanged(final int deviceId) {
                    handleKeyboardStateChange();
                }
            };

    private volatile HotkeyBinding[] bindings = new HotkeyBinding[0];
    private volatile LanguageSwitchShortcut languageShortcut = LanguageSwitchShortcut.DEFAULT;

    private HotkeyRepository repository;
    private InputManager inputManager;
    private boolean isRussian;
    private boolean keyboardPresent;
    private boolean listenerRegistered;

    @Override
    public void onCreate() {
        super.onCreate();
        keyboardPresent = KeyboardPresenceMonitor.isPhysicalKeyboardAvailable(this);
        registerDeviceListener();
        repository = Objects.requireNonNull(
                HotkeyRepository.getInstance(this), "repository");
        reloadConfiguration();
    }

    @Override
    public void onDestroy() {
        unregisterDeviceListener();
        super.onDestroy();
    }

    @Override
    public void onConfigurationChanged(final Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        handleKeyboardStateChange();
    }

    @Override
    public void onStartInput(final EditorInfo attribute, final boolean restarting) {
        super.onStartInput(attribute, restarting);
        reloadConfiguration();
    }

    @Override
    public View onCreateInputView() {
        final ViewImeFallbackBinding viewBinding =
                ViewImeFallbackBinding.inflate(getLayoutInflater());
        viewBinding.switchKeyboardButton.setOnClickListener(
                view -> switchToSoftKeyboard(true));
        return viewBinding.getRoot();
    }

    @Override
    public boolean onEvaluateInputViewShown() {
        super.onEvaluateInputViewShown();
        return !KeyboardPresenceMonitor.isPhysicalKeyboardAvailable(this);
    }

    @Override
    public boolean onShowInputRequested(final int flags, final boolean configChange) {
        if (KeyboardPresenceMonitor.isPhysicalKeyboardAvailable(this)) {
            return false;
        }
        return super.onShowInputRequested(flags, configChange);
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

    private void handleKeyboardStateChange() {
        final boolean present = KeyboardPresenceMonitor.isPhysicalKeyboardAvailable(this);
        if (present != keyboardPresent) {
            keyboardPresent = present;
            if (present) {
                if (!isActiveInputMethod()) {
                    KeyboardNotification.showReconnected(this);
                }
            } else {
                switchToSoftKeyboard(false);
            }
        }
        updateInputViewShown();
    }

    private void switchToSoftKeyboard(final boolean showPickerOnFailure) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                if (switchToPreviousInputMethod()) {
                    return;
                }
                if (switchToNextInputMethod(false)) {
                    return;
                }
            }
            if (showPickerOnFailure) {
                final InputMethodManager manager =
                        getSystemService(InputMethodManager.class);
                if (manager != null) {
                    manager.showInputMethodPicker();
                }
            }
        } catch (final RuntimeException exception) {
            Log.w(TAG, "Unable to switch to the soft keyboard", exception);
        }
    }

    private boolean isActiveInputMethod() {
        try {
            final String active = Settings.Secure.getString(
                    getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);
            if (active == null) {
                return false;
            }
            final ComponentName component =
                    new ComponentName(this, HardwareInputService.class);
            return active.contains(component.flattenToString())
                    || active.contains(component.flattenToShortString());
        } catch (final RuntimeException exception) {
            Log.w(TAG, "Unable to read the active input method", exception);
            return false;
        }
    }

    private void registerDeviceListener() {
        try {
            inputManager = getSystemService(InputManager.class);
            if (inputManager == null) {
                return;
            }
            inputManager.registerInputDeviceListener(deviceListener, null);
            listenerRegistered = true;
        } catch (final RuntimeException exception) {
            Log.e(TAG, "Unable to register input device listener", exception);
        }
    }

    private void unregisterDeviceListener() {
        if (!listenerRegistered) {
            return;
        }
        listenerRegistered = false;
        try {
            final InputManager manager = inputManager;
            if (manager != null) {
                manager.unregisterInputDeviceListener(deviceListener);
            }
        } catch (final RuntimeException exception) {
            Log.w(TAG, "Unable to unregister input device listener", exception);
        } finally {
            inputManager = null;
        }
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
