package ru.b1sted.altshift.service;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.database.ContentObserver;
import android.hardware.input.InputManager;
import android.inputmethodservice.InputMethodService;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.util.Objects;

import ru.b1sted.altshift.R;
import ru.b1sted.altshift.databinding.ViewImeFallbackBinding;
import ru.b1sted.altshift.model.HotkeyBinding;
import ru.b1sted.altshift.model.LanguageSwitchShortcut;
import ru.b1sted.altshift.storage.HotkeyRepository;
import ru.b1sted.altshift.ui.SwitchBackActivity;
import ru.b1sted.altshift.ui.ThemeController;

public final class HardwareInputService extends InputMethodService {

    private static final String TAG = "AltShift";
    private static final String INPUT_THREAD_NAME = "AltShift-Input";

    private static volatile boolean autoSwitchSuppressed;

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
        // The IME runs under Theme.DeviceDefault.InputMethod, which lacks the
        // Material 3 attributes used by view_ime_fallback.xml. Inflate through
        // the shared locale + night-mode context instead.
        Context themed = ThemeController.themedContext(this);
        Context inflationContext = themed != null ? themed : this;

        try {
            fallbackBinding = ViewImeFallbackBinding.inflate(
                    LayoutInflater.from(inflationContext));
        } catch (RuntimeException exception) {
            Log.e(TAG, "Unable to inflate the IME fallback view", exception);
            fallbackBinding = null;

            return createEmergencyFallbackView(inflationContext);
        }

        fallbackBinding.switchKeyboardButton.setOnClickListener(
                view -> requestSoftKeyboardSwitch());
        fallbackBinding.openKeyboardSettingsButton.setOnClickListener(
                view -> openKeyboardSettings());

        applyFallbackState();

        return fallbackBinding.getRoot();
    }

    /**
     * Keeps the system input framework alive if a future platform change makes
     * the Material fallback layout impossible to inflate.
     */
    private View createEmergencyFallbackView(Context context) {
        TextView message = new TextView(context);
        message.setText(context.getString(R.string.ime_fallback_message));

        int padding = Math.round(
                16 * context.getResources().getDisplayMetrics().density);
        message.setPadding(padding, padding, padding, padding);

        return message;
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

        if (event.getRepeatCount() == 0 && handleShortcut(keyCode, event)) {
            return true;
        }

        if (!isRussian || event.isCtrlPressed() || event.isAltPressed()
                || event.isMetaPressed()) {
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

    private boolean handleShortcut(int keyCode, KeyEvent event) {
        if (isLanguageToggle(keyCode, event)) {
            isRussian = !isRussian;

            return true;
        }

        return launchHotkeyApp(keyCode, event);
    }

    /**
     * Keeps the persistent device monitor alive even while another IME is active.
     * This is the only entry point used outside the service lifecycle.
     */
    public static void startKeyboardMonitor(Context context) {
        KeyboardMonitor.ensureStarted(context);
    }

    /**
     * Prevents the monitor from undoing an explicit IME selection made in the
     * setup wizard while the wizard is in the foreground.
     */
    public static void setAutoSwitchSuppressed(boolean suppressed) {
        autoSwitchSuppressed = suppressed;
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
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            return false;
        }

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

        Context viewContext = local.getRoot().getContext();
        local.fallbackMessage.setText(viewContext.getString(stranded
                ? R.string.ime_fallback_no_keyboard_message
                : R.string.ime_fallback_message));
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

        // Shift alone is normal typing (uppercase), never an app trigger.
        if (!ctrl && !alt && !meta) {
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

        try {
            InputConnection connection = getCurrentInputConnection();
            if (connection == null) {
                Log.w(TAG, "InputConnection is null during commitMapped");

                return false;
            }

            return connection.commitText(text, 1);
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
        private final ContentObserver imeObserver;

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
        private boolean warningPosted;
        private boolean initialized;

        private KeyboardMonitor(Context appContext) {
            this.appContext = appContext;
            keyboardPresent = KeyboardPresenceMonitor
                    .isKeyboardReportedByConfiguration(appContext);

            inputThread = new HandlerThread(INPUT_THREAD_NAME);
            inputThread.start();
            inputHandler = new Handler(inputThread.getLooper());
            imeObserver = new ContentObserver(inputHandler) {
                @Override
                public void onChange(boolean selfChange) {
                    onInputMethodSettingsChanged();
                }
            };

            registerDeviceListener();
            registerImeObserver();
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
            if (appContext == null) {
                return;
            }

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

        private void clearService(HardwareInputService service) {
            if (serviceRef.get() == service) {
                serviceRef.clear();
            }

            // The IME binding may disappear before the settings observer fires,
            // so the watchdog is reconciled as soon as the service is released.
            inputHandler.post(() -> KeyboardWatchdogService.reconcile(appContext));
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

        private void registerImeObserver() {
            try {
                appContext.getContentResolver().registerContentObserver(
                        Settings.Secure.getUriFor(Settings.Secure.DEFAULT_INPUT_METHOD),
                        false,
                        imeObserver);
                appContext.getContentResolver().registerContentObserver(
                        Settings.Secure.getUriFor(Settings.Secure.ENABLED_INPUT_METHODS),
                        false,
                        imeObserver);
            } catch (RuntimeException exception) {
                Log.w(TAG, "Unable to observe input method settings", exception);
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
            boolean changed = !initialized
                    || present != keyboardPresent
                    || (!present && alternative != alternativeImeAvailable);

            keyboardPresent = present;
            alternativeImeAvailable = alternative;
            initialized = true;

            // Reconcile before applying the transition so a pending detach
            // hand-off is not cancelled by the active-IME check of this cycle.
            KeyboardWatchdogService.reconcile(appContext, present);

            if (changed) {
                Log.i(TAG, "Keyboard state changed: present=" + present
                        + ", alternative=" + alternative);
                applyKeyboardState(present, alternative);
            }

            HardwareInputService service = serviceRef.get();
            if (service != null) {
                service.onKeyboardStateReconciled(present, alternative);
            }

            updateWarningNotification(!alternative
                    && InputMethodSwitcher.isAltShiftEnabled(appContext));

            if (!present || InputMethodSwitcher.isAltShiftActive(appContext)) {
                KeyboardNotification.cancel(appContext);
            }
        }

        private void updateWarningNotification(boolean required) {
            if (required) {
                if (!warningPosted) {
                    warningPosted = KeyboardWarningNotification.show(appContext);
                    if (warningPosted) {
                        Log.i(TAG, "Single-keyboard warning posted");
                    }
                }

                return;
            }

            if (warningPosted) {
                KeyboardWarningNotification.cancel(appContext);
                Log.i(TAG, "Single-keyboard warning cleared");
            }

            warningPosted = false;
        }

        private void applyKeyboardState(boolean present, boolean alternative) {
            if (present) {
                applyKeyboardAttached();

                return;
            }

            applyKeyboardDetached(alternative);
        }

        private void applyKeyboardAttached() {
            // A stylus handwriting session holds the input connection, so it must
            // be released before AltShift can take over the physical keyboard.
            HardwareInputService service = serviceRef.get();
            if (service != null) {
                mainHandler.post(
                        () -> InputMethodSwitcher.resetStylusHandwritingSession(service));
            }

            if (isAutoSwitchPreferenceEnabled()
                    && InputMethodSwitcher.switchToAltShift(appContext)) {
                KeyboardWatchdogService.stop(appContext);
                KeyboardNotification.cancel(appContext);
                KeyboardPromptOverlay.dismissActive();

                return;
            }

            if (InputMethodSwitcher.isAltShiftActive(appContext)) {
                KeyboardWatchdogService.stop(appContext);
                KeyboardNotification.cancel(appContext);
                KeyboardPromptOverlay.dismissActive();

                return;
            }

            // The user stays on another IME. Keep the watchdog alive so this
            // process still observes the next disconnect and can show the
            // Dynamic Island again after a reconnect.
            KeyboardWatchdogService.reconcile(appContext, true);

            KeyboardNotification.cancel(appContext);
            mainHandler.post(this::showReconnectPrompt);
        }

        private void applyKeyboardDetached(boolean alternative) {
            KeyboardPromptOverlay.dismissActive();
            KeyboardNotification.cancel(appContext);

            if (autoSwitchSuppressed) {
                ensureWatchdog();

                return;
            }

            if (!alternative || !isAutoSwitchPreferenceEnabled()) {
                ensureWatchdog();

                return;
            }

            // Start while the IME binding is still alive: the platform accepts
            // the foreground-service transition in this state even without an
            // overlay or battery-optimization exemption.
            KeyboardWatchdogService.startWhileDetached(appContext);

            HardwareInputService service = serviceRef.get();
            if (service == null) {
                applySettingsSwitchAndWatchdog();

                return;
            }

            mainHandler.post(() -> {
                if (service.switchToSoftKeyboardFromIme()) {
                    inputHandler.post(
                            () -> KeyboardWatchdogService.startWhileDetached(appContext));

                    return;
                }

                inputHandler.post(this::applySettingsSwitchAndWatchdog);
            });
        }

        private void ensureWatchdog() {
            KeyboardWatchdogService.reconcile(appContext, false);
        }

        /**
         * Falls back to the secure-settings switch and keeps the watchdog alive
         * when the hand-off succeeded. The active-IME read can lag behind the
         * write, so it is not used to stop a watchdog started for a real switch.
         */
        private void applySettingsSwitchAndWatchdog() {
            if (switchToSoftKeyboardThroughSettings()) {
                KeyboardWatchdogService.startWhileDetached(appContext);

                return;
            }

            ensureWatchdog();
        }

        private boolean switchToSoftKeyboardThroughSettings() {
            if (!InputMethodSwitcher.isAltShiftActive(appContext)
                    || !InputMethodSwitcher.hasSecureSettingsPermission(appContext)) {
                return false;
            }

            return InputMethodSwitcher.switchToSoftKeyboard(appContext);
        }

        private boolean isAutoSwitchPreferenceEnabled() {
            try {
                return HotkeyRepository.getInstance(appContext).isAutoSwitchEnabled();
            } catch (RuntimeException exception) {
                Log.e(TAG, "Unable to read the auto-switch setting", exception);

                return false;
            }
        }

        /**
         * Dismissing the prompt is the only action that starts the picker. The
         * notification is graded by {@link SwitchBackActivity} after the user
         * closes the dialog; closing the prompt never posts anything.
         */
        private void requestPickerSwitch() {
            KeyboardPromptOverlay.dismissActive();
            SwitchBackActivity.start(appContext);
        }

        private void onInputMethodSettingsChanged() {
            Log.d(TAG, "Input method settings changed");
            scheduleCheckInternal();

            if (InputMethodSwitcher.isAltShiftActive(appContext)) {
                KeyboardNotification.cancel(appContext);
            }

            KeyboardWatchdogService.reconcile(appContext);
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

            if (KeyboardPromptOverlay.show(
                    appContext, this::requestPickerSwitch, this::onPromptDismissed)) {
                return;
            }

            Log.i(TAG, "Overlay unavailable; waiting for an explicit switch attempt");
        }

        /**
         * A closed or timed-out prompt leaves the user on the soft keyboard, so
         * it posts a standard (non-heads-up) reminder. The explicit switch
         * action is graded by {@link SwitchBackActivity} instead.
         */
        private void onPromptDismissed() {
            if (!keyboardPresent || InputMethodSwitcher.isAltShiftActive(appContext)) {
                return;
            }

            KeyboardNotification.showInactive(appContext);
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
