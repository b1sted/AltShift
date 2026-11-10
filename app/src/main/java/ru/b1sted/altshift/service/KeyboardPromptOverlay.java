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
    private static final Object LOCK = new Object();

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

        if (Looper.myLooper() != Looper.getMainLooper()) {
            Log.w(TAG, "Overlay must be shown from the main thread");

            return false;
        }

        synchronized (LOCK) {
            KeyboardPromptOverlay current = active;
            if (current != null && current.shown) {
                return true;
            }
        }

        KeyboardPromptOverlay overlay = new KeyboardPromptOverlay(appContext);
        if (!overlay.showInternal(onSwitch)) {
            return false;
        }

        synchronized (LOCK) {
            KeyboardPromptOverlay previous = active;
            active = overlay;

            if (previous != null && previous != overlay) {
                previous.dismissInternal();
            }
        }

        return true;
    }

    public static void dismiss() {
        KeyboardPromptOverlay overlay;

        synchronized (LOCK) {
            overlay = active;
        }

        if (overlay != null) {
            overlay.dismissInternal();
        }
    }

    private boolean showInternal(Runnable onSwitch) {
        synchronized (LOCK) {
            if (shown) {
                return true;
            }

            if (windowManager == null) {
                return false;
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
                ViewKeyboardPromptBinding failed = binding;
                binding = null;
                shown = false;

                removeFailedView(failed);

                return false;
            }
        }
    }

    private void removeFailedView(ViewKeyboardPromptBinding failed) {
        if (failed == null || windowManager == null) {
            return;
        }

        View root = failed.getRoot();
        if (root == null || !root.isAttachedToWindow()) {
            return;
        }

        try {
            windowManager.removeViewImmediate(root);
        } catch (RuntimeException exception) {
            Log.w(TAG, "Unable to remove a partially added overlay", exception);
        }
    }

    private void dismissSelf() {
        dismissInternal();
    }

    private void dismissInternal() {
        handler.removeCallbacks(dismissRunnable);

        if (Looper.myLooper() != Looper.getMainLooper()) {
            handler.post(this::dismissInternal);

            return;
        }

        ViewKeyboardPromptBinding local;
        View root;

        synchronized (LOCK) {
            local = binding;
            if (local == null) {
                clearLocked(null);

                return;
            }

            root = local.getRoot();
            if (root == null || !root.isAttachedToWindow()) {
                clearLocked(local);

                return;
            }
        }

        boolean removed = false;
        WindowManager manager = windowManager;

        if (manager != null) {
            try {
                manager.removeViewImmediate(root);
                removed = true;
            } catch (IllegalArgumentException exception) {
                Log.w(TAG, "Overlay was already detached from the window manager", exception);
                removed = true;
            } catch (RuntimeException exception) {
                Log.w(TAG, "Unable to remove the keyboard prompt overlay", exception);
            }
        }

        synchronized (LOCK) {
            if (removed || !root.isAttachedToWindow()) {
                clearLocked(local);
                Log.d(TAG, "Keyboard prompt overlay dismissed");

                return;
            }

            Log.w(TAG, "Overlay is still attached; keeping state for a retry");
        }
    }

    private void clearLocked(ViewKeyboardPromptBinding expected) {
        if (expected == null || binding == expected) {
            binding = null;
        }

        shown = false;
        if (active == this) {
            active = null;
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
