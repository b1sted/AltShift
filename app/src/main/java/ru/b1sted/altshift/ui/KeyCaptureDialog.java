package ru.b1sted.altshift.ui;

import android.content.Context;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.widget.Button;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Objects;

import ru.b1sted.altshift.R;
import ru.b1sted.altshift.databinding.DialogKeyCaptureBinding;
import ru.b1sted.altshift.model.LanguageSwitchShortcut;

public final class KeyCaptureDialog {

    public interface Listener {
        void onCaptured(
                int keyCode,
                boolean ctrl,
                boolean alt,
                boolean shift,
                boolean meta);
    }

    private KeyCaptureDialog() {
    }

    public static void show(
            Context context,
            @Nullable LanguageSwitchShortcut current,
            Listener listener) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(listener, "listener");

        DialogKeyCaptureBinding binding =
                DialogKeyCaptureBinding.inflate(LayoutInflater.from(context));
        KeyCaptureState state = new KeyCaptureState();

        if (current != null) {
            state.keyCode = current.keyCode();
            state.ctrl = current.isCtrl();
            state.alt = current.isAlt();
            state.shift = current.isShift();
            state.meta = current.isMeta();
            binding.capturedText.setText(KeyLabelFormatter.format(current));
        } else {
            binding.capturedText.setText(R.string.capture_key_none);
        }

        AlertDialog dialog = new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.capture_key_title)
                .setView(binding.getRoot())
                .setPositiveButton(R.string.save, null)
                .setNegativeButton(android.R.string.cancel, null)
                .create();

        dialog.setOnShowListener(ignored -> {
            Button positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            positive.setEnabled(state.isValid());
            positive.setOnClickListener(view -> {
                if (!state.isValid()) {
                    return;
                }

                listener.onCaptured(
                        state.keyCode,
                        state.ctrl,
                        state.alt,
                        state.shift,
                        state.meta);
                dialog.dismiss();
            });
        });

        dialog.setOnKeyListener((ignored, keyCode, event) -> {
            if (event == null) {
                return false;
            }

            if (event.getAction() != KeyEvent.ACTION_DOWN) {
                return true;
            }

            if (event.getRepeatCount() > 0) {
                return true;
            }

            if (keyCode == KeyEvent.KEYCODE_BACK) {
                return false;
            }

            if (keyCode == KeyEvent.KEYCODE_UNKNOWN) {
                return true;
            }

            state.update(keyCode, event);
            binding.capturedText.setText(KeyLabelFormatter.format(
                    state.keyCode,
                    state.ctrl,
                    state.alt,
                    state.shift,
                    state.meta));

            Button positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            if (positive != null) {
                positive.setEnabled(true);
            }

            return true;
        });

        dialog.show();
    }

    private static final class KeyCaptureState {

        int keyCode = 0;
        boolean ctrl;
        boolean alt;
        boolean shift;
        boolean meta;

        boolean isValid() {
            return keyCode > 0;
        }

        void update(int keyCode, KeyEvent event) {
            this.keyCode = keyCode;
            ctrl = event.isCtrlPressed();
            alt = event.isAltPressed();
            shift = event.isShiftPressed();
            meta = event.isMetaPressed();
        }
    }
}
