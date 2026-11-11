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
import android.view.ViewTreeObserver;
import android.view.Window;

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

    private static final long LIST_FADE_DURATION_MS = 150L;

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

        PickerSession session = new PickerSession(
                context.getApplicationContext(), dialog, binding, adapter);
        session.show();
    }

    /**
     * Keeps the dialog alive while an OEM privacy manager resolves package access.
     *
     * <p>The first package query on some ROMs returns an empty list because the
     * system shows its own permission prompt. Once that prompt is dismissed the
     * dialog window regains focus, which is the signal to query again.
     */
    private static final class PickerSession {

        private final Context context;
        private final AlertDialog dialog;
        private final DialogAppPickerBinding binding;
        private final AppListAdapter adapter;
        private final Handler mainHandler = new Handler(Looper.getMainLooper());
        private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "altshift-app-picker");
            thread.setDaemon(true);

            return thread;
        });
        private final ViewTreeObserver.OnWindowFocusChangeListener focusChangeListener =
                this::onWindowFocusChanged;

        private ViewTreeObserver windowObserver;
        private boolean loading;
        private boolean appsAvailable;
        private boolean released;

        private PickerSession(
                Context context,
                AlertDialog dialog,
                DialogAppPickerBinding binding,
                AppListAdapter adapter) {
            this.context = context;
            this.dialog = dialog;
            this.binding = binding;
            this.adapter = adapter;
        }

        private void show() {
            binding.appList.setLayoutManager(new LinearLayoutManager(context));
            binding.appList.setAdapter(adapter);
            binding.retryButton.setOnClickListener(view -> loadApps());

            dialog.setOnShowListener(ignored -> {
                attachFocusListener();
                loadApps();
            });
            dialog.setOnDismissListener(ignored -> release());

            showLoadingState();
            dialog.show();
        }

        private void attachFocusListener() {
            Window window = dialog.getWindow();
            if (window == null) {
                return;
            }

            View decorView = window.getDecorView();
            windowObserver = decorView.getViewTreeObserver();
            windowObserver.addOnWindowFocusChangeListener(focusChangeListener);
        }

        private void onWindowFocusChanged(boolean hasFocus) {
            if (!hasFocus || released || loading || appsAvailable) {
                return;
            }

            if (binding.emptyState.getVisibility() != View.VISIBLE) {
                return;
            }

            Log.i(TAG, "App picker regained focus with an empty list; retrying package query");
            loadApps();
        }

        private void loadApps() {
            if (released || loading) {
                return;
            }

            loading = true;
            showLoadingState();

            executor.execute(() -> {
                List<AppEntry> apps;
                try {
                    apps = loadLaunchableApps(context);
                } catch (RuntimeException exception) {
                    Log.e(TAG, "Unable to query launchable applications", exception);
                    mainHandler.post(() -> onLoadFailed(R.string.app_picker_failed));

                    return;
                }

                mainHandler.post(() -> onAppsLoaded(apps));
            });
        }

        private void onAppsLoaded(List<AppEntry> apps) {
            loading = false;
            if (released) {
                return;
            }

            if (apps.isEmpty()) {
                showEmptyState(R.string.no_launchable_apps);

                return;
            }

            appsAvailable = true;
            adapter.submit(apps);
            showApps();
        }

        private void onLoadFailed(int messageResId) {
            loading = false;
            if (released) {
                return;
            }

            showEmptyState(messageResId);
        }

        private void showLoadingState() {
            binding.progress.setVisibility(View.VISIBLE);
            binding.emptyState.setVisibility(View.GONE);

            binding.appList.animate().cancel();
            binding.appList.setAlpha(0f);
            binding.appList.setVisibility(View.GONE);
        }

        private void showEmptyState(int messageResId) {
            binding.progress.setVisibility(View.GONE);
            binding.appList.animate().cancel();
            binding.appList.setVisibility(View.GONE);

            binding.emptyView.setText(messageResId);
            binding.emptyState.setVisibility(View.VISIBLE);
        }

        private void showApps() {
            binding.progress.setVisibility(View.GONE);
            binding.emptyState.setVisibility(View.GONE);

            binding.appList.animate().cancel();
            binding.appList.setAlpha(0f);
            binding.appList.setVisibility(View.VISIBLE);
            binding.appList.animate()
                    .alpha(1f)
                    .setDuration(LIST_FADE_DURATION_MS)
                    .start();
        }

        private void release() {
            released = true;
            executor.shutdownNow();
            mainHandler.removeCallbacksAndMessages(null);

            if (windowObserver != null) {
                if (windowObserver.isAlive()) {
                    windowObserver.removeOnWindowFocusChangeListener(focusChangeListener);
                }

                windowObserver = null;
            }

            binding.retryButton.setOnClickListener(null);
            binding.appList.setAdapter(null);
        }
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
