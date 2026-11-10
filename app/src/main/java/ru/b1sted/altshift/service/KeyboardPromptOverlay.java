package ru.b1sted.altshift.service;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Insets;
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

import androidx.annotation.RequiresApi;

import ru.b1sted.altshift.R;
import ru.b1sted.altshift.databinding.ViewKeyboardPromptBinding;

/**
 * Floating bottom capsule that offers a direct return to AltShift.
 */
public final class KeyboardPromptOverlay {

    private static final String TAG = "AltShift";
    private static final long AUTO_DISMISS_MS = 12000L;
    private static final int FALLBACK_BOTTOM_PADDING_DP = 16;
    private static final Object LOCK = new Object();

    @SuppressLint("StaticFieldLeak")
    private static KeyboardPromptOverlay active;

    private final Context context;
    private final WindowManager windowManager;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable dismissRunnable = this::dismiss;

    private ViewKeyboardPromptBinding binding;
    private Runnable onSwitch;
    private volatile boolean shown;

    private KeyboardPromptOverlay(Context context) {
        this.context = context;
        windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
    }

    public static boolean show(Context context, Runnable onSwitch) {
        if (context == null || onSwitch == null) {
            return false;
        }

        Context appContext = context.getApplicationContext();

        try {
            if (!Settings.canDrawOverlays(appContext)) {
                Log.w(TAG, "Overlay permission is not granted");

                return false;
            }
        } catch (RuntimeException exception) {
            Log.w(TAG, "Unable to verify the overlay permission", exception);

            return false;
        }

        if (Looper.myLooper() != Looper.getMainLooper()) {
            Log.w(TAG, "Overlay must be shown from the main thread");

            return false;
        }

        synchronized (LOCK) {
            KeyboardPromptOverlay current = active;
            if (current != null && current.shown) {
                current.onSwitch = onSwitch;

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
                previous.dismiss();
            }
        }

        return true;
    }

    public static void dismissActive() {
        KeyboardPromptOverlay overlay;

        synchronized (LOCK) {
            overlay = active;
        }

        if (overlay != null) {
            overlay.dismiss();
        }
    }

    private boolean showInternal(Runnable switchAction) {
        synchronized (LOCK) {
            if (shown) {
                onSwitch = switchAction;

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

                onSwitch = switchAction;
                inflated.promptAction.setOnClickListener(view -> runSwitchAction());
                inflated.promptClose.setOnClickListener(view -> dismiss());

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
                onSwitch = null;
                shown = false;

                removeFailedView(failed);

                return false;
            }
        }
    }

    private void runSwitchAction() {
        Runnable action = onSwitch;
        dismiss();

        if (action != null) {
            action.run();
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

    /**
     * Clears the logical state synchronously, then detaches the view on the main
     * thread. A failed detach can never leave {@link #shown} stuck as true.
     */
    public void dismiss() {
        handler.removeCallbacks(dismissRunnable);
        shown = false;

        if (Looper.myLooper() != Looper.getMainLooper()) {
            handler.post(this::removePromptView);

            return;
        }

        removePromptView();
    }

    private void removePromptView() {
        ViewKeyboardPromptBinding local;
        synchronized (LOCK) {
            local = binding;
            binding = null;
            onSwitch = null;

            if (active == this) {
                active = null;
            }
        }

        if (local == null) {
            return;
        }

        WindowManager manager = windowManager;
        if (manager == null) {
            return;
        }

        try {
            View root = local.getRoot();
            if (root != null && root.isAttachedToWindow()) {
                manager.removeViewImmediate(root);
            }
        } catch (RuntimeException exception) {
            Log.w(TAG, "Unable to remove prompt view", exception);
        } finally {
            Log.d(TAG, "Keyboard prompt overlay dismissed");
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
            return modernBottomInset();
        }

        return legacyNavigationBarHeight();
    }

    /**
     * Reads the platform insets reported for the current window. Both the
     * navigation bar and the gesture area are considered, so the capsule stays
     * clear of three-button bars, gesture pills, and OEM-specific system bars.
     */
    @RequiresApi(Build.VERSION_CODES.R)
    private int modernBottomInset() {
        try {
            WindowMetrics metrics = windowManager.getCurrentWindowMetrics();
            WindowInsets insets = metrics.getWindowInsets();
            Insets navigation = insets.getInsetsIgnoringVisibility(
                    WindowInsets.Type.navigationBars());
            Insets gestures = insets.getInsetsIgnoringVisibility(
                    WindowInsets.Type.systemGestures());

            return Math.max(navigation.bottom, gestures.bottom);
        } catch (RuntimeException exception) {
            Log.w(TAG, "Unable to read navigation bar insets", exception);

            return legacyNavigationBarHeight();
        }
    }

    @SuppressLint({"DiscouragedApi", "InternalInsetResource"})
    private int legacyNavigationBarHeight() {
        int resourceId = context.getResources().getIdentifier(
                "navigation_bar_height", "dimen", "android");
        if (resourceId <= 0) {
            return dp(FALLBACK_BOTTOM_PADDING_DP);
        }

        return context.getResources().getDimensionPixelSize(resourceId);
    }

    private int dp(int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
