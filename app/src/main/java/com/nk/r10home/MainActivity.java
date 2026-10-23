package com.nk.r10home;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class MainActivity extends Activity {
    private static final String PREFS = "r10_home";
    private static final String PREF_FAVORITES = "favorites_csv";

    private static final String[] DEFAULT_FAVORITES = {
            "com.netflix.ninja",
            "com.google.android.youtube.tv",
            "com.amazon.amazonvideo.livingroom",
            "in.startv.hotstar",
            "com.spotify.tv.android",
            "com.plexapp.android",
            "org.xbmc.kodi",
            "org.videolan.vlc"
    };

    private final Handler clockHandler = new Handler(Looper.getMainLooper());
    private final List<AppEntry> apps = new ArrayList<>();

    private LinearLayout favoritesRow;
    private GridLayout appsGrid;
    private TextView clockView;
    private TextView dateView;
    private TextView networkView;
    private ScrollView pageScroll;
    private View firstAppCard;

    private final Runnable clockTick = new Runnable() {
        @Override public void run() {
            updateClock();
            long now = System.currentTimeMillis();
            clockHandler.postDelayed(this, 60_000L - (now % 60_000L));
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        configureWindow();
        buildUi();
        reloadApps();
    }

    @Override protected void onResume() {
        super.onResume();
        enterImmersive();
        updateNetwork();
        clockHandler.removeCallbacks(clockTick);
        clockHandler.post(clockTick);
    }

    @Override protected void onPause() {
        super.onPause();
        clockHandler.removeCallbacks(clockTick);
    }

    private void configureWindow() {
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        enterImmersive();
    }

    private void enterImmersive() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundResource(R.drawable.bg_launcher);

        pageScroll = new ScrollView(this);
        pageScroll.setFillViewport(true);
        pageScroll.setClipToPadding(false);
        pageScroll.setPadding(dp(34), dp(20), dp(34), dp(30));
        pageScroll.setVerticalScrollBarEnabled(false);

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setLayoutParams(new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        page.addView(buildHeader());
        page.addView(spacer(dp(16)));
        page.addView(sectionTitle("Favorites", "Long-press OK on an app to add or remove it"));

        HorizontalScrollView favoritesScroll = new HorizontalScrollView(this);
        favoritesScroll.setHorizontalScrollBarEnabled(false);
        favoritesScroll.setClipToPadding(false);
        favoritesRow = new LinearLayout(this);
        favoritesRow.setOrientation(LinearLayout.HORIZONTAL);
        favoritesRow.setPadding(dp(2), dp(5), dp(10), dp(8));
        favoritesScroll.addView(favoritesRow, new HorizontalScrollView.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        page.addView(favoritesScroll);

        page.addView(spacer(dp(12)));
        page.addView(sectionTitle("Quick actions", null));
        page.addView(buildQuickActions());

        page.addView(spacer(dp(12)));
        page.addView(sectionTitle("All apps", "D-pad optimized • no feeds • no ads"));

        appsGrid = new GridLayout(this);
        appsGrid.setColumnCount(5);
        appsGrid.setAlignmentMode(GridLayout.ALIGN_BOUNDS);
        appsGrid.setUseDefaultMargins(false);
        appsGrid.setPadding(0, dp(5), 0, dp(12));
        page.addView(appsGrid, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView footer = new TextView(this);
        footer.setText("R10 Home 1.0 • no recommendation feeds • no persistent background services");
        footer.setTextColor(Color.rgb(130, 137, 148));
        footer.setTextSize(11);
        footer.setPadding(0, dp(16), 0, dp(10));
        page.addView(footer);

        pageScroll.addView(page);
        root.addView(pageScroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);
    }

    private View buildHeader() {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        TextView title = new TextView(this);
        title.setText("R10 Home");
        title.setTextColor(Color.WHITE);
        title.setTextSize(26);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        left.addView(title);

        networkView = new TextView(this);
        networkView.setTextColor(Color.rgb(170, 177, 188));
        networkView.setTextSize(12);
        networkView.setPadding(0, dp(3), 0, 0);
        left.addView(networkView);

        header.addView(left, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        LinearLayout timeBox = new LinearLayout(this);
        timeBox.setOrientation(LinearLayout.VERTICAL);
        timeBox.setGravity(Gravity.END);

        clockView = new TextView(this);
        clockView.setTextColor(Color.WHITE);
        clockView.setTextSize(25);
        clockView.setGravity(Gravity.END);
        timeBox.addView(clockView);

        dateView = new TextView(this);
        dateView.setTextColor(Color.rgb(170, 177, 188));
        dateView.setTextSize(12);
        dateView.setGravity(Gravity.END);
        timeBox.addView(dateView);

        header.addView(timeBox);
        updateClock();
        updateNetwork();
        return header;
    }

    private View sectionTitle(String titleText, String hintText) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.BOTTOM);
        row.setPadding(0, 0, 0, dp(5));

        TextView title = new TextView(this);
        title.setText(titleText);
        title.setTextColor(Color.WHITE);
        title.setTextSize(17);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        row.addView(title, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        if (hintText != null) {
            TextView hint = new TextView(this);
            hint.setText(hintText);
            hint.setTextColor(Color.rgb(125, 132, 143));
            hint.setTextSize(10);
            row.addView(hint);
        }
        return row;
    }

    private View buildQuickActions() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(3), 0, dp(6));

        row.addView(actionCard("Settings", "System", this::openSettings));
        row.addView(actionCard("TV / Inputs", "Realtek TV", this::openTv));
        row.addView(actionCard("All apps", "Jump to grid", () -> {
            if (firstAppCard != null) {
                firstAppCard.requestFocus();
                pageScroll.smoothScrollTo(0, Math.max(0, firstAppCard.getTop() - dp(80)));
            }
        }));
        row.addView(actionCard("Refresh", "Reload apps", this::reloadApps));
        return row;
    }

    private View actionCard(String title, String subtitle, Runnable action) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setFocusable(true);
        card.setClickable(true);
        card.setPadding(dp(15), dp(10), dp(12), dp(10));
        setCardBackground(card, false);
        addFocusEffect(card);
        card.setOnClickListener(v -> action.run());

        TextView t = new TextView(this);
        t.setText(title);
        t.setTextColor(Color.WHITE);
        t.setTextSize(15);
        t.setTypeface(null, android.graphics.Typeface.BOLD);
        card.addView(t);

        TextView s = new TextView(this);
        s.setText(subtitle);
        s.setTextColor(Color.rgb(160, 167, 178));
        s.setTextSize(10);
        s.setPadding(0, dp(2), 0, 0);
        card.addView(s);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(68), 1f);
        lp.setMargins(0, 0, dp(9), 0);
        card.setLayoutParams(lp);
        return card;
    }

    private void reloadApps() {
        apps.clear();
        PackageManager pm = getPackageManager();
        Set<String> seen = new HashSet<>();

        Intent leanback = new Intent(Intent.ACTION_MAIN);
        leanback.addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        collectApps(pm, leanback, seen);

        Intent normal = new Intent(Intent.ACTION_MAIN);
        normal.addCategory(Intent.CATEGORY_LAUNCHER);
        collectApps(pm, normal, seen);

        Collections.sort(apps, Comparator.comparing(a -> a.label.toLowerCase(Locale.getDefault())));
        ensureDefaultFavorites();
        renderFavorites();
        renderAllApps();
    }

    private void collectApps(PackageManager pm, Intent query, Set<String> seen) {
        List<ResolveInfo> results = pm.queryIntentActivities(query, PackageManager.MATCH_ALL);
        for (ResolveInfo ri : results) {
            if (ri.activityInfo == null) continue;
            String pkg = ri.activityInfo.packageName;
            if (getPackageName().equals(pkg) || seen.contains(pkg)) continue;
            if (!ri.activityInfo.enabled || !ri.activityInfo.applicationInfo.enabled) continue;

            CharSequence loaded = ri.loadLabel(pm);
            String label = loaded == null ? pkg : loaded.toString().trim();
            Drawable icon;
            try {
                icon = ri.loadIcon(pm);
            } catch (Exception e) {
                icon = getDrawable(android.R.drawable.sym_def_app_icon);
            }
            ComponentName component = new ComponentName(pkg, ri.activityInfo.name);
            apps.add(new AppEntry(pkg, label, icon, component));
            seen.add(pkg);
        }
    }

    private void ensureDefaultFavorites() {
        LinkedHashSet<String> favs = readFavorites();
        if (!favs.isEmpty()) return;

        Set<String> installed = new HashSet<>();
        for (AppEntry app : apps) installed.add(app.packageName);

        for (String pkg : DEFAULT_FAVORITES) {
            if (installed.contains(pkg)) favs.add(pkg);
            if (favs.size() >= 6) break;
        }
        if (favs.isEmpty()) {
            for (AppEntry app : apps) {
                favs.add(app.packageName);
                if (favs.size() >= 6) break;
            }
        }
        writeFavorites(favs);
    }

    private void renderFavorites() {
        favoritesRow.removeAllViews();
        LinkedHashSet<String> favs = readFavorites();
        int shown = 0;
        for (String pkg : favs) {
            AppEntry app = findApp(pkg);
            if (app == null) continue;
            View card = appCard(app, true);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(132), dp(96));
            lp.setMargins(0, 0, dp(10), 0);
            card.setLayoutParams(lp);
            favoritesRow.addView(card);
            shown++;
        }
        if (shown == 0) {
            TextView empty = new TextView(this);
            empty.setText("Long-press any app below to add it here");
            empty.setTextColor(Color.rgb(155, 162, 173));
            empty.setTextSize(12);
            empty.setPadding(dp(4), dp(20), 0, dp(20));
            favoritesRow.addView(empty);
        }
    }

    private void renderAllApps() {
        appsGrid.removeAllViews();
        firstAppCard = null;
        for (int i = 0; i < apps.size(); i++) {
            AppEntry app = apps.get(i);
            View card = appCard(app, false);
            if (firstAppCard == null) firstAppCard = card;

            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = 0;
            lp.height = dp(106);
            lp.columnSpec = GridLayout.spec(i % 5, 1f);
            lp.setMargins(0, 0, dp(9), dp(9));
            card.setLayoutParams(lp);
            appsGrid.addView(card);
        }
    }

    private View appCard(AppEntry app, boolean compact) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setFocusable(true);
        card.setClickable(true);
        card.setPadding(dp(8), dp(8), dp(8), dp(7));
        setCardBackground(card, false);
        addFocusEffect(card);

        ImageView icon = new ImageView(this);
        icon.setImageDrawable(app.icon);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int iconSize = compact ? dp(43) : dp(48);
        card.addView(icon, new LinearLayout.LayoutParams(iconSize, iconSize));

        TextView label = new TextView(this);
        label.setText(app.label);
        label.setTextColor(Color.WHITE);
        label.setTextSize(compact ? 10 : 11);
        label.setGravity(Gravity.CENTER);
        label.setSingleLine(true);
        label.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        labelLp.topMargin = dp(5);
        card.addView(label, labelLp);

        card.setOnClickListener(v -> launchApp(app));
        card.setOnLongClickListener(v -> {
            toggleFavorite(app);
            return true;
        });
        return card;
    }

    private void launchApp(AppEntry app) {
        try {
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
            intent.setComponent(app.component);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Intent fallback = getPackageManager().getLaunchIntentForPackage(app.packageName);
            if (fallback != null) startActivity(fallback);
            else Toast.makeText(this, "Unable to open " + app.label, Toast.LENGTH_SHORT).show();
        }
    }

    private void toggleFavorite(AppEntry app) {
        LinkedHashSet<String> favs = readFavorites();
        boolean removed = favs.remove(app.packageName);
        if (!removed) favs.add(app.packageName);
        writeFavorites(favs);
        renderFavorites();
        Toast.makeText(this, removed ? "Removed from favorites" : "Added to favorites",
                Toast.LENGTH_SHORT).show();
    }

    private LinkedHashSet<String> readFavorites() {
        String csv = getSharedPreferences(PREFS, MODE_PRIVATE).getString(PREF_FAVORITES, "");
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (csv == null || csv.trim().isEmpty()) return out;
        for (String item : csv.split(",")) {
            String pkg = item.trim();
            if (!pkg.isEmpty()) out.add(pkg);
        }
        return out;
    }

    private void writeFavorites(LinkedHashSet<String> favs) {
        StringBuilder sb = new StringBuilder();
        for (String pkg : favs) {
            if (sb.length() > 0) sb.append(',');
            sb.append(pkg);
        }
        getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit().putString(PREF_FAVORITES, sb.toString()).apply();
    }

    private AppEntry findApp(String packageName) {
        for (AppEntry app : apps) {
            if (app.packageName.equals(packageName)) return app;
        }
        return null;
    }

    private void openSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_SETTINGS));
        } catch (ActivityNotFoundException e) {
            Intent launch = getPackageManager().getLaunchIntentForPackage("com.android.tv.settings");
            if (launch != null) startActivity(launch);
        }
    }

    private void openTv() {
        Intent launch = getPackageManager().getLaunchIntentForPackage("com.realtek.tv");
        if (launch != null) startActivity(launch);
        else Toast.makeText(this, "TV input app is not exposed by this firmware", Toast.LENGTH_SHORT).show();
    }

    private void updateClock() {
        Date now = new Date();
        if (clockView != null) clockView.setText(new SimpleDateFormat("h:mm a", Locale.getDefault()).format(now));
        if (dateView != null) dateView.setText(new SimpleDateFormat("EEE, d MMM", Locale.getDefault()).format(now));
    }

    private void updateNetwork() {
        if (networkView == null) return;
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) {
            networkView.setText("Network status unavailable");
            return;
        }
        Network network = cm.getActiveNetwork();
        NetworkCapabilities caps = network == null ? null : cm.getNetworkCapabilities(network);
        if (caps == null) {
            networkView.setText("Offline");
            return;
        }
        String transport = "Connected";
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) transport = "Wi-Fi connected";
        else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) transport = "Ethernet connected";
        networkView.setText(transport);
    }

    private void addFocusEffect(View view) {
        view.setOnFocusChangeListener((v, hasFocus) -> {
            setCardBackground(v, hasFocus);
            v.animate().scaleX(hasFocus ? 1.055f : 1f).scaleY(hasFocus ? 1.055f : 1f)
                    .setDuration(110).start();
            v.setElevation(hasFocus ? dp(10) : dp(1));
        });
    }

    private void setCardBackground(View view, boolean focused) {
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(13));
        bg.setColor(focused ? Color.rgb(45, 50, 58) : Color.rgb(27, 31, 37));
        if (focused) bg.setStroke(dp(2), Color.WHITE);
        else bg.setStroke(dp(1), Color.rgb(54, 60, 69));
        view.setBackground(bg);
    }

    private View spacer(int height) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, height));
        return v;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_MENU && event.getAction() == KeyEvent.ACTION_UP) {
            reloadApps();
            Toast.makeText(this, "Apps refreshed", Toast.LENGTH_SHORT).show();
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    private static final class AppEntry {
        final String packageName;
        final String label;
        final Drawable icon;
        final ComponentName component;

        AppEntry(String packageName, String label, Drawable icon, ComponentName component) {
            this.packageName = packageName;
            this.label = label;
            this.icon = icon;
            this.component = component;
        }
    }
}
