package ru.b1sted.altshift.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;

import ru.b1sted.altshift.service.InputMethodSwitcher;
import ru.b1sted.altshift.service.KeyboardNotification;
import ru.b1sted.altshift.service.KeyboardPromptOverlay;

/**
 * Transparent trampoline that opens the native input method picker and grades
 * the user's choice only after the system dialog has been dismissed.
 */
public final class SwitchBackActivity extends Activity {

    private static final String TAG = "AltShift";

    /**
     * One frame is not enough for the platform to register this window as the
     * active input client, so the picker request is briefly deferred.
     */
    private static final long PICKER_LAUNCH_DELAY_MS = 200L;
    private static final long SAFETY_FINISH_MS = 15000L;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private InputClientView inputClient;
    private boolean pickerScheduled;
    private boolean pickerRequested;
    private boolean pickerLostFocus;
    private boolean pickerDismissed;

    public static void start(Context context) {
        if (context == null) {
            return;
        }

        Intent intent = new Intent(context, SwitchBackActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);

        try {
            context.startActivity(intent);
            Log.i(TAG, "Switch-back trampoline started");
        } catch (RuntimeException exception) {
            Log.w(TAG, "Unable to open the input method picker", exception);
        }
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        overridePendingTransition(0, 0);

        // Gboard's stylus handwriting session can suppress the system picker.
        InputMethodSwitcher.resetStylusHandwritingSession(this);
        KeyboardPromptOverlay.dismissActive();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
        setContentView(createInputClient());
        handler.postDelayed(this::finishSilently, SAFETY_FINISH_MS);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);

        if (!hasFocus) {
            // The picker is an independent system window. Losing focus is the
            // signal that it is now on screen and owns the interaction.
            pickerLostFocus = pickerRequested;

            return;
        }

        if (!pickerScheduled) {
            requestPicker();

            return;
        }

        // Ignore focus churn during the launch delay: only a picker that was
        // actually requested can be graded when focus comes back.
        if (pickerRequested && pickerLostFocus) {
            onPickerDismissed();
            finishSilently();
        }
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        inputClient = null;

        super.onDestroy();
    }

    /**
     * The platform ignores picker requests from clients that are not the active
     * input client. This minimal editor registers the trampoline as that client
     * without showing a text field or an on-screen keyboard.
     */
    private View createInputClient() {
        FrameLayout root = new FrameLayout(this);
        InputClientView client = new InputClientView(this);
        client.setFocusable(true);
        client.setFocusableInTouchMode(true);
        root.addView(client, new FrameLayout.LayoutParams(1, 1));
        inputClient = client;
        client.requestFocus();

        return root;
    }

    private void requestPicker() {
        if (pickerScheduled) {
            return;
        }

        pickerScheduled = true;
        handler.postDelayed(this::showPicker, PICKER_LAUNCH_DELAY_MS);
    }

    private void showPicker() {
        if (isFinishing() || isDestroyed() || pickerDismissed) {
            return;
        }

        InputMethodManager manager = getSystemService(InputMethodManager.class);
        if (manager == null) {
            Log.w(TAG, "InputMethodManager is unavailable for the picker");

            finishSilently();

            return;
        }

        focusInputClient();
        pickerRequested = true;

        try {
            manager.showInputMethodPicker();
            Log.i(TAG, "System input method picker requested");
        } catch (RuntimeException exception) {
            Log.w(TAG, "Unable to show the input method picker", exception);

            finishSilently();
        }
    }

    /**
     * Focus can only return here after the picker dialog is gone, so this is
     * the single point where the pending switch attempt is evaluated.
     */
    private void onPickerDismissed() {
        if (pickerDismissed) {
            return;
        }

        pickerDismissed = true;

        if (!InputMethodSwitcher.isAltShiftActive(this)) {
            KeyboardNotification.showInactiveHeadsUp(this);

            return;
        }

        KeyboardNotification.cancel(this);
    }

    private void focusInputClient() {
        InputClientView client = inputClient;
        if (client != null && !client.hasFocus()) {
            client.requestFocus();
        }
    }

    /**
     * Timeout path for a picker that never took focus. It must never post a
     * notification because the user did not finish a deliberate interaction.
     */
    private void finishSilently() {
        if (isFinishing() || isDestroyed()) {
            return;
        }

        finish();
        overridePendingTransition(0, 0);
    }

    private static final class InputClientView extends View {

        InputClientView(Context context) {
            super(context);
        }

        @Override
        public boolean onCheckIsTextEditor() {
            return true;
        }

        @Override
        public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
            outAttrs.inputType = InputType.TYPE_CLASS_TEXT;
            outAttrs.imeOptions = EditorInfo.IME_ACTION_NONE;

            return new BaseInputConnection(this, false);
        }
    }
}
