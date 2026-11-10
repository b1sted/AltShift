package ru.b1sted.altshift.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.LruCache;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import ru.b1sted.altshift.R;
import ru.b1sted.altshift.databinding.ItemHotkeyBinding;
import ru.b1sted.altshift.model.HotkeyBinding;

public final class HotkeyAdapter
        extends RecyclerView.Adapter<HotkeyAdapter.HotkeyViewHolder> {

    private static final String TAG = "AltShift";

    public interface OnDeleteListener {
        void onDelete(HotkeyBinding binding);
    }

    private final Context context;
    private final PackageManager packageManager;
    private final OnDeleteListener deleteListener;
    private final List<HotkeyBinding> items = new ArrayList<>();
    private final LruCache<String, Drawable> iconCache = new LruCache<>(32);
    private final Set<String> pendingIcons = new HashSet<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService iconExecutor = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "altshift-icon-loader");
        thread.setDaemon(true);

        return thread;
    });

    private boolean shutDown;

    public HotkeyAdapter(Context context, OnDeleteListener deleteListener) {
        this.context = context.getApplicationContext();
        packageManager = context.getPackageManager();
        this.deleteListener = deleteListener;
    }

    @SuppressLint("NotifyDataSetChanged")
    public void submit(HotkeyBinding[] bindings) {
        items.clear();

        for (int index = 0; index < bindings.length; index++) {
            items.add(bindings[index]);
        }

        notifyDataSetChanged();
    }

    public void shutdown() {
        shutDown = true;
        iconExecutor.shutdownNow();
        mainHandler.removeCallbacksAndMessages(null);
    }

    @NonNull
    @Override
    public HotkeyViewHolder onCreateViewHolder(
            @NonNull ViewGroup parent, int viewType) {
        ItemHotkeyBinding binding = ItemHotkeyBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);

        return new HotkeyViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(
            @NonNull HotkeyViewHolder holder, int position) {
        HotkeyBinding binding = items.get(position);

        holder.binding.appName.setText(binding.appName());
        holder.binding.packageName.setText(binding.packageName());
        holder.binding.keyBadge.setText(KeyLabelFormatter.format(binding));
        holder.binding.deleteButton.setContentDescription(
                context.getString(R.string.delete_hotkey_for, binding.appName()));
        holder.binding.deleteButton.setOnClickListener(
                view -> deleteListener.onDelete(binding));

        bindIcon(holder, binding);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    private void bindIcon(HotkeyViewHolder holder, HotkeyBinding binding) {
        Drawable cached = iconCache.get(binding.packageName());
        if (cached != null) {
            holder.binding.appIcon.setImageDrawable(cached);

            return;
        }

        holder.binding.appIcon.setImageResource(android.R.drawable.sym_def_app_icon);
        requestIcon(holder, binding.packageName());
    }

    private void requestIcon(HotkeyViewHolder holder, String packageName) {
        if (!pendingIcons.add(packageName)) {
            return;
        }

        WeakReference<HotkeyViewHolder> holderRef = new WeakReference<>(holder);

        iconExecutor.execute(() -> {
            Drawable icon = loadIcon(packageName);

            mainHandler.post(() -> {
                if (shutDown) {
                    return;
                }

                pendingIcons.remove(packageName);

                if (icon == null) {
                    return;
                }

                iconCache.put(packageName, icon);

                HotkeyViewHolder currentHolder = holderRef.get();
                if (currentHolder != null) {
                    applyIconIfBound(currentHolder, packageName, icon);
                }
            });
        });
    }

    private Drawable loadIcon(String packageName) {
        try {
            return packageManager.getApplicationIcon(packageName);
        } catch (PackageManager.NameNotFoundException | RuntimeException exception) {
            Log.w(TAG, "Unable to load icon for " + packageName, exception);

            return null;
        }
    }

    private void applyIconIfBound(
            HotkeyViewHolder holder, String packageName, Drawable icon) {
        int position = holder.getBindingAdapterPosition();
        if (position == RecyclerView.NO_POSITION || position >= items.size()) {
            return;
        }

        HotkeyBinding current = items.get(position);
        if (!packageName.equals(current.packageName())) {
            return;
        }

        holder.binding.appIcon.setImageDrawable(icon);
    }

    static final class HotkeyViewHolder extends RecyclerView.ViewHolder {

        private final ItemHotkeyBinding binding;

        private HotkeyViewHolder(ItemHotkeyBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
