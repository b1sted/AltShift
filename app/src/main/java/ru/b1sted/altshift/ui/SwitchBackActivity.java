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

/**
 * Transparent trampoline that opens the native input method picker.
 */
public final class SwitchBackActivity extends Activity {

    private static final String TAG = "AltShift";
    private static final long PICKER_DELAY_MS = 200L;
    private static final long SAFETY_FINISH_MS = 15000L;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private boolean pickerRequested;
    private boolean pickerShown;

    public static void start(Context context) {
        if (context == null) {
            return;
        }

        Intent intent = new Intent(context, SwitchBackActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        try {
            context.startActivity(intent);
        } catch (RuntimeException exception) {
            Log.w(TAG, "Unable to open the input method picker", exception);
        }
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
        setContentView(createInputClient());
        handler.postDelayed(this::finish, SAFETY_FINISH_MS);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);

        if (!hasFocus) {
            if (pickerRequested) {
                pickerShown = true;
            }

            return;
        }

        if (pickerShown) {
            finish();

            return;
        }

        if (pickerRequested) {
            return;
        }

        pickerRequested = true;
        handler.postDelayed(this::showPicker, PICKER_DELAY_MS);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);

        super.onDestroy();
    }

    /**
     * The platform only accepts the picker request from the active input client.
     * This minimal editor registers the trampoline as that client without a
     * visible text field or an on-screen keyboard.
     */
    private View createInputClient() {
        FrameLayout root = new FrameLayout(this);
        InputClientView client = new InputClientView(this);
        client.setFocusable(true);
        client.setFocusableInTouchMode(true);
        root.addView(client, new FrameLayout.LayoutParams(1, 1));
        client.requestFocus();

        return root;
    }

    private void showPicker() {
        InputMethodManager imm = getSystemService(InputMethodManager.class);
        if (imm != null) {
            try {
                imm.showInputMethodPicker();
            } catch (RuntimeException exception) {
                Log.w(TAG, "Unable to show the input method picker", exception);
            }
        }
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
