package ru.b1sted.altshift.ui;

import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import java.util.Objects;

import ru.b1sted.altshift.R;
import ru.b1sted.altshift.databinding.ActivityMainBinding;
import ru.b1sted.altshift.model.HotkeyBinding;
import ru.b1sted.altshift.model.LanguageSwitchShortcut;
import ru.b1sted.altshift.service.HardwareInputService;
import ru.b1sted.altshift.storage.HotkeyRepository;

public final class MainActivity extends AppCompatActivity {

    private static final String TAG = "AltShift";

    private ActivityMainBinding binding;
    private HotkeyRepository repository;
    private HotkeyAdapter adapter;

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

        binding.imeSettingsButton.setOnClickListener(view -> openImeSettings());
        binding.languageShortcutButton.setOnClickListener(
                view -> captureLanguageShortcut());
        binding.addHotkeyFab.setOnClickListener(view -> addHotkey());

        refreshAll();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshAll();
    }

    private void refreshAll() {
        refreshImeStatus();
        refreshLanguageShortcut();
        refreshHotkeys();
    }

    private void refreshImeStatus() {
        final ComponentName component =
                new ComponentName(this, HardwareInputService.class);
        final String enabled = Settings.Secure.getString(
                getContentResolver(), Settings.Secure.ENABLED_INPUT_METHODS);
        final String active = Settings.Secure.getString(
                getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);
        final boolean isEnabled = matchesComponent(enabled, component);
        final boolean isActive = matchesComponent(active, component);

        if (isActive) {
            binding.imeStatusText.setText(R.string.ime_status_active);
            binding.imeSettingsButton.setVisibility(View.GONE);
            return;
        }
        if (isEnabled) {
            binding.imeStatusText.setText(R.string.ime_status_enabled_not_selected);
        } else {
            binding.imeStatusText.setText(R.string.ime_status_disabled);
        }
        binding.imeSettingsButton.setVisibility(View.VISIBLE);
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
