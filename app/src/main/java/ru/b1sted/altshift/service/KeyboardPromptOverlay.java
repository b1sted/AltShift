package ru.b1sted.altshift.service;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.WindowMetrics;

import ru.b1sted.altshift.R;
import ru.b1sted.altshift.databinding.ViewKeyboardPromptBinding;

/**
 * Floating bottom capsule that offers a direct return to AltShift.
 */
public final class KeyboardPromptOverlay {

    private static final String TAG = "AltShift";
    private static final long AUTO_DISMISS_MS = 12000L;

    @SuppressLint("StaticFieldLeak")
    private static KeyboardPromptOverlay active;

    private final Context context;
    private final WindowManager windowManager;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable dismissRunnable = this::dismissSelf;

    private ViewKeyboardPromptBinding binding;
    private boolean shown;

    private KeyboardPromptOverlay(Context context) {
        this.context = context;
        windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
    }

    public static boolean show(Context context, Runnable onSwitch) {
        if (context == null || onSwitch == null) {
            return false;
        }

        Context appContext = context.getApplicationContext();
        if (!Settings.canDrawOverlays(appContext)) {
            Log.w(TAG, "Overlay permission is not granted");

            return false;
        }

        KeyboardPromptOverlay current = active;
        if (current != null && current.shown) {
            return true;
        }

        KeyboardPromptOverlay overlay = new KeyboardPromptOverlay(appContext);
        if (!overlay.showInternal(onSwitch)) {
            return false;
        }

        active = overlay;

        return true;
    }

    public static void dismiss() {
        KeyboardPromptOverlay overlay = active;
        active = null;

        if (overlay != null) {
            overlay.removeView();
        }
    }

    private boolean showInternal(Runnable onSwitch) {
        if (windowManager == null || shown) {
            return shown;
        }

        try {
            ContextThemeWrapper themedContext =
                    new ContextThemeWrapper(context, R.style.Theme_AltShift);
            ViewKeyboardPromptBinding inflated = ViewKeyboardPromptBinding.inflate(
                    LayoutInflater.from(themedContext));

            inflated.promptAction.setOnClickListener(view -> {
                dismissSelf();
                onSwitch.run();
            });
            inflated.promptClose.setOnClickListener(view -> dismissSelf());

            binding = inflated;
            windowManager.addView(inflated.getRoot(), createParams());

            shown = true;
            handler.postDelayed(dismissRunnable, AUTO_DISMISS_MS);
            Log.d(TAG, "Keyboard prompt overlay shown");

            return true;
        } catch (RuntimeException exception) {
            Log.w(TAG, "Unable to show the keyboard prompt overlay", exception);
            removeView();

            return false;
        }
    }

    private void dismissSelf() {
        removeView();

        if (active == this) {
            active = null;
        }
    }

    private void removeView() {
        handler.removeCallbacks(dismissRunnable);

        ViewKeyboardPromptBinding local = binding;
        binding = null;
        shown = false;

        if (local == null) {
            return;
        }

        View root = local.getRoot();
        if (root == null || !root.isAttachedToWindow()) {
            return;
        }

        WindowManager manager = windowManager;
        if (manager == null) {
            return;
        }

        try {
            manager.removeView(root);
            Log.d(TAG, "Keyboard prompt overlay dismissed");
        } catch (RuntimeException exception) {
            Log.w(TAG, "Unable to remove the keyboard prompt overlay", exception);
        }
    }

    private WindowManager.LayoutParams createParams() {
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        params.y = bottomInset() + dp(24);
        params.windowAnimations = R.style.Animation_AltShift_Prompt;

        return params;
    }

    private int bottomInset() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                WindowMetrics metrics = windowManager.getCurrentWindowMetrics();
                WindowInsets insets = metrics.getWindowInsets();

                return insets.getInsetsIgnoringVisibility(
                        WindowInsets.Type.navigationBars()).bottom;
            } catch (RuntimeException exception) {
                Log.w(TAG, "Unable to read navigation bar insets", exception);
            }
        }

        return legacyNavigationBarHeight();
    }

    @SuppressLint({"DiscouragedApi", "InternalInsetResource"})
    private int legacyNavigationBarHeight() {
        int resourceId = context.getResources().getIdentifier(
                "navigation_bar_height", "dimen", "android");
        if (resourceId <= 0) {
            return 0;
        }

        return context.getResources().getDimensionPixelSize(resourceId);
    }

    private int dp(int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
