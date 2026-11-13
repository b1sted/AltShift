package ru.b1sted.altshift.ui;

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
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

import ru.b1sted.altshift.R;
import ru.b1sted.altshift.databinding.ItemHotkeyBinding;
import ru.b1sted.altshift.model.HotkeyBinding;

public final class HotkeyAdapter
        extends ListAdapter<HotkeyBinding, HotkeyAdapter.HotkeyViewHolder> {

    private static final String TAG = "AltShift";
    private static final int ICON_CACHE_SIZE = 32;

    private static final DiffUtil.ItemCallback<HotkeyBinding> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<>() {
                @Override
                public boolean areItemsTheSame(
                        @NonNull HotkeyBinding oldItem, @NonNull HotkeyBinding newItem) {
                    return oldItem.matches(
                            newItem.keyCode(),
                            newItem.isCtrl(),
                            newItem.isAlt(),
                            newItem.isShift(),
                            newItem.isMeta());
                }

                @Override
                public boolean areContentsTheSame(
                        @NonNull HotkeyBinding oldItem, @NonNull HotkeyBinding newItem) {
                    return oldItem.equals(newItem);
                }
            };

    public interface OnDeleteListener {
        void onDelete(HotkeyBinding binding);
    }

    private final Context context;
    private final PackageManager packageManager;
    private final OnDeleteListener deleteListener;
    private final LruCache<String, Drawable> iconCache =
            new LruCache<>(ICON_CACHE_SIZE);
    private final Set<String> pendingIcons = new HashSet<>();
    private final Map<String, List<WeakReference<HotkeyViewHolder>>> pendingHolders =
            new HashMap<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService iconExecutor = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "altshift-icon-loader");
        thread.setDaemon(true);

        return thread;
    });

    private volatile boolean shutDown;

    public HotkeyAdapter(Context context, OnDeleteListener deleteListener) {
        super(DIFF_CALLBACK);

        this.context = context.getApplicationContext();
        packageManager = context.getPackageManager();
        this.deleteListener = deleteListener;
    }

    public void submit(HotkeyBinding[] bindings) {
        if (shutDown) {
            return;
        }

        submitList(List.of(bindings));
    }

    public void shutdown() {
        shutDown = true;
        iconExecutor.shutdownNow();
        mainHandler.removeCallbacksAndMessages(null);
        pendingIcons.clear();
        pendingHolders.clear();
        iconCache.evictAll();
        submitList(null);
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
        HotkeyBinding binding = getItem(position);

        holder.binding.appName.setText(binding.appName());
        holder.binding.packageName.setText(binding.packageName());
        holder.binding.keyBadge.setText(KeyLabelFormatter.format(binding));
        holder.binding.deleteButton.setContentDescription(
                context.getString(R.string.delete_hotkey_for, binding.appName()));
        holder.binding.deleteButton.setOnClickListener(
                view -> deleteListener.onDelete(binding));

        bindIcon(holder, binding);
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
        if (shutDown) {
            return;
        }

        registerPendingHolder(packageName, holder);

        if (!pendingIcons.add(packageName)) {
            return;
        }

        submitIconLoad(packageName);
    }

    private void registerPendingHolder(String packageName, HotkeyViewHolder holder) {
        List<WeakReference<HotkeyViewHolder>> holders = pendingHolders.get(packageName);
        if (holders == null) {
            holders = new ArrayList<>(2);
            pendingHolders.put(packageName, holders);
        }

        holders.add(new WeakReference<>(holder));
    }

    private void submitIconLoad(String packageName) {
        try {
            iconExecutor.execute(() -> loadAndPostIcon(packageName));
        } catch (RejectedExecutionException exception) {
            pendingIcons.remove(packageName);
            pendingHolders.remove(packageName);
            Log.w(TAG, "Unable to queue icon loading", exception);
        }
    }

    private void loadAndPostIcon(String packageName) {
        Drawable icon = loadIcon(packageName);
        mainHandler.post(() -> deliverIconResult(packageName, icon));
    }

    private void deliverIconResult(String packageName, Drawable icon) {
        if (shutDown) {
            return;
        }

        pendingIcons.remove(packageName);
        List<WeakReference<HotkeyViewHolder>> waiting =
                pendingHolders.remove(packageName);

        if (icon == null) {
            return;
        }

        iconCache.put(packageName, icon);
        deliverIconToHolders(waiting, packageName, icon);
    }

    private void deliverIconToHolders(
            List<WeakReference<HotkeyViewHolder>> holders,
            String packageName,
            Drawable icon) {
        if (holders == null) {
            return;
        }

        for (int index = 0; index < holders.size(); index++) {
            HotkeyViewHolder holder = holders.get(index).get();
            if (holder != null) {
                applyIconIfBound(holder, packageName, icon);
            }
        }
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
        if (position == RecyclerView.NO_POSITION || position >= getItemCount()) {
            return;
        }

        HotkeyBinding current = getItem(position);
        if (current == null || !packageName.equals(current.packageName())) {
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
