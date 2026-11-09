package ru.b1sted.altshift.ui;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import ru.b1sted.altshift.R;

public final class AppPickerDialog {

    private static final String TAG = "AltShift";

    public interface Listener {
        void onSelected(String appName, String packageName);
    }

    private AppPickerDialog() {
    }

    public static void show(final Context context, final Listener listener) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(listener, "listener");

        final List<AppEntry> apps;
        try {
            apps = loadLaunchableApps(context);
        } catch (final RuntimeException exception) {
            Log.e(TAG, "Unable to query launchable applications", exception);
            new MaterialAlertDialogBuilder(context)
                    .setTitle(R.string.select_app_title)
                    .setMessage(R.string.app_picker_failed)
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
            return;
        }

        if (apps.isEmpty()) {
            new MaterialAlertDialogBuilder(context)
                    .setTitle(R.string.select_app_title)
                    .setMessage(R.string.no_launchable_apps)
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
            return;
        }

        final AppListAdapter adapter = new AppListAdapter(context, apps);
        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.select_app_title)
                .setAdapter(adapter, (dialog, which) -> {
                    final AppEntry entry = apps.get(which);
                    listener.onSelected(entry.label(), entry.packageName());
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private static List<AppEntry> loadLaunchableApps(final Context context) {
        final PackageManager packageManager = context.getPackageManager();
        final Intent launcherIntent = new Intent(Intent.ACTION_MAIN);
        launcherIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        final List<ResolveInfo> resolved =
                packageManager.queryIntentActivities(launcherIntent, 0);
        final List<AppEntry> apps = new ArrayList<>(resolved.size());
        final Set<String> seen = new HashSet<>();
        final String ownPackage = context.getPackageName();

        for (int index = 0; index < resolved.size(); index++) {
            final ResolveInfo info = resolved.get(index);
            if (info.activityInfo == null) {
                continue;
            }
            final String packageName = info.activityInfo.packageName;
            if (packageName == null
                    || packageName.equals(ownPackage)
                    || !seen.add(packageName)) {
                continue;
            }
            final ApplicationInfo applicationInfo = info.activityInfo.applicationInfo;
            CharSequence label = applicationInfo == null
                    ? packageName
                    : packageManager.getApplicationLabel(applicationInfo);
            if (label == null || label.length() == 0) {
                label = packageName;
            }
            Drawable icon = null;
            try {
                icon = packageManager.getApplicationIcon(packageName);
            } catch (final PackageManager.NameNotFoundException | RuntimeException exception) {
                Log.w(TAG, "Unable to load icon for " + packageName, exception);
            }
            apps.add(new AppEntry(label.toString(), packageName, icon));
        }

        apps.sort((left, right) -> left.label().compareToIgnoreCase(right.label()));
        return apps;
    }

    private record AppEntry(String label, String packageName, Drawable icon) {
        private AppEntry {
            label = Objects.requireNonNull(label, "label");
            packageName = Objects.requireNonNull(packageName, "packageName");
        }
    }

    private static final class AppListAdapter extends BaseAdapter {

        private final LayoutInflater inflater;
        private final List<AppEntry> items;

        private AppListAdapter(final Context context, final List<AppEntry> items) {
            inflater = LayoutInflater.from(context);
            this.items = items;
        }

        @Override
        public int getCount() {
            return items.size();
        }

        @Override
        public AppEntry getItem(final int position) {
            return items.get(position);
        }

        @Override
        public long getItemId(final int position) {
            return position;
        }

        @Override
        public View getView(
                final int position,
                final View convertView,
                final ViewGroup parent) {
            final View view;
            final ViewHolder holder;
            if (convertView == null) {
                view = inflater.inflate(R.layout.item_app_picker, parent, false);
                holder = new ViewHolder(view);
                view.setTag(holder);
            } else {
                view = convertView;
                holder = (ViewHolder) view.getTag();
            }
            final AppEntry entry = getItem(position);
            holder.label.setText(entry.label());
            holder.packageName.setText(entry.packageName());
            if (entry.icon() == null) {
                holder.icon.setImageResource(android.R.drawable.sym_def_app_icon);
            } else {
                holder.icon.setImageDrawable(entry.icon());
            }
            return view;
        }
    }

    private static final class ViewHolder {

        private final ImageView icon;
        private final TextView label;
        private final TextView packageName;

        private ViewHolder(final View view) {
            icon = view.findViewById(R.id.appIcon);
            label = view.findViewById(R.id.appLabel);
            packageName = view.findViewById(R.id.appPackage);
        }
    }
}
