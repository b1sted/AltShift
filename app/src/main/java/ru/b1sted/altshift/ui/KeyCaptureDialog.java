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
            final Context context,
            @Nullable final LanguageSwitchShortcut current,
            final Listener listener) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(listener, "listener");

        final DialogKeyCaptureBinding binding =
                DialogKeyCaptureBinding.inflate(LayoutInflater.from(context));
        final int[] capturedKeyCode = {0};
        final boolean[] capturedCtrl = {false};
        final boolean[] capturedAlt = {false};
        final boolean[] capturedShift = {false};
        final boolean[] capturedMeta = {false};

        if (current != null) {
            capturedKeyCode[0] = current.keyCode();
            capturedCtrl[0] = current.isCtrl();
            capturedAlt[0] = current.isAlt();
            capturedShift[0] = current.isShift();
            capturedMeta[0] = current.isMeta();
            binding.capturedText.setText(KeyLabelFormatter.format(current));
        } else {
            binding.capturedText.setText(R.string.capture_key_none);
        }

        final AlertDialog dialog = new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.capture_key_title)
                .setView(binding.getRoot())
                .setPositiveButton(R.string.save, null)
                .setNegativeButton(android.R.string.cancel, null)
                .create();

        dialog.setOnShowListener(ignored -> {
            final Button positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            positive.setEnabled(capturedKeyCode[0] > 0);
            positive.setOnClickListener(view -> {
                if (capturedKeyCode[0] <= 0) {
                    return;
                }
                listener.onCaptured(
                        capturedKeyCode[0],
                        capturedCtrl[0],
                        capturedAlt[0],
                        capturedShift[0],
                        capturedMeta[0]);
                dialog.dismiss();
            });
        });

        dialog.setOnKeyListener((ignored, keyCode, event) -> {
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
            capturedKeyCode[0] = keyCode;
            capturedCtrl[0] = event.isCtrlPressed();
            capturedAlt[0] = event.isAltPressed();
            capturedShift[0] = event.isShiftPressed();
            capturedMeta[0] = event.isMetaPressed();
            binding.capturedText.setText(KeyLabelFormatter.format(
                    keyCode,
                    capturedCtrl[0],
                    capturedAlt[0],
                    capturedShift[0],
                    capturedMeta[0]));
            final Button positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            if (positive != null) {
                positive.setEnabled(true);
            }
            return true;
        });

        dialog.show();
    }
}
