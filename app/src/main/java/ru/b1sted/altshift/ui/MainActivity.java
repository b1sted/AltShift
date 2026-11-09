package ru.b1sted.altshift.ui;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Objects;

import ru.b1sted.altshift.R;
import ru.b1sted.altshift.databinding.ActivityMainBinding;
import ru.b1sted.altshift.model.HotkeyBinding;
import ru.b1sted.altshift.model.LanguageSwitchShortcut;
import ru.b1sted.altshift.model.ThemeMode;
import ru.b1sted.altshift.service.InputMethodSwitcher;
import ru.b1sted.altshift.service.KeyboardPresenceMonitor;
import ru.b1sted.altshift.service.KeyboardSwitchService;
import ru.b1sted.altshift.storage.HotkeyRepository;

public final class MainActivity extends AppCompatActivity {

    public static final String EXTRA_OPEN_IME_PICKER =
            "ru.b1sted.altshift.extra.OPEN_IME_PICKER";

    private static final String TAG = "AltShift";
    private static final int REQUEST_NOTIFICATIONS = 3001;

    private ActivityMainBinding binding;
    private HotkeyRepository repository;
    private HotkeyAdapter adapter;
    private boolean openPickerOnResume;
    private boolean autoSwitchHintExpanded;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        repository = Objects.requireNonNull(
                HotkeyRepository.getInstance(this), "repository");
        if (!repository.isOnboardingCompleted()) {
            openOnboarding();

            return;
        }

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setSupportActionBar(binding.toolbar);

        adapter = new HotkeyAdapter(this, this::deleteBinding);
        binding.hotkeyList.setLayoutManager(new LinearLayoutManager(this));
        binding.hotkeyList.setAdapter(adapter);

        binding.languageShortcutButton.setOnClickListener(
                view -> captureLanguageShortcut());
        binding.addHotkeyFab.setOnClickListener(view -> addHotkey());
        binding.autoSwitchToggle.setOnCheckedChangeListener(
                (button, checked) -> onAutoSwitchToggled(checked));
        binding.autoSwitchHintToggle.setOnClickListener(
                view -> toggleAutoSwitchHint());
        binding.copyCommandButton.setOnClickListener(
                view -> copyAutoSwitchCommand());
        binding.themeButton.setOnClickListener(view -> showThemeDialog());

        openPickerOnResume = getIntent().getBooleanExtra(
                EXTRA_OPEN_IME_PICKER, false);
        refreshAll();
        requestNotificationPermissionIfNeeded();
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode != REQUEST_NOTIFICATIONS) {
            return;
        }

        refreshAll();
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (binding == null) {
            return;
        }

        refreshAll();

        if (openPickerOnResume) {
            openPickerOnResume = false;
            showImePicker();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);

        openPickerOnResume = intent.getBooleanExtra(EXTRA_OPEN_IME_PICKER, false);
        if (openPickerOnResume) {
            openPickerOnResume = false;
            showImePicker();
        }
    }

    @Override
    protected void onDestroy() {
        if (adapter != null) {
            adapter.shutdown();
        }

        super.onDestroy();
    }

    private void openOnboarding() {
        Intent intent = new Intent(this, OnboardingActivity.class);
        startActivity(intent);
        finish();
    }

    private void refreshAll() {
        refreshStatus();
        refreshAutoSwitch();
        refreshLanguageShortcut();
        refreshHotkeys();
    }

    private void refreshStatus() {
        boolean keyboardPresent = KeyboardPresenceMonitor
                .isPhysicalKeyboardAvailable(this);
        boolean imeActive = InputMethodSwitcher.isAltShiftActive(this);

        String keyboardStatus = getString(keyboardPresent
                ? R.string.status_keyboard_connected
                : R.string.status_keyboard_disconnected);
        String imeStatus = getString(imeActive
                ? R.string.status_ime_active
                : R.string.status_ime_inactive);
        binding.statusText.setText(getString(
                R.string.status_badge_format, keyboardStatus, imeStatus));

        int dotColor = getColor(keyboardPresent && imeActive
                ? R.color.brand_success
                : R.color.brand_error);
        binding.statusDot.setBackgroundTintList(ColorStateList.valueOf(dotColor));
    }

    private void refreshAutoSwitch() {
        boolean enabled = repository.isAutoSwitchEnabled();
        boolean permission = InputMethodSwitcher.hasSecureSettingsPermission(this);

        binding.autoSwitchToggle.setOnCheckedChangeListener(null);
        binding.autoSwitchToggle.setChecked(enabled);
        binding.autoSwitchToggle.setOnCheckedChangeListener(
                (button, checked) -> onAutoSwitchToggled(checked));

        int statusResId;
        int statusColorResId;

        if (!enabled) {
            statusResId = R.string.auto_switch_status_off;
            statusColorResId = R.color.brand_on_surface_variant;
        } else if (permission) {
            statusResId = R.string.auto_switch_status_active;
            statusColorResId = R.color.brand_success;
        } else {
            statusResId = R.string.auto_switch_status_permission;
            statusColorResId = R.color.brand_error;
        }

        binding.autoSwitchStatus.setText(statusResId);
        binding.autoSwitchStatus.setTextColor(getColor(statusColorResId));

        if (permission) {
            autoSwitchHintExpanded = false;
        }

        binding.autoSwitchHintToggle.setVisibility(permission ? View.GONE : View.VISIBLE);
        binding.autoSwitchHintToggle.setText(autoSwitchHintExpanded
                ? R.string.auto_switch_hint_hide
                : R.string.auto_switch_hint_show);
        binding.autoSwitchHint.setVisibility(
                !permission && autoSwitchHintExpanded ? View.VISIBLE : View.GONE);

        updateAutoSwitchService(enabled, permission);
    }

    private void onAutoSwitchToggled(boolean enabled) {
        repository.saveAutoSwitchEnabled(enabled);

        if (enabled && !InputMethodSwitcher.hasSecureSettingsPermission(this)) {
            autoSwitchHintExpanded = true;
            toast(R.string.auto_switch_permission_required);
        }

        refreshAutoSwitch();
    }

    private void toggleAutoSwitchHint() {
        autoSwitchHintExpanded = !autoSwitchHintExpanded;
        refreshAutoSwitch();
    }

    private void copyAutoSwitchCommand() {
        ClipboardManager clipboard =
                (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (clipboard == null) {
            return;
        }

        ClipData clip = ClipData.newPlainText(
                getString(R.string.auto_switch_command_label),
                getString(R.string.auto_switch_command));
        clipboard.setPrimaryClip(clip);
        toast(R.string.command_copied);
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return;
        }

        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) {
            return;
        }

        ActivityCompat.requestPermissions(
                this,
                new String[]{Manifest.permission.POST_NOTIFICATIONS},
                REQUEST_NOTIFICATIONS);
    }

    private void updateAutoSwitchService(boolean enabled, boolean permission) {
        if (enabled && permission) {
            KeyboardSwitchService.start(this);

            return;
        }

        KeyboardSwitchService.stop(this);
    }

    private void showThemeDialog() {
        ThemeMode[] modes = ThemeMode.values();
        String[] labels = new String[modes.length];

        for (int index = 0; index < modes.length; index++) {
            labels[index] = getString(themeLabelRes(modes[index]));
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.theme_title)
                .setSingleChoiceItems(
                        labels,
                        repository.getThemeMode().ordinal(),
                        (dialog, which) -> {
                            ThemeController.saveAndApply(this, modes[which]);
                            dialog.dismiss();
                        })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private int themeLabelRes(ThemeMode mode) {
        return switch (mode) {
            case LIGHT -> R.string.theme_light;
            case DARK -> R.string.theme_dark;
            case FOLLOW_SYSTEM -> R.string.theme_system;
            default -> R.string.theme_system;
        };
    }

    private void refreshLanguageShortcut() {
        LanguageSwitchShortcut shortcut = repository.getLanguageShortcut();
        binding.languageShortcutText.setText(KeyLabelFormatter.format(shortcut));
    }

    private void refreshHotkeys() {
        HotkeyBinding[] bindings = repository.getBindings();
        adapter.submit(bindings);
        binding.emptyView.setVisibility(
                bindings.length == 0 ? View.VISIBLE : View.GONE);
    }

    private void showImePicker() {
        try {
            InputMethodManager manager = getSystemService(InputMethodManager.class);
            if (manager == null) {
                toast(R.string.ime_picker_failed);

                return;
            }

            manager.showInputMethodPicker();
        } catch (RuntimeException exception) {
            Log.e(TAG, "Unable to open the input method picker", exception);
            toast(R.string.ime_picker_failed);
        }
    }

    private void captureLanguageShortcut() {
        LanguageSwitchShortcut current = repository.getLanguageShortcut();

        KeyCaptureDialog.show(this, current, this::saveLanguageShortcut);
    }

    private void saveLanguageShortcut(
            int keyCode, boolean ctrl, boolean alt, boolean shift, boolean meta) {
        try {
            repository.saveLanguageShortcut(new LanguageSwitchShortcut(
                    keyCode, ctrl, alt, shift, meta));
            refreshLanguageShortcut();
            toast(R.string.shortcut_saved);
        } catch (RuntimeException exception) {
            Log.e(TAG, "Unable to save language shortcut", exception);
            toast(R.string.shortcut_save_failed);
        }
    }

    private void addHotkey() {
        KeyCaptureDialog.show(this, null, this::showAppPicker);
    }

    private void showAppPicker(
            int keyCode, boolean ctrl, boolean alt, boolean shift, boolean meta) {
        AppPickerDialog.show(this, (appName, packageName) ->
                saveHotkey(keyCode, ctrl, alt, shift, meta, appName, packageName));
    }

    private void saveHotkey(
            int keyCode,
            boolean ctrl,
            boolean alt,
            boolean shift,
            boolean meta,
            String appName,
            String packageName) {
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
        } catch (RuntimeException exception) {
            Log.e(TAG, "Unable to save hotkey binding", exception);
            toast(R.string.shortcut_save_failed);
        }
    }

    private void deleteBinding(HotkeyBinding binding) {
        try {
            repository.deleteBinding(binding);
            refreshHotkeys();
            toast(R.string.shortcut_deleted);
        } catch (RuntimeException exception) {
            Log.e(TAG, "Unable to delete hotkey binding", exception);
            toast(R.string.shortcut_save_failed);
        }
    }

    private void toast(int messageResId) {
        Toast.makeText(this, messageResId, Toast.LENGTH_SHORT).show();
    }
}
