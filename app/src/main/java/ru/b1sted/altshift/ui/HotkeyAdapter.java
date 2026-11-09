package ru.b1sted.altshift.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import ru.b1sted.altshift.R;
import ru.b1sted.altshift.databinding.ItemHotkeyBinding;
import ru.b1sted.altshift.model.HotkeyBinding;

public final class HotkeyAdapter
        extends RecyclerView.Adapter<HotkeyAdapter.HotkeyViewHolder> {

    public interface OnDeleteListener {
        void onDelete(HotkeyBinding binding);
    }

    private final Context context;
    private final PackageManager packageManager;
    private final OnDeleteListener deleteListener;
    private final List<HotkeyBinding> items = new ArrayList<>();

    public HotkeyAdapter(final Context context, final OnDeleteListener deleteListener) {
        this.context = context.getApplicationContext();
        packageManager = context.getPackageManager();
        this.deleteListener = deleteListener;
    }

    @SuppressLint("NotifyDataSetChanged")
    public void submit(final HotkeyBinding[] bindings) {
        items.clear();
        for (int index = 0; index < bindings.length; index++) {
            items.add(bindings[index]);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public HotkeyViewHolder onCreateViewHolder(
            @NonNull final ViewGroup parent, final int viewType) {
        return new HotkeyViewHolder(ItemHotkeyBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(
            @NonNull final HotkeyViewHolder holder, final int position) {
        final HotkeyBinding binding = items.get(position);
        holder.binding.appName.setText(binding.appName());
        holder.binding.packageName.setText(binding.packageName());
        holder.binding.keyBadge.setText(KeyLabelFormatter.format(binding));
        holder.binding.deleteButton.setContentDescription(
                context.getString(R.string.delete_hotkey_for, binding.appName()));
        holder.binding.deleteButton.setOnClickListener(
                view -> deleteListener.onDelete(binding));
        try {
            final Drawable icon =
                    packageManager.getApplicationIcon(binding.packageName());
            holder.binding.appIcon.setImageDrawable(icon);
        } catch (final PackageManager.NameNotFoundException | RuntimeException exception) {
            holder.binding.appIcon.setImageResource(android.R.drawable.sym_def_app_icon);
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static final class HotkeyViewHolder extends RecyclerView.ViewHolder {

        private final ItemHotkeyBinding binding;

        private HotkeyViewHolder(final ItemHotkeyBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
