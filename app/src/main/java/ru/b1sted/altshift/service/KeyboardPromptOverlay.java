package ru.b1sted.altshift.service;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Insets;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.WindowMetrics;

import androidx.annotation.RequiresApi;

import com.google.android.material.color.MaterialColors;

import ru.b1sted.altshift.R;
import ru.b1sted.altshift.databinding.ViewKeyboardPromptBinding;
import ru.b1sted.altshift.model.ThemeMode;
import ru.b1sted.altshift.storage.HotkeyRepository;
import ru.b1sted.altshift.ui.LocaleController;
import ru.b1sted.altshift.util.PermissionHelper;

/**
 * Floating bottom capsule that offers a direct return to AltShift.
 */
public final class KeyboardPromptOverlay {

    private static final String TAG = "AltShift";
    private static final long AUTO_DISMISS_MS = 12000L;
    private static final int FALLBACK_BOTTOM_PADDING_DP = 16;
    private static final int BOTTOM_OFFSET_DP = 20;
    private static final int PROMPT_HORIZONTAL_MARGIN_DP = 8;
    private static final int TABLET_MIN_WIDTH_DP = 600;
    private static final Object LOCK = new Object();

    @SuppressLint("StaticFieldLeak")
    private static KeyboardPromptOverlay active;

    private final Context context;
    private final WindowManager windowManager;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable dismissRunnable = this::dismissByUser;

    private ViewKeyboardPromptBinding binding;
    private Runnable onSwitch;
    private Runnable onDismissed;
    private volatile boolean shown;

    private KeyboardPromptOverlay(Context context) {
        this.context = context;
        windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
    }

    public static boolean show(Context context, Runnable onSwitch) {
        return show(context, onSwitch, null);
    }

    /**
     * Shows the overlay and reports a user-driven dismissal. The callback runs
     * only when the user taps the close button or the timer expires; an
     * explicit switch action and programmatic {@link #dismissActive()} never
     * invoke it.
     */
    public static boolean show(Context context, Runnable onSwitch, Runnable onDismissed) {
        if (context == null || onSwitch == null) {
            return false;
        }

        Context appContext = context.getApplicationContext();

        if (!PermissionHelper.canDrawOverlays(appContext)) {
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
                current.refreshAutoDismiss(onSwitch, onDismissed);

                return true;
            }
        }

        KeyboardPromptOverlay overlay = new KeyboardPromptOverlay(appContext);
        if (!overlay.showInternal(onSwitch, onDismissed)) {
            return false;
        }

        KeyboardPromptOverlay previous;
        synchronized (LOCK) {
            previous = active;
            active = overlay;
        }

        if (previous != null && previous != overlay) {
            previous.dismiss();
        }

        return true;
    }

    private void refreshAutoDismiss(Runnable switchAction, Runnable dismissedAction) {
        synchronized (LOCK) {
            onSwitch = switchAction;
            onDismissed = dismissedAction;
        }

        handler.removeCallbacks(dismissRunnable);
        handler.postDelayed(dismissRunnable, AUTO_DISMISS_MS);
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

    private boolean showInternal(Runnable switchAction, Runnable dismissedAction) {
        if (windowManager == null) {
            return false;
        }

        ViewKeyboardPromptBinding inflated;
        try {
            inflated = ViewKeyboardPromptBinding.inflate(
                    LayoutInflater.from(themedContext()));
        } catch (RuntimeException exception) {
            Log.w(TAG, "Unable to inflate the keyboard prompt overlay", exception);

            return false;
        }

        configureCardWidth(inflated);
        applyThemeTint(inflated);
        inflated.promptAction.setOnClickListener(view -> runSwitchAction());
        inflated.promptClose.setOnClickListener(view -> dismissByUser());

        try {
            windowManager.addView(inflated.getRoot(), createParams());
        } catch (RuntimeException exception) {
            Log.w(TAG, "Unable to show the keyboard prompt overlay", exception);
            removeFailedView(inflated);

            return false;
        }

        synchronized (LOCK) {
            onSwitch = switchAction;
            onDismissed = dismissedAction;
            binding = inflated;
            shown = true;
        }

        handler.postDelayed(dismissRunnable, AUTO_DISMISS_MS);
        Log.d(TAG, "Keyboard prompt overlay shown");

        return true;
    }

    /**
     * The overlay lives outside any Activity, so both the persisted theme and
     * the selected per-app language must be applied to the inflation context
     * explicitly. The application context on its own follows only the OS night
     * mode and locale, ignoring the in-app choices.
     */
    private Context themedContext() {
        Context localized = LocaleController.localizedContext(context);
        ThemeMode mode = HotkeyRepository.getInstance(context).getThemeMode();
        if (mode == ThemeMode.FOLLOW_SYSTEM) {
            return new ContextThemeWrapper(localized, R.style.Theme_AltShift);
        }

        Configuration configuration = new Configuration(
                localized.getResources().getConfiguration());
        configuration.uiMode = (configuration.uiMode & ~Configuration.UI_MODE_NIGHT_MASK)
                | nightMode(mode);

        return new ContextThemeWrapper(
                localized.createConfigurationContext(configuration),
                R.style.Theme_AltShift);
    }

    private int nightMode(ThemeMode mode) {
        return switch (mode) {
            case LIGHT -> Configuration.UI_MODE_NIGHT_NO;
            case DARK -> Configuration.UI_MODE_NIGHT_YES;
            default -> Configuration.UI_MODE_NIGHT_UNDEFINED;
        };
    }

    /**
     * The overlay is inflated with a plain themed inflater rather than an
     * AppCompat activity, so the XML app:tint attribute is not applied by the
     * framework factory. Resolve the same theme color explicitly or the white
     * vector source would stay invisible on the light capsule.
     */
    private void applyThemeTint(ViewKeyboardPromptBinding inflated) {
        int color = MaterialColors.getColor(
                inflated.getRoot(),
                com.google.android.material.R.attr.colorOnSurfaceVariant);
        ColorStateList tint = ColorStateList.valueOf(color);

        inflated.promptIcon.setImageTintList(tint);
        inflated.promptClose.setImageTintList(tint);
    }

    /**
     * The phone window has a fixed inset width, so the card must fill the padded
     * content area for the weighted message to shrink correctly. The tablet
     * capsule gets an exact 420dp width so the weighted message absorbs the
     * remaining space and pins the action and close buttons to the right edge.
     */
    private void configureCardWidth(ViewKeyboardPromptBinding inflated) {
        ViewGroup.LayoutParams params = inflated.promptCard.getLayoutParams();

        if (isCompactWidth()) {
            params.width = ViewGroup.LayoutParams.MATCH_PARENT;
        } else {
            params.width = context.getResources().getDimensionPixelSize(R.dimen.prompt_min_width);
        }

        inflated.promptCard.setLayoutParams(params);
    }

    private boolean isCompactWidth() {
        return context.getResources().getConfiguration().smallestScreenWidthDp
                < TABLET_MIN_WIDTH_DP;
    }

    private void runSwitchAction() {
        Runnable action;

        synchronized (LOCK) {
            action = onSwitch;
        }

        dismissInternal(false);

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
     * Programmatic dismissal used when the keyboard state changes or another
     * activity takes over. It never posts a fallback notification.
     */
    public void dismiss() {
        dismissInternal(false);
    }

    /**
     * User-driven dismissal through the close button or the auto-dismiss timer.
     */
    private void dismissByUser() {
        dismissInternal(true);
    }

    /**
     * Clears the logical state synchronously, then detaches the view on the main
     * thread. A failed detach can never leave {@link #shown} stuck as true.
     */
    private void dismissInternal(boolean notifyDismissed) {
        handler.removeCallbacks(dismissRunnable);

        Runnable dismissedAction;
        synchronized (LOCK) {
            shown = false;
            onSwitch = null;
            dismissedAction = notifyDismissed ? onDismissed : null;
            onDismissed = null;
        }

        if (Looper.myLooper() != Looper.getMainLooper()) {
            handler.post(() -> {
                removePromptView();

                if (dismissedAction != null) {
                    dismissedAction.run();
                }
            });

            return;
        }

        removePromptView();

        if (dismissedAction != null) {
            dismissedAction.run();
        }
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
                promptWidth(),
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        // The frame keeps 28dp of bottom padding for the shadow, so the window
        // may overlap the system inset by 20dp; the visible card then sits 8dp
        // above the navigation bar or gesture area.
        params.y = Math.max(0, bottomInset() - dp(BOTTOM_OFFSET_DP));
        params.windowAnimations = R.style.Animation_AltShift_Prompt;

        return params;
    }

    /**
     * Phones get an explicit inset width so the message can shrink and
     * ellipsize instead of pushing the action button off-screen. Tablets wrap
     * the card, which carries an exact 420dp width set after inflation.
     */
    private int promptWidth() {
        if (!isCompactWidth()) {
            return WindowManager.LayoutParams.WRAP_CONTENT;
        }

        int screenWidth = context.getResources().getDisplayMetrics().widthPixels;
        int availableWidth = screenWidth - dp(PROMPT_HORIZONTAL_MARGIN_DP) * 2;

        return availableWidth > 0
                ? availableWidth
                : WindowManager.LayoutParams.WRAP_CONTENT;
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
