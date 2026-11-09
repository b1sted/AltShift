package ru.b1sted.altshift.ui;

import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import java.util.List;
import java.util.Objects;

import ru.b1sted.altshift.R;
import ru.b1sted.altshift.databinding.ActivityMainBinding;
import ru.b1sted.altshift.model.HotkeyBinding;
import ru.b1sted.altshift.model.LanguageSwitchShortcut;
import ru.b1sted.altshift.service.HardwareInputService;
import ru.b1sted.altshift.service.KeyboardPresenceMonitor;
import ru.b1sted.altshift.storage.HotkeyRepository;

public final class MainActivity extends AppCompatActivity {

    public static final String EXTRA_OPEN_IME_PICKER =
            "ru.b1sted.altshift.extra.OPEN_IME_PICKER";

    private static final String TAG = "AltShift";

    private ActivityMainBinding binding;
    private HotkeyRepository repository;
    private HotkeyAdapter adapter;
    private boolean openPickerOnResume;

    @Override
    protected void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setSupportActionBar(binding.toolbar);

        repository = Objects.requireNonNull(
                HotkeyRepository.getInstance(this), "repository");
        adapter = new HotkeyAdapter(this, this::deleteBinding);
        binding.hotkeyList.setLayoutManager(new LinearLayoutManager(this));
        binding.hotkeyList.setAdapter(adapter);

        binding.step1Button.setOnClickListener(view -> openImeSettings());
        binding.step2Button.setOnClickListener(view -> showImePicker());
        binding.step3Button.setOnClickListener(view -> openOverlaySettings());
        binding.keyboardFallbackButton.setOnClickListener(view -> showImePicker());
        binding.languageShortcutButton.setOnClickListener(
                view -> captureLanguageShortcut());
        binding.addHotkeyFab.setOnClickListener(view -> addHotkey());

        openPickerOnResume = getIntent().getBooleanExtra(EXTRA_OPEN_IME_PICKER, false);
        refreshAll();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshAll();
        if (openPickerOnResume) {
            openPickerOnResume = false;
            showImePicker();
        }
    }

    @Override
    protected void onNewIntent(final Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        openPickerOnResume = intent.getBooleanExtra(EXTRA_OPEN_IME_PICKER, false);
        if (openPickerOnResume) {
            openPickerOnResume = false;
            showImePicker();
        }
    }

    private void refreshAll() {
        refreshSetup();
        refreshLanguageShortcut();
        refreshHotkeys();
    }

    private void refreshSetup() {
        final ComponentName component =
                new ComponentName(this, HardwareInputService.class);
        final boolean isEnabled = isImeEnabled(component);
        final String active = readSecureSetting(Settings.Secure.DEFAULT_INPUT_METHOD);
        final boolean isActiveKnown = active != null;
        final boolean isActive = isActiveKnown && matchesComponent(active, component);
        final boolean hasOverlay = Settings.canDrawOverlays(this);

        bindStep(binding.step1Check, binding.step1Status, binding.step1Button,
                isEnabled, R.string.setup_step_enable_ok,
                R.string.setup_step_enable_pending);
        bindStep(binding.step2Check, binding.step2Status, binding.step2Button,
                isActive, R.string.setup_step_select_ok,
                isEnabled ? R.string.setup_step_select_pending
                        : R.string.setup_step_select_blocked);
        bindOverlayStep(hasOverlay);

        final boolean hasKeyboard = KeyboardPresenceMonitor
                .isPhysicalKeyboardAvailable(this);
        binding.keyboardStateText.setText(hasKeyboard
                ? R.string.keyboard_connected : R.string.keyboard_disconnected);
        binding.keyboardStateText.setTextColor(getColor(hasKeyboard
                ? R.color.brand_success : R.color.brand_error));
        binding.keyboardFallbackButton.setVisibility(
                hasKeyboard ? View.GONE : View.VISIBLE);
    }

    private void bindStep(
            final TextView check,
            final TextView status,
            final View action,
            final boolean done,
            final int doneResId,
            final int pendingResId) {
        status.setText(done ? doneResId : pendingResId);
        status.setTextColor(getColor(done ? R.color.brand_success : R.color.brand_error));
        check.setVisibility(done ? View.VISIBLE : View.GONE);
        action.setVisibility(done ? View.GONE : View.VISIBLE);
    }

    private void bindOverlayStep(final boolean granted) {
        binding.step3Status.setText(R.string.setup_step_overlay_explanation);
        binding.step3Check.setVisibility(granted ? View.VISIBLE : View.GONE);
        binding.step3Button.setVisibility(granted ? View.GONE : View.VISIBLE);
    }

    private boolean isImeEnabled(final ComponentName component) {
        try {
            final InputMethodManager manager = getSystemService(InputMethodManager.class);
            if (manager == null) {
                return false;
            }
            final List<InputMethodInfo> methods = manager.getEnabledInputMethodList();
            for (int index = 0; index < methods.size(); index++) {
                if (component.equals(methods.get(index).getComponent())) {
                    return true;
                }
            }
        } catch (final RuntimeException exception) {
            Log.e(TAG, "Unable to query enabled input methods", exception);
        }
        return false;
    }

    @Nullable
    private String readSecureSetting(final String key) {
        try {
            return Settings.Secure.getString(getContentResolver(), key);
        } catch (final SecurityException exception) {
            Log.w(TAG, "Secure setting is restricted: " + key, exception);
            return null;
        } catch (final RuntimeException exception) {
            Log.e(TAG, "Unable to read secure setting: " + key, exception);
            return null;
        }
    }

    private static boolean matchesComponent(
            final String flat, final ComponentName component) {
        if (TextUtils.isEmpty(flat) || component == null) {
            return false;
        }
        return flat.contains(component.flattenToString())
                || flat.contains(component.flattenToShortString());
    }

    private void refreshLanguageShortcut() {
        final LanguageSwitchShortcut shortcut = repository.getLanguageShortcut();
        binding.languageShortcutText.setText(KeyLabelFormatter.format(shortcut));
    }

    private void refreshHotkeys() {
        final HotkeyBinding[] bindings = repository.getBindings();
        adapter.submit(bindings);
        binding.emptyView.setVisibility(
                bindings.length == 0 ? View.VISIBLE : View.GONE);
    }

    private void openImeSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS));
        } catch (final RuntimeException exception) {
            Log.e(TAG, "Unable to open input method settings", exception);
            toast(R.string.settings_open_failed);
        }
    }

    private void openOverlaySettings() {
        try {
            startActivity(new Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())));
        } catch (final RuntimeException first) {
            Log.e(TAG, "Unable to open overlay settings", first);
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION));
            } catch (final RuntimeException second) {
                Log.e(TAG, "Unable to open generic overlay settings", second);
                toast(R.string.overlay_settings_failed);
            }
        }
    }

    private void showImePicker() {
        try {
            final InputMethodManager manager = getSystemService(InputMethodManager.class);
            if (manager == null) {
                toast(R.string.ime_picker_failed);
                return;
            }
            manager.showInputMethodPicker();
        } catch (final RuntimeException exception) {
            Log.e(TAG, "Unable to open the input method picker", exception);
            toast(R.string.ime_picker_failed);
        }
    }

    private void captureLanguageShortcut() {
        final LanguageSwitchShortcut current = repository.getLanguageShortcut();
        KeyCaptureDialog.show(this, current, (keyCode, ctrl, alt, shift, meta) -> {
            try {
                repository.saveLanguageShortcut(new LanguageSwitchShortcut(
                        keyCode, ctrl, alt, shift, meta));
                refreshLanguageShortcut();
                toast(R.string.shortcut_saved);
            } catch (final RuntimeException exception) {
                Log.e(TAG, "Unable to save language shortcut", exception);
                toast(R.string.shortcut_save_failed);
            }
        });
    }

    private void addHotkey() {
        KeyCaptureDialog.show(this, null, (keyCode, ctrl, alt, shift, meta) ->
                AppPickerDialog.show(this, (appName, packageName) -> {
                    try {
                        repository.saveBinding(new HotkeyBinding(
                                keyCode,
                                ctrl,
                                alt,
                                shift,
                                meta,
                                packageName,
                                appName));
                        refreshHotkeys();
                        toast(R.string.shortcut_saved);
                    } catch (final RuntimeException exception) {
                        Log.e(TAG, "Unable to save hotkey binding", exception);
                        toast(R.string.shortcut_save_failed);
                    }
                }));
    }

    private void deleteBinding(final HotkeyBinding binding) {
        try {
            repository.deleteBinding(binding);
            refreshHotkeys();
            toast(R.string.shortcut_deleted);
        } catch (final RuntimeException exception) {
            Log.e(TAG, "Unable to delete hotkey binding", exception);
            toast(R.string.shortcut_save_failed);
        }
    }

    private void toast(final int messageResId) {
        Toast.makeText(this, messageResId, Toast.LENGTH_SHORT).show();
    }
}
