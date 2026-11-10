package ru.b1sted.altshift.service;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.hardware.input.InputManager;
import android.inputmethodservice.InputMethodService;
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

import java.lang.ref.WeakReference;
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

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Runnable refreshInputViewTask = this::refreshInputView;

    private volatile HotkeyBinding[] bindings = new HotkeyBinding[0];
    private volatile LanguageSwitchShortcut languageShortcut = LanguageSwitchShortcut.DEFAULT;
    private volatile boolean keyboardPresent;
    private volatile boolean alternativeImeAvailable = true;
    private volatile boolean shuttingDown;

    private HotkeyRepository repository;
    private ViewImeFallbackBinding fallbackBinding;
    private boolean isRussian;

    @Override
    public void onCreate() {
        super.onCreate();

        repository = Objects.requireNonNull(
                HotkeyRepository.getInstance(this), "repository");
        reloadConfiguration();

        State state = KeyboardMonitor.acquire(this);
        keyboardPresent = state.keyboardPresent();
        alternativeImeAvailable = state.alternativeImeAvailable();
        mainHandler.post(refreshInputViewTask);
    }

    @Override
    public void onDestroy() {
        shuttingDown = true;

        KeyboardMonitor.release(this);
        mainHandler.removeCallbacksAndMessages(null);
        fallbackBinding = null;

        super.onDestroy();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        KeyboardMonitor.scheduleCheck();
    }

    @Override
    public void onStartInput(EditorInfo attribute, boolean restarting) {
        super.onStartInput(attribute, restarting);

        reloadConfiguration();
        KeyboardMonitor.scheduleCheck();
    }

    @Override
    public View onCreateInputView() {
        fallbackBinding = ViewImeFallbackBinding.inflate(getLayoutInflater());
        fallbackBinding.switchKeyboardButton.setOnClickListener(
                view -> requestSoftKeyboardSwitch());
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

    /**
     * Keeps the persistent device monitor alive even while another IME is active.
     * This is the only entry point used outside the service lifecycle.
     */
    public static void startKeyboardMonitor(Context context) {
        KeyboardMonitor.ensureStarted(context);
    }

    /**
     * Switches away from AltShift through the active IME session. The platform
     * authorises this call for the current input method, so it needs neither
     * WRITE_SECURE_SETTINGS nor a Settings.Secure write.
     */
    public boolean switchToSoftKeyboardFromIme() {
        if (shuttingDown) {
            return false;
        }

        if (switchToPreviousInputMethodSafely()) {
            Log.i(TAG, "Switched to the previous input method through the IME session");

            return true;
        }

        String target = InputMethodSwitcher.resolveSoftKeyboardId(this);
        if (target == null) {
            Log.w(TAG, "No soft keyboard is available for a native switch");

            return false;
        }

        return switchInputMethodSafely(target);
    }

    private boolean switchToPreviousInputMethodSafely() {
        try {
            return switchToPreviousInputMethod();
        } catch (RuntimeException exception) {
            Log.w(TAG, "Unable to switch to the previous input method", exception);

            return false;
        }
    }

    private boolean switchInputMethodSafely(String imeId) {
        try {
            switchInputMethod(imeId);
            Log.i(TAG, "Switched to the soft keyboard through the IME session: " + imeId);

            return true;
        } catch (RuntimeException exception) {
            Log.w(TAG, "Unable to switch the input method through the IME session", exception);

            return false;
        }
    }

    private void requestSoftKeyboardSwitch() {
        if (switchToSoftKeyboardFromIme()) {
            return;
        }

        KeyboardMonitor.requestSoftKeyboardSwitch(true);
    }

    private void onKeyboardStateReconciled(boolean present, boolean alternative) {
        if (shuttingDown) {
            return;
        }

        keyboardPresent = present;
        alternativeImeAvailable = alternative;
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

    private void openKeyboardSettings() {
        Intent intent = new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        try {
            startActivity(intent);
        } catch (RuntimeException exception) {
            Log.w(TAG, "Unable to open keyboard settings", exception);
        }
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

    /**
     * Single process-wide owner of input-device monitoring and IME switching.
     * It outlives the IME binding so a re-attached keyboard can reactivate AltShift.
     */
    private static final class KeyboardMonitor {

        private static final long DEBOUNCE_MS = 150L;
        private static final Object LOCK = new Object();

        private static volatile KeyboardMonitor instance;

        private final Context appContext;
        private final Handler mainHandler = new Handler(Looper.getMainLooper());
        private final HandlerThread inputThread;
        private final Handler inputHandler;
        private final Runnable checkTask = this::runReconcile;
        private final Runnable activateAltShiftTask = this::activateAltShift;

        private final InputManager.InputDeviceListener deviceListener =
                new InputManager.InputDeviceListener() {
                    @Override
                    public void onInputDeviceAdded(int deviceId) {
                        Log.d(TAG, "Input device added: " + deviceId);
                        scheduleCheck();
                    }

                    @Override
                    public void onInputDeviceRemoved(int deviceId) {
                        Log.d(TAG, "Input device removed: " + deviceId);
                        scheduleCheck();
                    }

                    @Override
                    public void onInputDeviceChanged(int deviceId) {
                        Log.d(TAG, "Input device changed: " + deviceId);
                        scheduleCheck();
                    }
                };

        private volatile WeakReference<HardwareInputService> serviceRef =
                new WeakReference<>(null);
        private volatile boolean keyboardPresent;
        private volatile boolean alternativeImeAvailable = true;
        private boolean initialized;

        private KeyboardMonitor(Context appContext) {
            this.appContext = appContext;
            keyboardPresent = KeyboardPresenceMonitor.isPhysicalKeyboardReported(appContext);

            inputThread = new HandlerThread(INPUT_THREAD_NAME);
            inputThread.start();
            inputHandler = new Handler(inputThread.getLooper());

            registerDeviceListener();
            inputHandler.post(checkTask);
        }

        static State acquire(HardwareInputService service) {
            Context appContext = service.getApplicationContext();

            synchronized (LOCK) {
                if (instance == null) {
                    instance = new KeyboardMonitor(appContext);
                }

                KeyboardMonitor monitor = instance;
                monitor.serviceRef = new WeakReference<>(service);
                monitor.scheduleCheckInternal();

                return new State(
                        monitor.keyboardPresent,
                        monitor.alternativeImeAvailable);
            }
        }

        static void release(HardwareInputService service) {
            synchronized (LOCK) {
                KeyboardMonitor monitor = instance;
                if (monitor == null) {
                    return;
                }

                monitor.clearService(service);
            }
        }

        static void ensureStarted(Context context) {
            if (context == null) {
                return;
            }

            Context appContext = context.getApplicationContext();

            synchronized (LOCK) {
                if (instance == null) {
                    instance = new KeyboardMonitor(appContext);
                    return;
                }

                instance.scheduleCheckInternal();
            }
        }

        static void scheduleCheck() {
            KeyboardMonitor monitor = instance;
            if (monitor == null) {
                return;
            }

            monitor.scheduleCheckInternal();
        }

        static void requestSoftKeyboardSwitch(boolean showPickerOnFailure) {
            KeyboardMonitor monitor = instance;
            if (monitor == null) {
                return;
            }

            monitor.inputHandler.removeCallbacks(monitor.checkTask);
            monitor.inputHandler.post(
                    () -> monitor.switchToSoftKeyboard(showPickerOnFailure));
        }

        static void requestAltShiftActivation() {
            KeyboardMonitor monitor = instance;
            if (monitor == null) {
                return;
            }

            monitor.inputHandler.removeCallbacks(monitor.checkTask);
            monitor.inputHandler.post(monitor.activateAltShiftTask);
        }

        private void clearService(HardwareInputService service) {
            if (serviceRef.get() == service) {
                serviceRef.clear();
            }
        }

        private void scheduleCheckInternal() {
            inputHandler.removeCallbacks(checkTask);
            inputHandler.postDelayed(checkTask, DEBOUNCE_MS);
        }

        private void registerDeviceListener() {
            try {
                InputManager manager = appContext.getSystemService(InputManager.class);
                if (manager == null) {
                    Log.w(TAG, "InputManager is unavailable");

                    return;
                }

                manager.registerInputDeviceListener(deviceListener, inputHandler);
            } catch (RuntimeException exception) {
                Log.e(TAG, "Unable to register the input device listener", exception);
            }
        }

        private void runReconcile() {
            try {
                reconcile();
            } catch (RuntimeException exception) {
                Log.e(TAG, "Unable to reconcile the keyboard state", exception);
            }
        }

        private void reconcile() {
            boolean present = KeyboardPresenceMonitor.isPhysicalKeyboardAvailable(appContext);
            boolean alternative = InputMethodSwitcher.hasAlternativeInputMethod(appContext);
            boolean changed = !initialized || present != keyboardPresent;

            keyboardPresent = present;
            alternativeImeAvailable = alternative;
            initialized = true;

            if (changed) {
                Log.i(TAG, "Physical keyboard state changed: present=" + present);
                applyKeyboardState(present, alternative);
            }

            HardwareInputService service = serviceRef.get();
            if (service != null) {
                service.onKeyboardStateReconciled(present, alternative);
            }
        }

        private void applyKeyboardState(boolean present, boolean alternative) {
            if (present) {
                applyKeyboardAttached();

                return;
            }

            applyKeyboardDetached(alternative);
        }

        private void applyKeyboardAttached() {
            if (isAutoSwitchPreferenceEnabled()
                    && InputMethodSwitcher.switchToAltShift(appContext)) {
                mainHandler.post(KeyboardPromptOverlay::dismiss);
                KeyboardNotification.cancel(appContext);

                return;
            }

            if (InputMethodSwitcher.isAltShiftActive(appContext)) {
                mainHandler.post(KeyboardPromptOverlay::dismiss);

                return;
            }

            mainHandler.post(this::showReconnectPrompt);
        }

        private void applyKeyboardDetached(boolean alternative) {
            mainHandler.post(KeyboardPromptOverlay::dismiss);

            if (!alternative || !isAutoSwitchPreferenceEnabled()) {
                return;
            }

            HardwareInputService service = serviceRef.get();
            if (service != null && service.switchToSoftKeyboardFromIme()) {
                return;
            }

            if (InputMethodSwitcher.isAltShiftActive(appContext)
                    && InputMethodSwitcher.hasSecureSettingsPermission(appContext)) {
                InputMethodSwitcher.switchToSoftKeyboard(appContext);
            }
        }

        private boolean isAutoSwitchPreferenceEnabled() {
            try {
                return HotkeyRepository.getInstance(appContext).isAutoSwitchEnabled();
            } catch (RuntimeException exception) {
                Log.e(TAG, "Unable to read the auto-switch setting", exception);

                return false;
            }
        }

        private void activateAltShift() {
            if (InputMethodSwitcher.switchToAltShift(appContext)) {
                mainHandler.post(KeyboardPromptOverlay::dismiss);
                KeyboardNotification.cancel(appContext);

                return;
            }

            Log.w(TAG, "Direct IME activation failed; opening the system picker");
            mainHandler.post(() -> {
                KeyboardPromptOverlay.dismiss();
                SwitchBackActivity.start(appContext);
            });
        }

        private void switchToSoftKeyboard(boolean showPickerOnFailure) {
            if (!alternativeImeAvailable) {
                mainHandler.post(this::openKeyboardSettings);

                return;
            }

            if (InputMethodSwitcher.switchToSoftKeyboard(appContext)) {
                return;
            }

            if (showPickerOnFailure) {
                mainHandler.post(this::showInputMethodPicker);
            }
        }

        private void showReconnectPrompt() {
            KeyboardNotification.cancel(appContext);

            boolean secure = InputMethodSwitcher.hasSecureSettingsPermission(appContext);
            Runnable onSwitch = secure
                    ? KeyboardMonitor::requestAltShiftActivation
                    : () -> SwitchBackActivity.start(appContext);

            if (KeyboardPromptOverlay.show(appContext, onSwitch)) {
                return;
            }

            KeyboardNotification.showReconnected(appContext);
        }

        private void showInputMethodPicker() {
            InputMethodManager manager = appContext.getSystemService(InputMethodManager.class);
            if (manager != null) {
                try {
                    manager.showInputMethodPicker();

                    return;
                } catch (RuntimeException exception) {
                    Log.w(TAG, "Unable to show the input method picker", exception);
                }
            }

            SwitchBackActivity.start(appContext);
        }

        private void openKeyboardSettings() {
            Intent intent = new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            try {
                appContext.startActivity(intent);
            } catch (RuntimeException exception) {
                Log.w(TAG, "Unable to open keyboard settings", exception);
            }
        }
    }

    private record State(boolean keyboardPresent, boolean alternativeImeAvailable) {
    }
}
