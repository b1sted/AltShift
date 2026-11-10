package ru.b1sted.altshift.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import ru.b1sted.altshift.R;
import ru.b1sted.altshift.databinding.DialogAppPickerBinding;
import ru.b1sted.altshift.databinding.ItemAppPickerBinding;

public final class AppPickerDialog {

    private static final String TAG = "AltShift";

    public interface Listener {
        void onSelected(String appName, String packageName);
    }

    private interface AppClickListener {
        void onAppClicked(AppEntry entry);
    }

    private AppPickerDialog() {
    }

    public static void show(Context context, Listener listener) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(listener, "listener");

        DialogAppPickerBinding binding =
                DialogAppPickerBinding.inflate(LayoutInflater.from(context));
        AlertDialog dialog = new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.select_app_title)
                .setView(binding.getRoot())
                .setNegativeButton(android.R.string.cancel, null)
                .create();

        AppListAdapter adapter = new AppListAdapter(entry -> {
            listener.onSelected(entry.label(), entry.packageName());
            dialog.dismiss();
        });

        binding.appList.setLayoutManager(new LinearLayoutManager(context));
        binding.appList.setAdapter(adapter);
        binding.progress.setVisibility(View.VISIBLE);
        binding.emptyView.setVisibility(View.GONE);

        dialog.show();

        loadAppsAsync(context.getApplicationContext(), dialog, binding, adapter);
    }

    private static void loadAppsAsync(
            Context context,
            AlertDialog dialog,
            DialogAppPickerBinding binding,
            AppListAdapter adapter) {
        Handler mainHandler = new Handler(Looper.getMainLooper());
        ExecutorService executor = Executors.newSingleThreadExecutor();

        executor.execute(() -> {
            try {
                List<AppEntry> apps = loadLaunchableApps(context);

                mainHandler.post(() -> publishApps(dialog, binding, adapter, apps));
            } catch (RuntimeException exception) {
                Log.e(TAG, "Unable to query launchable applications", exception);

                mainHandler.post(() -> showEmptyState(
                        dialog, binding, R.string.app_picker_failed));
            } finally {
                executor.shutdown();
            }
        });
    }

    private static void publishApps(
            AlertDialog dialog,
            DialogAppPickerBinding binding,
            AppListAdapter adapter,
            List<AppEntry> apps) {
        if (!dialog.isShowing()) {
            return;
        }

        binding.progress.setVisibility(View.GONE);

        if (apps.isEmpty()) {
            showEmptyState(dialog, binding, R.string.no_launchable_apps);

            return;
        }

        adapter.submit(apps);
        binding.appList.setVisibility(View.VISIBLE);
    }

    private static void showEmptyState(
            AlertDialog dialog, DialogAppPickerBinding binding, int messageResId) {
        if (!dialog.isShowing()) {
            return;
        }

        binding.progress.setVisibility(View.GONE);
        binding.appList.setVisibility(View.GONE);
        binding.emptyView.setText(messageResId);
        binding.emptyView.setVisibility(View.VISIBLE);
    }

    private static List<AppEntry> loadLaunchableApps(Context context) {
        PackageManager packageManager = context.getPackageManager();
        Intent launcherIntent = new Intent(Intent.ACTION_MAIN);
        launcherIntent.addCategory(Intent.CATEGORY_LAUNCHER);

        List<ResolveInfo> resolved =
                packageManager.queryIntentActivities(launcherIntent, 0);
        List<AppEntry> apps = new ArrayList<>(resolved.size());
        Set<String> seen = new HashSet<>();
        String ownPackage = context.getPackageName();

        for (int index = 0; index < resolved.size(); index++) {
            ResolveInfo info = resolved.get(index);
            if (info.activityInfo == null) {
                continue;
            }

            String packageName = info.activityInfo.packageName;
            if (packageName == null
                    || packageName.equals(ownPackage)
                    || !seen.add(packageName)) {
                continue;
            }

            ApplicationInfo applicationInfo = info.activityInfo.applicationInfo;
            CharSequence label = applicationInfo == null
                    ? packageName
                    : packageManager.getApplicationLabel(applicationInfo);
            if (label == null || label.length() == 0) {
                label = packageName;
            }

            Drawable icon = loadIcon(packageManager, packageName);
            apps.add(new AppEntry(label.toString(), packageName, icon));
        }

        apps.sort((left, right) -> left.label().compareToIgnoreCase(right.label()));

        return apps;
    }

    private static Drawable loadIcon(PackageManager packageManager, String packageName) {
        try {
            return packageManager.getApplicationIcon(packageName);
        } catch (PackageManager.NameNotFoundException | RuntimeException exception) {
            Log.w(TAG, "Unable to load icon for " + packageName, exception);

            return null;
        }
    }

    private record AppEntry(String label, String packageName, Drawable icon) {
        private AppEntry {
            label = Objects.requireNonNull(label, "label");
            packageName = Objects.requireNonNull(packageName, "packageName");
        }
    }

    private static final class AppListAdapter extends RecyclerView.Adapter<AppViewHolder> {

        private final List<AppEntry> items = new ArrayList<>();
        private final AppClickListener clickListener;

        private AppListAdapter(AppClickListener clickListener) {
            this.clickListener = clickListener;
        }

        @SuppressLint("NotifyDataSetChanged")
        void submit(List<AppEntry> apps) {
            items.clear();
            items.addAll(apps);

            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public AppViewHolder onCreateViewHolder(
                @NonNull ViewGroup parent, int viewType) {
            ItemAppPickerBinding binding = ItemAppPickerBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false);

            return new AppViewHolder(binding);
        }

        @Override
        public void onBindViewHolder(
                @NonNull AppViewHolder holder, int position) {
            AppEntry entry = items.get(position);

            holder.binding.appLabel.setText(entry.label());
            holder.binding.appPackage.setText(entry.packageName());
            if (entry.icon() == null) {
                holder.binding.appIcon.setImageResource(android.R.drawable.sym_def_app_icon);
            } else {
                holder.binding.appIcon.setImageDrawable(entry.icon());
            }

            holder.itemView.setOnClickListener(view -> clickListener.onAppClicked(entry));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }
    }

    private static final class AppViewHolder extends RecyclerView.ViewHolder {

        private final ItemAppPickerBinding binding;

        private AppViewHolder(ItemAppPickerBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
