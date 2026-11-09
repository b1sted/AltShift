package ru.b1sted.altshift.ui;

import android.Manifest;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.List;
import java.util.Objects;

import ru.b1sted.altshift.R;
import ru.b1sted.altshift.databinding.ActivityOnboardingBinding;
import ru.b1sted.altshift.service.HardwareInputService;
import ru.b1sted.altshift.storage.HotkeyRepository;

public final class OnboardingActivity extends AppCompatActivity {

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
        if (repository.isOnboardingCompleted()) {
            openDashboard();

            return;
        }

        binding = ActivityOnboardingBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.primaryButton.setOnClickListener(view -> handlePrimaryAction());
        binding.secondaryButton.setOnClickListener(
                view -> requestNotificationPermission());

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

        showStep(firstIncompleteStep());
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
        if (!isImeEnabled()) {
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
        binding.primaryButton.setText(imeEnabled
                ? R.string.onboarding_step_continue
                : R.string.onboarding_step_enable_action);
        binding.stepStatus.setText(imeEnabled
                ? R.string.onboarding_step_enable_status_ok
                : R.string.onboarding_step_enable_status_pending);
        binding.stepStatus.setTextColor(getColor(imeEnabled
                ? R.color.brand_success
                : R.color.brand_error));
        binding.secondaryButton.setVisibility(View.GONE);
    }

    private void refreshSelectStep(boolean imeEnabled, boolean imeActive) {
        binding.secondaryButton.setVisibility(View.GONE);

        if (!imeEnabled) {
            binding.primaryButton.setText(R.string.onboarding_step_enable_action);
            binding.stepStatus.setText(R.string.onboarding_step_select_status_blocked);
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
        binding.primaryButton.setText(overlayGranted
                ? R.string.onboarding_step_finish
                : R.string.onboarding_step_permissions_action);

        String overlayStatus = getString(overlayGranted
                ? R.string.onboarding_overlay_granted
                : R.string.onboarding_overlay_pending);
        String notificationStatus = getString(notificationsGranted
                ? R.string.onboarding_notifications_granted
                : R.string.onboarding_notifications_pending);
        binding.stepStatus.setText(getString(
                R.string.onboarding_status_two_lines, overlayStatus, notificationStatus));
        binding.stepStatus.setTextColor(getColor(
                overlayGranted && notificationsGranted
                        ? R.color.brand_success
                        : R.color.brand_error));

        boolean showNotificationAction =
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                        && !notificationsGranted;
        binding.secondaryButton.setVisibility(
                showNotificationAction ? View.VISIBLE : View.GONE);
        binding.secondaryButton.setText(R.string.onboarding_notifications_action);

        if (showNotificationAction && !notificationRequested) {
            notificationRequested = true;
            requestNotificationPermission();
        }
    }

    private void handlePrimaryAction() {
        switch (currentStep) {
            case STEP_ENABLE -> {
                if (isImeEnabled()) {
                    showStep(STEP_SELECT);

                    return;
                }

                openImeSettings();
            }
            case STEP_SELECT -> {
                if (!isImeEnabled()) {
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
        List<InputMethodInfo> methods = readEnabledInputMethods();
        if (methods == null) {
            return false;
        }

        ComponentName component = new ComponentName(this, HardwareInputService.class);

        for (int index = 0; index < methods.size(); index++) {
            if (component.equals(methods.get(index).getComponent())) {
                return true;
            }
        }

        return false;
    }

    @Nullable
    private List<InputMethodInfo> readEnabledInputMethods() {
        try {
            InputMethodManager manager = getSystemService(InputMethodManager.class);
            if (manager == null) {
                return null;
            }

            return manager.getEnabledInputMethodList();
        } catch (RuntimeException exception) {
            Log.e(TAG, "Unable to query enabled input methods", exception);

            return null;
        }
    }

    private boolean isAltShiftActive() {
        try {
            String active = Settings.Secure.getString(
                    getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);
            if (TextUtils.isEmpty(active)) {
                return false;
            }

            ComponentName component = new ComponentName(this, HardwareInputService.class);

            return active.contains(component.flattenToString())
                    || active.contains(component.flattenToShortString());
        } catch (RuntimeException exception) {
            Log.w(TAG, "Unable to read the active input method", exception);

            return false;
        }
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
