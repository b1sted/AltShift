package ru.b1sted.altshift.ui;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Objects;

import ru.b1sted.altshift.R;
import ru.b1sted.altshift.databinding.ActivityOnboardingBinding;
import ru.b1sted.altshift.service.InputMethodSwitcher;
import ru.b1sted.altshift.storage.HotkeyRepository;
import ru.b1sted.altshift.util.BatteryHelper;

public final class OnboardingActivity extends AppCompatActivity {

    public static final String EXTRA_RERUN =
            "ru.b1sted.altshift.extra.RERUN_ONBOARDING";

    private static final String TAG = "AltShift";
    private static final int REQUEST_NOTIFICATIONS = 2001;
    private static final int STEP_ENABLE = 0;
    private static final int STEP_SELECT = 1;
    private static final int STEP_PERMISSIONS = 2;

    private ActivityOnboardingBinding binding;
    private HotkeyRepository repository;
    private int currentStep = STEP_ENABLE;
    private boolean notificationRequested;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        repository = Objects.requireNonNull(
                HotkeyRepository.getInstance(this), "repository");
        boolean rerun = getIntent().getBooleanExtra(EXTRA_RERUN, false);
        if (!rerun && repository.isOnboardingCompleted()) {
            openDashboard();

            return;
        }

        binding = ActivityOnboardingBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.primaryButton.setOnClickListener(view -> handlePrimaryAction());
        binding.secondaryButton.setOnClickListener(view -> handleSecondaryAction());
        binding.batteryButton.setOnClickListener(view -> showBatteryRationale());

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (currentStep > STEP_ENABLE) {
                    showStep(currentStep - 1);

                    return;
                }

                finish();
            }
        });

        showStep(rerun ? STEP_ENABLE : firstIncompleteStep());
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (binding == null) {
            return;
        }

        refreshState();
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode != REQUEST_NOTIFICATIONS) {
            return;
        }

        refreshState();
    }

    private int firstIncompleteStep() {
        if (!isImeEnabled() || !hasAlternativeKeyboard()) {
            return STEP_ENABLE;
        }

        if (!isAltShiftActive()) {
            return STEP_SELECT;
        }

        return STEP_PERMISSIONS;
    }

    private void showStep(int step) {
        currentStep = Math.max(STEP_ENABLE, Math.min(STEP_PERMISSIONS, step));
        binding.stepLabel.setText(getString(
                R.string.onboarding_step_label, currentStep + 1));

        updateDots();
        updateStepContent();
        refreshState();
    }

    private void updateStepContent() {
        switch (currentStep) {
            case STEP_ENABLE -> {
                binding.stepIcon.setImageResource(R.drawable.ic_step_enable);
                binding.stepTitle.setText(R.string.onboarding_step_enable_title);
                binding.stepDescription.setText(
                        R.string.onboarding_step_enable_description);
            }
            case STEP_SELECT -> {
                binding.stepIcon.setImageResource(R.drawable.ic_step_select);
                binding.stepTitle.setText(R.string.onboarding_step_select_title);
                binding.stepDescription.setText(
                        R.string.onboarding_step_select_description);
            }
            default -> {
                binding.stepIcon.setImageResource(R.drawable.ic_step_permissions);
                binding.stepTitle.setText(R.string.onboarding_step_permissions_title);
                binding.stepDescription.setText(
                        R.string.onboarding_step_permissions_description);
            }
        }
    }

    private void refreshState() {
        boolean imeEnabled = isImeEnabled();
        boolean imeActive = isAltShiftActive();
        boolean overlayGranted = Settings.canDrawOverlays(this);
        boolean notificationsGranted = hasNotificationPermission();

        switch (currentStep) {
            case STEP_ENABLE -> refreshEnableStep(imeEnabled);
            case STEP_SELECT -> refreshSelectStep(imeEnabled, imeActive);
            default -> refreshPermissionsStep(overlayGranted, notificationsGranted);
        }
    }

    private void refreshEnableStep(boolean imeEnabled) {
        boolean alternativeAvailable = hasAlternativeKeyboard();
        binding.stepStatus.setVisibility(View.VISIBLE);
        binding.permissionsCard.setVisibility(View.GONE);

        if (!imeEnabled) {
            binding.primaryButton.setText(R.string.onboarding_step_enable_action);
            binding.stepStatus.setText(R.string.onboarding_step_enable_status_pending);
            binding.stepStatus.setTextColor(getColor(R.color.brand_error));
        } else if (!alternativeAvailable) {
            binding.primaryButton.setText(
                    R.string.onboarding_enable_soft_keyboard_action);
            binding.stepStatus.setText(
                    R.string.onboarding_step_enable_status_no_soft_keyboard);
            binding.stepStatus.setTextColor(getColor(R.color.brand_error));
        } else {
            binding.primaryButton.setText(R.string.onboarding_step_continue);
            binding.stepStatus.setText(R.string.onboarding_step_enable_status_ok);
            binding.stepStatus.setTextColor(getColor(R.color.brand_success));
        }

        binding.secondaryButton.setVisibility(View.GONE);
    }

    private void refreshSelectStep(boolean imeEnabled, boolean imeActive) {
        binding.secondaryButton.setVisibility(View.GONE);
        binding.stepStatus.setVisibility(View.VISIBLE);
        binding.permissionsCard.setVisibility(View.GONE);

        if (!imeEnabled) {
            binding.primaryButton.setText(R.string.onboarding_step_enable_action);
            binding.stepStatus.setText(R.string.onboarding_step_select_status_blocked);
            binding.stepStatus.setTextColor(getColor(R.color.brand_error));

            return;
        }

        if (!hasAlternativeKeyboard()) {
            binding.primaryButton.setText(
                    R.string.onboarding_enable_soft_keyboard_action);
            binding.stepStatus.setText(
                    R.string.onboarding_step_select_status_no_soft_keyboard);
            binding.stepStatus.setTextColor(getColor(R.color.brand_error));

            return;
        }

        binding.primaryButton.setText(imeActive
                ? R.string.onboarding_step_continue
                : R.string.onboarding_step_select_action);
        binding.stepStatus.setText(imeActive
                ? R.string.onboarding_step_select_status_ok
                : R.string.onboarding_step_select_status_pending);
        binding.stepStatus.setTextColor(getColor(imeActive
                ? R.color.brand_success
                : R.color.brand_error));
    }

    private void refreshPermissionsStep(
            boolean overlayGranted, boolean notificationsGranted) {
        boolean notificationsRequired =
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                        && !notificationsGranted;

        binding.stepStatus.setVisibility(View.GONE);
        binding.permissionsCard.setVisibility(View.VISIBLE);

        bindPermissionStatus(binding.overlayDot, binding.overlayStatus, overlayGranted,
                R.string.onboarding_overlay_granted,
                R.string.onboarding_overlay_pending,
                R.color.brand_success,
                R.color.brand_error);
        bindPermissionStatus(binding.notificationsDot, binding.notificationsStatus,
                notificationsGranted,
                R.string.onboarding_notifications_granted,
                R.string.onboarding_notifications_pending,
                R.color.brand_success,
                R.color.brand_error);

        if (!overlayGranted) {
            binding.primaryButton.setText(R.string.onboarding_step_permissions_action);
        } else if (notificationsRequired) {
            binding.primaryButton.setText(R.string.onboarding_notifications_action);
        } else {
            binding.primaryButton.setText(R.string.onboarding_step_finish);
        }

        boolean showSecondary = notificationsRequired;
        binding.secondaryButton.setVisibility(showSecondary ? View.VISIBLE : View.GONE);
        binding.secondaryButton.setText(overlayGranted
                ? R.string.onboarding_finish_anyway
                : R.string.onboarding_notifications_action);

        refreshBatterySection();

        if (notificationsRequired && !notificationRequested) {
            notificationRequested = true;
            requestNotificationPermission();
        }
    }

    private void refreshBatterySection() {
        boolean unrestricted = BatteryHelper.isIgnoringBatteryOptimizations(this);

        bindPermissionStatus(binding.batteryDot, binding.batteryStatus, unrestricted,
                R.string.onboarding_background_granted,
                R.string.onboarding_background_restricted,
                R.color.brand_success,
                R.color.brand_on_surface_variant);
        binding.batteryDescription.setVisibility(unrestricted ? View.GONE : View.VISIBLE);
        binding.batteryButton.setVisibility(unrestricted ? View.GONE : View.VISIBLE);
    }

    private void bindPermissionStatus(
            View dot, TextView status, boolean granted,
            int grantedResId, int pendingResId,
            int grantedColorResId, int pendingColorResId) {
        int colorResId = granted ? grantedColorResId : pendingColorResId;

        tintStatusDot(dot, colorResId);
        status.setText(granted ? grantedResId : pendingResId);
        status.setTextColor(getColor(colorResId));
    }

    private void tintStatusDot(View dot, int colorResId) {
        Drawable background = dot.getBackground();
        if (background == null) {
            return;
        }

        background.mutate().setTint(getColor(colorResId));
    }

    private void showBatteryRationale() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.onboarding_background_title)
                .setMessage(R.string.onboarding_background_rationale)
                .setPositiveButton(R.string.onboarding_background_continue,
                        (dialog, which) -> BatteryHelper.requestIgnoreBatteryOptimizations(this))
                .setNegativeButton(R.string.onboarding_background_cancel, null)
                .show();
    }

    private void handleSecondaryAction() {
        if (currentStep == STEP_PERMISSIONS
                && Settings.canDrawOverlays(this)
                && !hasNotificationPermission()) {
            completeOnboarding();

            return;
        }

        requestNotificationPermission();
    }

    private void handlePrimaryAction() {
        switch (currentStep) {
            case STEP_ENABLE -> {
                if (!isImeEnabled() || !hasAlternativeKeyboard()) {
                    openImeSettings();

                    return;
                }

                showStep(STEP_SELECT);
            }
            case STEP_SELECT -> {
                if (!isImeEnabled() || !hasAlternativeKeyboard()) {
                    openImeSettings();

                    return;
                }

                if (isAltShiftActive()) {
                    showStep(STEP_PERMISSIONS);

                    return;
                }

                showImePicker();
            }
            default -> {
                if (!Settings.canDrawOverlays(this)) {
                    openOverlaySettings();

                    return;
                }

                if (!hasNotificationPermission()) {
                    requestNotificationPermission();

                    return;
                }

                completeOnboarding();
            }
        }
    }

    private void completeOnboarding() {
        repository.setOnboardingCompleted(true);
        openDashboard();
    }

    private void openDashboard() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }

    private boolean isImeEnabled() {
        return InputMethodSwitcher.isAltShiftEnabled(this);
    }

    private boolean isAltShiftActive() {
        return InputMethodSwitcher.isAltShiftActive(this);
    }

    private boolean hasAlternativeKeyboard() {
        return InputMethodSwitcher.hasAlternativeInputMethod(this);
    }

    private boolean hasNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return true;
        }

        return ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return;
        }

        if (hasNotificationPermission()) {
            return;
        }

        ActivityCompat.requestPermissions(
                this,
                new String[]{Manifest.permission.POST_NOTIFICATIONS},
                REQUEST_NOTIFICATIONS);
    }

    private void openImeSettings() {
        Intent intent = new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS);

        if (!tryStartActivity(intent)) {
            toast(R.string.settings_open_failed);
        }
    }

    private void openOverlaySettings() {
        Intent packageIntent = new Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName()));

        if (tryStartActivity(packageIntent)) {
            return;
        }

        Intent genericIntent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
        if (!tryStartActivity(genericIntent)) {
            toast(R.string.overlay_settings_failed);
        }
    }

    private boolean tryStartActivity(Intent intent) {
        try {
            startActivity(intent);

            return true;
        } catch (RuntimeException exception) {
            Log.w(TAG, "Cannot resolve activity for intent: " + intent, exception);

            return false;
        }
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

    private void updateDots() {
        binding.stepDotOne.setBackgroundResource(currentStep == STEP_ENABLE
                ? R.drawable.bg_dot_active
                : R.drawable.bg_dot_inactive);
        binding.stepDotTwo.setBackgroundResource(currentStep == STEP_SELECT
                ? R.drawable.bg_dot_active
                : R.drawable.bg_dot_inactive);
        binding.stepDotThree.setBackgroundResource(currentStep == STEP_PERMISSIONS
                ? R.drawable.bg_dot_active
                : R.drawable.bg_dot_inactive);
    }

    private void toast(int messageResId) {
        Toast.makeText(this, messageResId, Toast.LENGTH_SHORT).show();
    }
}
