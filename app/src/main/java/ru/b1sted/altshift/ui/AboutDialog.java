package ru.b1sted.altshift.ui;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.InputDevice;
import android.view.LayoutInflater;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import ru.b1sted.altshift.BuildConfig;
import ru.b1sted.altshift.R;
import ru.b1sted.altshift.databinding.DialogAboutBinding;
import ru.b1sted.altshift.service.InputMethodSwitcher;
import ru.b1sted.altshift.service.KeyboardPresenceMonitor;

public final class AboutDialog {

    private static final String TAG = "AltShift";
    private static final String REPOSITORY_URL = "https://github.com/b1sted/AltShift";

    private AboutDialog() {
    }

    public static void show(Context context) {
        Objects.requireNonNull(context, "context");

        DialogAboutBinding binding =
                DialogAboutBinding.inflate(LayoutInflater.from(context));

        AlertDialog dialog = new MaterialAlertDialogBuilder(context)
                .setView(binding.getRoot())
                .setPositiveButton(R.string.about_close, null)
                .create();

        binding.versionText.setText(context.getString(
                R.string.about_version_format,
                BuildConfig.VERSION_NAME,
                BuildConfig.VERSION_CODE));
        binding.githubButton.setOnClickListener(view -> openRepository(context));
        binding.rerunWizardButton.setOnClickListener(view -> {
            dialog.dismiss();
            rerunSetupWizard(context);
        });

        dialog.show();
        loadDiagnosticsAsync(context.getApplicationContext(), dialog, binding);
    }

    private static void loadDiagnosticsAsync(
            Context context, AlertDialog dialog, DialogAboutBinding binding) {
        Handler mainHandler = new Handler(Looper.getMainLooper());
        ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "altshift-diagnostics");
            thread.setDaemon(true);

            return thread;
        });

        executor.execute(() -> {
            try {
                Diagnostics diagnostics = readDiagnostics(context);

                mainHandler.post(() -> publishDiagnostics(dialog, binding, diagnostics));
            } catch (RuntimeException exception) {
                Log.e(TAG, "Unable to read hardware diagnostics", exception);

                mainHandler.post(() -> publishFailure(dialog, binding));
            } finally {
                executor.shutdown();
            }
        });
    }

    private static Diagnostics readDiagnostics(Context context) {
        InputDevice keyboard = KeyboardPresenceMonitor.findPhysicalKeyboard(context);

        return new Diagnostics(
                readKeyboardName(context, keyboard),
                readHardwareIds(context, keyboard),
                readActiveInputMethod(context),
                readAutoSwitchMode(context));
    }

    private static String readKeyboardName(Context context, InputDevice keyboard) {
        if (keyboard == null) {
            return context.getString(R.string.about_keyboard_none);
        }

        String name = keyboard.getName();
        if (TextUtils.isEmpty(name)) {
            return context.getString(R.string.about_value_unknown);
        }

        return name;
    }

    private static String readHardwareIds(Context context, InputDevice keyboard) {
        if (keyboard == null) {
            return context.getString(R.string.about_hardware_ids_unknown);
        }

        int vendorId = keyboard.getVendorId();
        int productId = keyboard.getProductId();
        if (vendorId == 0 && productId == 0) {
            return context.getString(R.string.about_hardware_ids_unknown);
        }

        return context.getString(
                R.string.about_hardware_ids_format, vendorId, productId);
    }

    private static String readActiveInputMethod(Context context) {
        String active = InputMethodSwitcher.readActiveInputMethod(context);
        if (TextUtils.isEmpty(active)) {
            return context.getString(R.string.about_value_unknown);
        }

        return active;
    }

    private static String readAutoSwitchMode(Context context) {
        return context.getString(
                InputMethodSwitcher.hasSecureSettingsPermission(context)
                        ? R.string.about_auto_switch_autonomous
                        : R.string.about_auto_switch_interactive);
    }

    private static void publishDiagnostics(
            AlertDialog dialog, DialogAboutBinding binding, Diagnostics diagnostics) {
        if (!dialog.isShowing()) {
            return;
        }

        binding.keyboardValue.setText(diagnostics.keyboardName());
        binding.hardwareIdsValue.setText(diagnostics.hardwareIds());
        binding.activeImeValue.setText(diagnostics.activeIme());
        binding.autoSwitchValue.setText(diagnostics.autoSwitchMode());
    }

    private static void publishFailure(AlertDialog dialog, DialogAboutBinding binding) {
        if (!dialog.isShowing()) {
            return;
        }

        String unavailable = binding.getRoot().getContext()
                .getString(R.string.about_value_unavailable);
        binding.keyboardValue.setText(unavailable);
        binding.hardwareIdsValue.setText(unavailable);
        binding.activeImeValue.setText(unavailable);
        binding.autoSwitchValue.setText(unavailable);
    }

    private static void openRepository(Context context) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(REPOSITORY_URL));

        if (!tryStartActivity(context, intent)) {
            toast(context, R.string.about_repository_failed);
        }
    }

    private static void rerunSetupWizard(Context context) {
        Intent intent = new Intent(context, OnboardingActivity.class);
        intent.putExtra(OnboardingActivity.EXTRA_RERUN, true);

        if (!tryStartActivity(context, intent)) {
            toast(context, R.string.about_wizard_failed);
        }
    }

    private static boolean tryStartActivity(Context context, Intent intent) {
        try {
            context.startActivity(intent);

            return true;
        } catch (RuntimeException exception) {
            Log.w(TAG, "Cannot resolve activity for intent: " + intent, exception);

            return false;
        }
    }

    private static void toast(Context context, int messageResId) {
        Toast.makeText(context, messageResId, Toast.LENGTH_SHORT).show();
    }

    private record Diagnostics(
            String keyboardName,
            String hardwareIds,
            String activeIme,
            String autoSwitchMode) {
    }
}
