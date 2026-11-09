package ru.b1sted.altshift.service;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.hardware.input.InputManager;
import android.inputmethodservice.InputMethodService;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;

import java.util.Objects;

import ru.b1sted.altshift.R;
import ru.b1sted.altshift.databinding.ViewImeFallbackBinding;
import ru.b1sted.altshift.model.HotkeyBinding;
import ru.b1sted.altshift.model.LanguageSwitchShortcut;
import ru.b1sted.altshift.storage.HotkeyRepository;
import ru.b1sted.altshift.ui.SwitchBackActivity;

public final class HardwareInputService extends InputMethodService {

    private static final String TAG = "AltShift";
    private static final String INPUT_THREAD_NAME = "AltShift-Input";

    private final InputManager.InputDeviceListener deviceListener =
            new InputManager.InputDeviceListener() {
                @Override
                public void onInputDeviceAdded(int deviceId) {
                    handleKeyboardStateChange();
                }

                @Override
                public void onInputDeviceRemoved(int deviceId) {
                    handleKeyboardStateChange();
                }

                @Override
                public void onInputDeviceChanged(int deviceId) {
                    handleKeyboardStateChange();
                }
            };

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Runnable keyboardCheckTask = this::handleKeyboardStateChange;
    private final Runnable refreshInputViewTask = this::refreshInputView;

    private volatile HotkeyBinding[] bindings = new HotkeyBinding[0];
    private volatile LanguageSwitchShortcut languageShortcut = LanguageSwitchShortcut.DEFAULT;
    private volatile boolean keyboardPresent;
    private volatile boolean alternativeImeAvailable = true;
    private volatile boolean shuttingDown;

    private HotkeyRepository repository;
    private InputManager inputManager;
    private HandlerThread inputThread;
    private Handler inputHandler;
    private ViewImeFallbackBinding fallbackBinding;
    private boolean isRussian;
    private boolean listenerRegistered;

    @Override
    public void onCreate() {
        super.onCreate();

        keyboardPresent = KeyboardPresenceMonitor.isPhysicalKeyboardAvailable(this);
        registerDeviceListener();

        repository = Objects.requireNonNull(
                HotkeyRepository.getInstance(this), "repository");
        reloadConfiguration();
        postKeyboardCheck();
    }

    @Override
    public void onDestroy() {
        shuttingDown = true;

        unregisterDeviceListener();
        stopInputThread();
        mainHandler.removeCallbacksAndMessages(null);
        fallbackBinding = null;

        super.onDestroy();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        postKeyboardCheck();
    }

    @Override
    public void onStartInput(EditorInfo attribute, boolean restarting) {
        super.onStartInput(attribute, restarting);

        reloadConfiguration();
        postKeyboardCheck();
    }

    @Override
    public View onCreateInputView() {
        fallbackBinding = ViewImeFallbackBinding.inflate(getLayoutInflater());
        fallbackBinding.switchKeyboardButton.setOnClickListener(
                view -> switchToSoftKeyboard(true));
        fallbackBinding.openKeyboardSettingsButton.setOnClickListener(
                view -> openKeyboardSettings());

        applyFallbackState();

        return fallbackBinding.getRoot();
    }

    @Override
    public boolean onEvaluateInputViewShown() {
        super.onEvaluateInputViewShown();

        return !keyboardPresent;
    }

    @Override
    public boolean onShowInputRequested(int flags, boolean configChange) {
        if (keyboardPresent) {
            return false;
        }

        return super.onShowInputRequested(flags, configChange);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
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

        if (!isRussian || event.isCtrlPressed() || event.isMetaPressed()) {
            return super.onKeyDown(keyCode, event);
        }

        String mapped = TransliterationTable.lookup(
                keyCode, event.isShiftPressed(), event.isCapsLockOn());
        if (mapped == null) {
            return super.onKeyDown(keyCode, event);
        }

        if (commitMapped(mapped)) {
            return true;
        }

        return super.onKeyDown(keyCode, event);
    }

    private void postKeyboardCheck() {
        Handler handler = inputHandler;
        if (handler == null) {
            return;
        }

        handler.post(keyboardCheckTask);
    }

    private void handleKeyboardStateChange() {
        if (shuttingDown) {
            return;
        }

        boolean present = KeyboardPresenceMonitor.isPhysicalKeyboardAvailable(this);
        boolean alternative = InputMethodSwitcher.hasAlternativeInputMethod(this);
        boolean changed = present != keyboardPresent;

        alternativeImeAvailable = alternative;
        if (changed) {
            keyboardPresent = present;
            updateKeyboardState(present);
        }

        mainHandler.post(refreshInputViewTask);
    }

    private void refreshInputView() {
        if (shuttingDown) {
            return;
        }

        updateInputViewShown();
        applyFallbackState();
    }

    private void applyFallbackState() {
        ViewImeFallbackBinding local = fallbackBinding;
        if (local == null) {
            return;
        }

        boolean stranded = !keyboardPresent && !alternativeImeAvailable;

        local.fallbackMessage.setText(stranded
                ? R.string.ime_fallback_no_keyboard_message
                : R.string.ime_fallback_message);
        local.switchKeyboardButton.setVisibility(stranded ? View.GONE : View.VISIBLE);
        local.openKeyboardSettingsButton.setVisibility(
                stranded ? View.VISIBLE : View.GONE);
    }

    private void updateKeyboardState(boolean present) {
        boolean autoSwitch = isAutoSwitchEnabled();

        if (present) {
            if (autoSwitch && InputMethodSwitcher.switchToAltShift(this)) {
                return;
            }

            if (!isActiveInputMethod()) {
                showReconnectPrompt();
            }

            return;
        }

        KeyboardPromptOverlay.dismiss();

        if (!alternativeImeAvailable) {
            return;
        }

        if (autoSwitch && InputMethodSwitcher.switchToSoftKeyboard(this)) {
            return;
        }

        switchToSoftKeyboard(false);
    }

    private void showReconnectPrompt() {
        KeyboardNotification.cancel(this);

        Context appContext = getApplicationContext();
        boolean shown = KeyboardPromptOverlay.show(
                appContext, () -> SwitchBackActivity.start(appContext));
        if (shown) {
            return;
        }

        KeyboardNotification.showReconnected(appContext);
    }

    private boolean isAutoSwitchEnabled() {
        HotkeyRepository local = repository;
        if (local == null) {
            return false;
        }

        return local.isAutoSwitchEnabled()
                && InputMethodSwitcher.hasSecureSettingsPermission(this);
    }

    private void switchToSoftKeyboard(boolean showPickerOnFailure) {
        if (!alternativeImeAvailable) {
            openKeyboardSettings();

            return;
        }

        try {
            if (switchToAvailableInputMethod()) {
                return;
            }

            if (showPickerOnFailure) {
                showInputMethodPicker();
            }
        } catch (RuntimeException exception) {
            Log.w(TAG, "Unable to switch to the soft keyboard", exception);
        }
    }

    private boolean switchToAvailableInputMethod() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            return false;
        }

        if (switchToPreviousInputMethod()) {
            return true;
        }

        return switchToNextInputMethod(false);
    }

    private void showInputMethodPicker() {
        InputMethodManager manager = getSystemService(InputMethodManager.class);
        if (manager == null) {
            return;
        }

        manager.showInputMethodPicker();
    }

    private void openKeyboardSettings() {
        Intent intent = new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        try {
            startActivity(intent);
        } catch (RuntimeException exception) {
            Log.w(TAG, "Unable to open keyboard settings", exception);
        }
    }

    private boolean isActiveInputMethod() {
        return InputMethodSwitcher.isAltShiftActive(this);
    }

    private void registerDeviceListener() {
        try {
            inputThread = new HandlerThread(INPUT_THREAD_NAME);
            inputThread.start();
            inputHandler = new Handler(inputThread.getLooper());

            inputManager = getSystemService(InputManager.class);
            if (inputManager == null) {
                return;
            }

            inputManager.registerInputDeviceListener(deviceListener, inputHandler);
            listenerRegistered = true;
        } catch (RuntimeException exception) {
            Log.e(TAG, "Unable to register input device listener", exception);
        }
    }

    private void unregisterDeviceListener() {
        if (!listenerRegistered) {
            return;
        }

        listenerRegistered = false;

        try {
            InputManager manager = inputManager;
            if (manager != null) {
                manager.unregisterInputDeviceListener(deviceListener);
            }
        } catch (RuntimeException exception) {
            Log.w(TAG, "Unable to unregister input device listener", exception);
        } finally {
            inputManager = null;
        }
    }

    private void stopInputThread() {
        HandlerThread thread = inputThread;
        inputThread = null;
        inputHandler = null;

        if (thread == null) {
            return;
        }

        thread.quitSafely();
    }

    private void reloadConfiguration() {
        HotkeyRepository local = repository;
        if (local == null) {
            return;
        }

        try {
            bindings = local.getBindings();
            languageShortcut = local.getLanguageShortcut();
        } catch (RuntimeException exception) {
            Log.e(TAG, "Unable to reload configuration", exception);
        }
    }

    private boolean isLanguageToggle(int keyCode, KeyEvent event) {
        LanguageSwitchShortcut shortcut = languageShortcut;
        if (shortcut == null) {
            return false;
        }

        return shortcut.matches(
                keyCode,
                event.isCtrlPressed(),
                event.isAltPressed(),
                event.isShiftPressed(),
                event.isMetaPressed());
    }

    private boolean launchHotkeyApp(int keyCode, KeyEvent event) {
        boolean ctrl = event.isCtrlPressed();
        boolean alt = event.isAltPressed();
        boolean shift = event.isShiftPressed();
        boolean meta = event.isMetaPressed();

        if (!ctrl && !alt && !shift && !meta) {
            return false;
        }

        HotkeyBinding[] snapshot = bindings;

        for (int index = 0; index < snapshot.length; index++) {
            HotkeyBinding binding = snapshot[index];
            if (!binding.matches(keyCode, ctrl, alt, shift, meta)) {
                continue;
            }

            launchApplication(binding);

            return true;
        }

        return false;
    }

    private void launchApplication(HotkeyBinding binding) {
        try {
            Intent intent = getPackageManager()
                    .getLaunchIntentForPackage(binding.packageName());
            if (intent == null) {
                Log.w(TAG, "No launch intent for " + binding.packageName());

                return;
            }

            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (RuntimeException exception) {
            Log.e(TAG, "Unable to launch " + binding.packageName(), exception);
        }
    }

    private boolean commitMapped(String mapped) {
        String text = Objects.requireNonNull(mapped, "mapped");
        InputConnection connection = getCurrentInputConnection();

        if (connection == null) {
            return false;
        }

        try {
            connection.commitText(text, 1);

            return true;
        } catch (RuntimeException exception) {
            Log.e(TAG, "Unable to commit text", exception);

            return false;
        }
    }
}
