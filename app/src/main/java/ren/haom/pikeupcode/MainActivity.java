package ren.haom.pikeupcode;

import android.Manifest;
import android.appwidget.AppWidgetManager;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.provider.Settings;
import android.provider.Telephony;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.navigation.NavigationBarView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends AppCompatActivity {
    private static final int PENDING = 1, HISTORY = 2, SETTINGS = 3;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private CoordinatorLayout root;
    private FrameLayout content;
    private BottomNavigationView navigation;
    private MaterialToolbar toolbar;
    private ParcelStore store;
    private int tab = PENDING;
    private int loadGeneration;
    private boolean importing;
    private long openId;
    private final BroadcastReceiver updates = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { reload(); }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        store = ParcelStore.get(this);
        tab = state == null ? PENDING : state.getInt("tab", PENDING);
        openId = state == null ? getIntent().getLongExtra("parcel_id", 0) : 0;
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        boolean light = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) != Configuration.UI_MODE_NIGHT_YES;
        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView()).setAppearanceLightStatusBars(light);
        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView()).setAppearanceLightNavigationBars(light);
        root = new CoordinatorLayout(this);
        root.setBackgroundColor(color(R.color.surface));
        LinearLayout shell = column();
        root.addView(shell, new CoordinatorLayout.LayoutParams(-1, -1));
        ViewCompat.setOnApplyWindowInsetsListener(shell, (view, insets) -> {
            androidx.core.graphics.Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            androidx.core.graphics.Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            view.setPadding(bars.left, bars.top, bars.right, Math.max(bars.bottom, ime.bottom));
            return WindowInsetsCompat.CONSUMED;
        });
        toolbar = new MaterialToolbar(this);
        toolbar.setTitle(tab == PENDING ? "待取包裹" : tab == HISTORY ? "已取包裹" : "设置");
        android.view.MenuItem add = toolbar.getMenu().add(0, 4, 0, "粘贴短信").setIcon(R.drawable.ic_add);
        add.setIconTintList(android.content.res.ColorStateList.valueOf(color(R.color.on_surface)));
        add.setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_ALWAYS);
        toolbar.setOnMenuItemClickListener(item -> { pasteSms(); return true; });
        toolbar.setMinimumHeight(dp(64));
        shell.addView(toolbar, new LinearLayout.LayoutParams(-1, -2));
        content = new FrameLayout(this);
        shell.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));
        navigation = new BottomNavigationView(this);
        navigation.getMenu().add(0, PENDING, 0, "待取").setIcon(R.drawable.ic_package);
        navigation.getMenu().add(0, HISTORY, 1, "已取").setIcon(R.drawable.ic_history);
        navigation.getMenu().add(0, SETTINGS, 2, "设置").setIcon(R.drawable.ic_settings);
        navigation.setLabelVisibilityMode(NavigationBarView.LABEL_VISIBILITY_LABELED);
        navigation.setSelectedItemId(tab);
        navigation.setOnItemSelectedListener(item -> { tab = item.getItemId(); reload(); return true; });
        shell.addView(navigation, new LinearLayout.LayoutParams(-1, -2));
        setContentView(root);
    }

    @Override protected void onStart() {
        super.onStart();
        ContextCompat.registerReceiver(this, updates, new IntentFilter(ParcelStore.CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED);
    }
    @Override protected void onResume() { super.onResume(); reload(); }
    @Override protected void onStop() { unregisterReceiver(updates); super.onStop(); }
    @Override protected void onDestroy() { worker.shutdown(); super.onDestroy(); }
    @Override protected void onSaveInstanceState(@NonNull Bundle state) { state.putInt("tab", tab); super.onSaveInstanceState(state); }
    @Override protected void onNewIntent(Intent intent) { super.onNewIntent(intent); setIntent(intent); openId = intent.getLongExtra("parcel_id", 0); reload(); }

    private void reload() {
        if (isDestroyed() || worker.isShutdown()) return;
        int generation = ++loadGeneration;
        int selected = tab;
        toolbar.setTitle(selected == PENDING ? "待取包裹" : selected == HISTORY ? "已取包裹" : "设置");
        worker.execute(() -> {
            try {
                List<Parcel> pending = store.list(false);
                List<Parcel> items = selected == HISTORY ? store.list(true) : pending;
                Parcel detail = openId == 0 ? null : store.find(openId);
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed() || generation != loadGeneration) return;
                    if (pending.isEmpty()) navigation.removeBadge(PENDING);
                    else { navigation.getOrCreateBadge(PENDING).setNumber(pending.size()); navigation.getOrCreateBadge(PENDING).setBackgroundColor(color(R.color.primary)); navigation.getOrCreateBadge(PENDING).setBadgeTextColor(color(R.color.on_primary)); }
                    content.removeAllViews();
                    if (selected == SETTINGS) showSettings(); else showList(items, selected == HISTORY);
                    if (openId != 0) { openId = 0; if (detail != null) showDetail(detail); }
                });
            } catch (RuntimeException e) { runOnUiThread(() -> notice("读取失败，请检查手机可用存储空间后重试")); }
        });
    }

    private void showList(List<Parcel> items, boolean history) {
        LinearLayout page = column(); content.addView(page, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout heading = column(); heading.setPadding(dp(24), dp(12), dp(24), dp(16));
        TextView subtitle = text(history ? "已经带回家的 " + items.size() + " 个包裹" : items.isEmpty() ? "所有取件码，都在这里" : "还有 " + items.size() + " 个包裹等你带回家", 14, false, R.color.on_surface_variant);
        topMargin(heading, subtitle, 8); page.addView(heading);
        if (!history && !allowed(Manifest.permission.RECEIVE_SMS)) {
            MaterialCardView permission = card();
            LinearLayout inside = column(); inside.setPadding(dp(16), dp(12), dp(16), dp(12));
            inside.addView(text("开启短信识别", 18, true, R.color.on_surface));
            topMargin(inside, text("允许接收短信后，新到的取件码会自动加入。", 14, false, R.color.on_surface_variant), 4);
            topMargin(inside, button("授予短信权限", true, v -> enableSms()), 8);
            permission.addView(inside); page.addView(permission, margins(-1, -2, 16, 0, 16, 8));
        }
        if (!history && allowed(Manifest.permission.RECEIVE_SMS) && !ParcelNotifications.enabled(this)) {
            MaterialButton notifications = button("开启取件通知", false, v -> enableNotifications());
            page.addView(notifications, margins(-1, -2, 16, 0, 16, 8));
        }
        if (!history && store.settings().getBoolean("receive_error", false)) {
            TextView error = text("有短信保存失败，请到设置导入近期短信。", 14, false, R.color.on_surface_variant);
            page.addView(error, margins(-1, -2, 24, 4, 24, 8));
        }
        if (items.isEmpty()) {
            LinearLayout empty = column(); empty.setGravity(Gravity.CENTER); empty.setPadding(dp(28), dp(16), dp(28), dp(24));
            ImageView icon = new ImageView(this); icon.setImageResource(history ? R.drawable.ic_history : R.drawable.ic_package); icon.setColorFilter(color(R.color.primary));
            icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            empty.addView(icon, new LinearLayout.LayoutParams(dp(64), dp(64)));
            TextView title = text(history ? "还没有已取包裹" : "没有待取包裹", 22, true, R.color.on_surface);
            title.setGravity(Gravity.CENTER); topMargin(empty, title, 20);
            TextView description = text(history ? "取件后点一下「标记已取」，就会保存在这里。" : "收到带取件码的短信后，会自动整理到这里。也可以粘贴短信，立即识别。", 14, false, R.color.on_surface_variant);
            description.setGravity(Gravity.CENTER); topMargin(empty, description, 12);
            if (!history) topMargin(empty, button("粘贴短信", false, v -> pasteSms()), 20);
            page.addView(empty, new LinearLayout.LayoutParams(-1, 0, 1));
        } else {
            RecyclerView list = new RecyclerView(this); list.setLayoutManager(new LinearLayoutManager(this));
            list.setClipToPadding(false); list.setPadding(dp(16), 0, dp(16), dp(16));
            list.setAdapter(new ParcelAdapter(items)); page.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
        }
    }

    private final class ParcelAdapter extends RecyclerView.Adapter<ParcelHolder> {
        private final List<Parcel> items;
        ParcelAdapter(List<Parcel> items) { this.items = items; }
        @NonNull @Override public ParcelHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            MaterialCardView card = card();
            RecyclerView.LayoutParams params = new RecyclerView.LayoutParams(-1, -2); params.bottomMargin = dp(12); card.setLayoutParams(params);
            return new ParcelHolder(card);
        }
        @Override public void onBindViewHolder(@NonNull ParcelHolder h, int position) { h.bind(items.get(position)); }
        @Override public int getItemCount() { return items.size(); }
    }

    private final class ParcelHolder extends RecyclerView.ViewHolder {
        final TextView place, meta, code, address;
        final MaterialButton detail, action;
        ParcelHolder(MaterialCardView card) {
            super(card);
            LinearLayout box = column(); box.setPadding(dp(20), dp(18), dp(20), dp(12)); card.addView(box);
            place = text("", 17, true, R.color.on_surface); box.addView(place);
            meta = text("", 12, false, R.color.on_surface_variant); topMargin(box, meta, 4);
            code = text("", 34, true, R.color.primary); code.setLetterSpacing(0.04f);
            code.setTextIsSelectable(true); code.setContentDescription("取件码"); topMargin(box, code, 14);
            address = text("", 14, false, R.color.on_surface_variant); topMargin(box, address, 8);
            LinearLayout actions = row(); actions.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
            detail = button("详情", false, null); action = button("标记已取", true, null);
            actions.addView(detail); LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, -2); p.leftMargin = dp(8); actions.addView(action, p);
            topMargin(box, actions, 14);
        }
        void bind(Parcel p) {
            place.setText(p.place());
            meta.setText((p.tracking.isEmpty() ? "" : p.tracking + " · ") + time(p.pickedAt > 0 ? p.pickedAt : p.receivedAt) + (p.pickedAt > 0 ? " 已取" : " 到达"));
            code.setText(p.code); code.setContentDescription("取件码 " + p.code);
            address.setText(p.address); address.setVisibility(p.address.isEmpty() || p.address.equals(p.place()) ? View.GONE : View.VISIBLE);
            detail.setOnClickListener(v -> showDetail(p));
            action.setText(p.pickedAt > 0 ? "恢复待取" : "标记已取");
            action.setOnClickListener(v -> mark(p));
            View.OnLongClickListener remove = v -> { confirmDelete(p); return true; };
            itemView.setOnLongClickListener(remove);
            place.setOnLongClickListener(remove); meta.setOnLongClickListener(remove);
            code.setOnLongClickListener(remove); address.setOnLongClickListener(remove);
        }
    }

    private void mark(Parcel p) {
        boolean picked = p.pickedAt == 0;
        worker.execute(() -> {
            try {
                store.setPicked(p.id, picked);
                if (picked) ParcelNotifications.cancel(this, p.id);
                PickupApplication.changed(this);
                runOnUiThread(() -> {
                    reload();
                    Snackbar.make(root, picked ? "已标记为已取" : "已恢复到待取", Snackbar.LENGTH_LONG).setAnchorView(navigation)
                            .setAction("撤销", v -> worker.execute(() -> {
                                try { store.setPicked(p.id, !picked); PickupApplication.changed(this); }
                                catch (RuntimeException e) { runOnUiThread(() -> notice("恢复失败，请重试")); }
                            })).show();
                });
            } catch (RuntimeException e) { runOnUiThread(() -> notice("保存失败，请重试")); }
        });
    }

    private void confirmDelete(Parcel parcel) {
        new MaterialAlertDialogBuilder(this).setTitle("删除这个包裹？")
                .setMessage(parcel.code + "\n" + parcel.place() + "\n\n只删除应用内的包裹记录，短信原文仍保留在系统收件箱。")
                .setNegativeButton("取消", null).setPositiveButton("删除", (dialog, which) -> worker.execute(() -> {
                    try {
                        Parcel deleted = store.delete(parcel.id);
                        if (deleted == null) return;
                        ParcelNotifications.cancel(this, deleted.id); PickupApplication.changed(this);
                        runOnUiThread(() -> {
                            if (isFinishing() || isDestroyed()) return;
                            reload();
                            Snackbar.make(root, "已删除 " + deleted.code, 5000).setAnchorView(navigation)
                                    .setAction("恢复", v -> worker.execute(() -> {
                                        try { store.restore(deleted); PickupApplication.changed(this); }
                                        catch (RuntimeException e) { runOnUiThread(() -> notice("恢复失败，请重试")); }
                                    })).show();
                        });
                    } catch (RuntimeException e) { runOnUiThread(() -> notice("删除失败，请重试")); }
                })).show();
    }

    private void showDetail(Parcel p) {
        LinearLayout box = column(); box.setPadding(dp(24), dp(4), dp(24), dp(8));
        TextView code = text(p.code, 32, true, R.color.primary); code.setTextIsSelectable(true); box.addView(code);
        field(box, "快递公司", p.company); field(box, "驿站名称", p.station); field(box, "驿站地址", p.address);
        field(box, "物流单号", p.tracking); field(box, "联系电话", p.phone); field(box, "网址", p.url);
        field(box, "收到时间", time(p.receivedAt));
        if (p.pickedAt > 0) field(box, "已取时间", time(p.pickedAt));
        field(box, "短信来源", p.sender); field(box, "短信原文", p.body);
        topMargin(box, button("复制取件码", false, v -> { ((ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("取件码", p.code)); notice("取件码已复制"); }), 8);
        if (!p.phone.isEmpty()) topMargin(box, button("拨打联系电话", false, v -> external(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + p.phone.replace(" ", ""))))), 0);
        if (!p.url.isEmpty()) topMargin(box, button("在浏览器打开网址", false, v -> new MaterialAlertDialogBuilder(this).setTitle("打开短信网址？")
                .setMessage("将交由浏览器打开，可能需要联网。请确认链接来源可信。\n\n" + p.url)
                .setNegativeButton("取消", null).setPositiveButton("打开", (d, w) -> external(new Intent(Intent.ACTION_VIEW, Uri.parse(p.url.startsWith("www.") ? "https://" + p.url : p.url)))).show()), 0);
        ScrollView scroll = new ScrollView(this); scroll.addView(box);
        new MaterialAlertDialogBuilder(this).setTitle("包裹详情").setView(scroll).setNegativeButton("关闭", null)
                .setPositiveButton(p.pickedAt > 0 ? "恢复待取" : "标记已取", (d, w) -> mark(p)).show();
    }

    private void showSettings() {
        ScrollView scroll = new ScrollView(this); LinearLayout box = column(); box.setPadding(dp(24), dp(12), dp(24), dp(24)); scroll.addView(box); content.addView(scroll);
        topMargin(box, text("短信识别", 20, true, R.color.on_surface), 12);
        field(box, "新短信自动识别", allowed(Manifest.permission.RECEIVE_SMS) ? "已开启" : "未授权");
        topMargin(box, button(allowed(Manifest.permission.RECEIVE_SMS) ? "管理应用权限" : "开启短信识别", false, v -> { if (allowed(Manifest.permission.RECEIVE_SMS)) appSettings(); else enableSms(); }), 8);
        topMargin(box, button(importing ? "正在导入…" : "导入近 7 天短信", false, v -> confirmImport()), 0);
        topMargin(box, text("导入只保存有取件码的包裹。之前已取的包裹可能需要手动标记。", 13, false, R.color.on_surface_variant), 4);
        field(box, "取件通知", ParcelNotifications.enabled(this) ? "已开启" : "未开启");
        topMargin(box, button(ParcelNotifications.enabled(this) ? "管理取件通知" : "开启取件通知", false, v -> enableNotifications()), 8);
        topMargin(box, text("只在新短信识别出新包裹时提醒，通知突出取件码和驿站位置。", 13, false, R.color.on_surface_variant), 4);
        topMargin(box, text("已取包裹", 20, true, R.color.on_surface), 28);
        TextInputLayout input = new TextInputLayout(this, null, com.google.android.material.R.attr.textInputOutlinedStyle);
        input.setHint("保留天数"); input.setHelperText("从标记已取时起算，0 为永不删除"); input.setSuffixText("天");
        TextInputEditText days = new TextInputEditText(input.getContext()); days.setInputType(InputType.TYPE_CLASS_NUMBER); days.setSingleLine(true); days.setText(String.valueOf(store.retentionDays())); input.addView(days);
        topMargin(box, input, 16);
        topMargin(box, button("保存保留天数", true, v -> {
            try {
                int value = Integer.parseInt(days.getText().toString().trim());
                if (value < 0) throw new NumberFormatException();
                input.setError(null);
                if (value == 0) saveRetention(value);
                else new MaterialAlertDialogBuilder(this).setTitle("保存保留天数？").setMessage("已取满 " + value + " 天的记录将被删除，之后每天自动清理。待取包裹不受影响。")
                        .setNegativeButton("取消", null).setPositiveButton("保存", (d, w) -> saveRetention(value)).show();
            } catch (NumberFormatException e) { input.setError("请输入 0 到 2147483647 之间的整数"); }
        }), 12);
        topMargin(box, text("桌面小组件", 20, true, R.color.on_surface), 28);
        topMargin(box, text("显示待取数量和所有取件码，包裹多时可以上下滚动。点击包裹查看详情。", 14, false, R.color.on_surface_variant), 8);
        topMargin(box, button("添加到桌面", false, v -> {
            AppWidgetManager manager = AppWidgetManager.getInstance(this);
            if (manager.isRequestPinAppWidgetSupported()) manager.requestPinAppWidget(new ComponentName(this, ParcelWidget.class), null, null);
            else notice("请长按桌面空白处 → 小组件 → 取件提醒");
        }), 8);
        topMargin(box, text("关于取件提醒", 20, true, R.color.on_surface), 28);
        topMargin(box, text("版本 " + BuildConfig.VERSION_NAME + " · Pride Versioning\n" + BuildConfig.APPLICATION_ID + "\n\n短信识别与记录全部保存在本机。应用没有联网权限，也不会上传短信。\n\n部分手机需额外允许自启动、后台运行和通知类短信权限。强行停止应用后，请重新打开。RCS / 5G 服务消息及只有链接的短信无法直接提取取件码。", 14, false, R.color.on_surface_variant), 8);
    }

    private void saveRetention(int days) {
        worker.execute(() -> {
            try {
                if (!store.settings().edit().putInt("retention_days", days).commit()) throw new IllegalStateException();
                int removed = store.cleanup(System.currentTimeMillis());
                runOnUiThread(() -> notice("已保存" + (removed > 0 ? "，清理了 " + removed + " 个已取包裹" : "")));
            } catch (RuntimeException e) { runOnUiThread(() -> notice("保存失败，请重试")); }
        });
    }

    private boolean allowed(String permission) { return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED; }
    private void enableSms() {
        new MaterialAlertDialogBuilder(this).setTitle("允许本机识别取件短信")
                .setMessage("需要「接收短信」权限，才能在新短信到达时提取取件码。接着可开启通知提醒。只有识别出的取件短信会保存在应用内，所有处理离线完成。")
                .setNegativeButton("暂不开启", null).setPositiveButton("继续", (d, w) -> requestPermissions(new String[]{Manifest.permission.RECEIVE_SMS}, 10)).show();
    }
    private void enableNotifications() {
        if (Build.VERSION.SDK_INT >= 33 && !allowed(Manifest.permission.POST_NOTIFICATIONS)) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 12);
        } else {
            external(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName()));
        }
    }
    @Override public void onRequestPermissionsResult(int request, @NonNull String[] permissions, @NonNull int[] results) {
        super.onRequestPermissionsResult(request, permissions, results);
        if (request == 12) {
            if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) notice("取件通知已开启");
            else new MaterialAlertDialogBuilder(this).setTitle("取件通知未开启").setMessage("包裹仍会自动加入待取列表。如需通知栏提醒，请在系统设置中允许通知。")
                    .setNegativeButton("知道了", null).setPositiveButton("通知设置", (d, w) -> external(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName()))).show();
        }
        else if (request == 10 && allowed(Manifest.permission.RECEIVE_SMS)) {
            if (Build.VERSION.SDK_INT >= 33 && !allowed(Manifest.permission.POST_NOTIFICATIONS)) enableNotifications();
        }
        else if (request == 11 && allowed(Manifest.permission.READ_SMS)) importInbox();
        else if (results.length == 0 || results[0] != PackageManager.PERMISSION_GRANTED) {
            new MaterialAlertDialogBuilder(this).setTitle("短信权限未开启").setMessage("可以继续粘贴短信识别。若系统不再显示授权窗口，请到应用权限中开启；部分系统还需允许通知类短信。")
                    .setNegativeButton("知道了", null).setPositiveButton("应用设置", (d, w) -> appSettings()).show();
        }
        reload();
    }
    private void appSettings() { external(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()))); }
    private void confirmImport() {
        if (importing) return;
        new MaterialAlertDialogBuilder(this).setTitle("导入近 7 天短信？").setMessage("会读取近 7 天的收件箱，只保存含取件码的快递短信。已有记录会去重，已取包裹请核对后标记。")
                .setNegativeButton("取消", null).setPositiveButton("导入", (d, w) -> {
                    if (allowed(Manifest.permission.READ_SMS)) importInbox(); else requestPermissions(new String[]{Manifest.permission.READ_SMS}, 11);
                }).show();
    }
    private void importInbox() {
        if (importing) return;
        importing = true; reload();
        worker.execute(() -> {
            int added = 0;
            try (Cursor c = getContentResolver().query(Telephony.Sms.Inbox.CONTENT_URI, new String[]{"body", "address", "date"}, "date>=?", new String[]{String.valueOf(System.currentTimeMillis() - 7L * 86400000L)}, "date ASC")) {
                if (c == null) throw new IllegalStateException();
                while (c.moveToNext()) added += store.ingest(c.getString(0), c.isNull(1) ? "" : c.getString(1), c.getLong(2)).size();
                store.settings().edit().putBoolean("receive_error", false).apply();
                int count = added; PickupApplication.changed(this);
                runOnUiThread(() -> notice("导入完成，新增 " + count + " 个包裹"));
            } catch (RuntimeException e) { runOnUiThread(() -> notice("导入失败，请检查短信权限和可用存储空间")); }
            finally { runOnUiThread(() -> { importing = false; reload(); }); }
        });
    }

    private void pasteSms() {
        TextInputLayout input = new TextInputLayout(this, null, com.google.android.material.R.attr.textInputOutlinedStyle);
        input.setHint("粘贴取件短信");
        TextInputEditText edit = new TextInputEditText(input.getContext()); edit.setMinLines(4); edit.setMaxLines(10); edit.setGravity(Gravity.TOP); edit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE); input.addView(edit);
        LinearLayout box = column(); box.setPadding(dp(24), dp(8), dp(24), 0); box.addView(input);
        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this).setTitle("从短信中识别").setView(box).setNegativeButton("取消", null).setPositiveButton("识别并添加", null).create();
        dialog.setOnShowListener(d -> dialog.getButton(-1).setOnClickListener(v -> {
            String body = edit.getText().toString().trim();
            if (SmsParser.parse(body).isEmpty()) { input.setError("未找到明确取件码；只有网址的短信无法离线提取"); return; }
            dialog.dismiss();
            worker.execute(() -> {
                try {
                    int count = store.ingest(body, "手动粘贴", System.currentTimeMillis()).size(); PickupApplication.changed(this);
                    runOnUiThread(() -> { navigation.setSelectedItemId(PENDING); reload(); notice(count == 0 ? "该短信已添加" : "已添加 " + count + " 个包裹"); });
                } catch (RuntimeException e) { runOnUiThread(() -> notice("保存失败，请重试")); }
            });
        })); dialog.show();
    }

    private void external(Intent intent) {
        try { startActivity(intent); } catch (android.content.ActivityNotFoundException e) { notice("未找到可处理此操作的应用"); }
    }
    private void notice(String message) { if (!isFinishing() && !isDestroyed()) Snackbar.make(root, message, Snackbar.LENGTH_LONG).setAnchorView(navigation).show(); }
    private void field(LinearLayout box, String label, String value) {
        if (value.isEmpty()) return;
        topMargin(box, text(label, 12, false, R.color.on_surface_variant), 16);
        TextView data = text(value, 15, false, R.color.on_surface); data.setTextIsSelectable(true); topMargin(box, data, 4);
    }
    private LinearLayout column() { LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.VERTICAL); return v; }
    private LinearLayout row() { LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.HORIZONTAL); return v; }
    private TextView text(String value, int size, boolean bold, int color) {
        TextView v = new TextView(this); v.setText(value); v.setTextSize(size); v.setTextColor(color(color)); v.setFontFeatureSettings("tnum");
        if (bold) v.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL)); v.setIncludeFontPadding(false); return v;
    }
    private MaterialButton button(String label, boolean filled, View.OnClickListener listener) {
        MaterialButton b = new MaterialButton(this, null, filled ? com.google.android.material.R.attr.materialButtonStyle : com.google.android.material.R.attr.borderlessButtonStyle);
        b.setText(label); b.setMinHeight(dp(48)); b.setOnClickListener(listener); return b;
    }
    private MaterialCardView card() {
        MaterialCardView card = new MaterialCardView(this); card.setRadius(dp(24)); card.setCardElevation(0); card.setStrokeWidth(0); card.setCardBackgroundColor(color(R.color.surface_container)); return card;
    }
    private void topMargin(LinearLayout parent, View view, int top) { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.topMargin = dp(top); parent.addView(view, p); }
    private LinearLayout.LayoutParams margins(int width, int height, int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(width, height); p.setMargins(dp(left), dp(top), dp(right), dp(bottom)); return p;
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private int color(int resource) { return ContextCompat.getColor(this, resource); }
    private static String time(long value) { return new SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(new Date(value)); }
}
