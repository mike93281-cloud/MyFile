package com.myfile.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.StatFs;
import android.text.format.Formatter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Pantalla de inicio: almacenamiento, categorías, carpetas concedidas y menú lateral. */
public class MainActivity extends Activity {

    private static final int REQ_PICK_FOLDER = 10;
    private static final int REQ_MEDIA_PERMISSION = 11;

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    private View scrim;
    private View drawer;
    private LinearLayout locationsContainer;
    private LinearLayout drawerItems;
    private TextView noLocations;
    private Category pendingCategory;
    private List<Uri> lastTrees = new ArrayList<>();
    private List<String> lastNames = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        scrim = findViewById(R.id.scrim);
        drawer = findViewById(R.id.drawer);
        locationsContainer = findViewById(R.id.locations_container);
        drawerItems = findViewById(R.id.drawer_items);
        noLocations = findViewById(R.id.tv_no_locations);

        findViewById(R.id.btn_menu).setOnClickListener(v -> openDrawer());
        findViewById(R.id.btn_search).setOnClickListener(v -> askGlobalSearch());
        findViewById(R.id.btn_add_location).setOnClickListener(v -> pickFolder());
        findViewById(R.id.privacy_card).setOnClickListener(v -> showPrivacy());
        scrim.setOnClickListener(v -> closeDrawer());

        buildCategories();
        buildTools();
        updateStorage();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshLocations();
        refreshFavorites();
    }

    @Override
    protected void onDestroy() {
        io.shutdownNow();
        super.onDestroy();
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (drawer.getVisibility() == View.VISIBLE) {
            closeDrawer();
        } else {
            super.onBackPressed();
        }
    }

    // ---------------------------------------------------------------- Almacenamiento

    private void updateStorage() {
        try {
            StatFs fs = new StatFs(Environment.getDataDirectory().getPath());
            long total = fs.getTotalBytes();
            long free = fs.getAvailableBytes();
            long used = total - free;
            int pct = total > 0 ? (int) (used * 100 / total) : 0;
            ((TextView) findViewById(R.id.tv_storage_detail)).setText(getString(R.string.storage_detail,
                    Formatter.formatShortFileSize(this, used),
                    Formatter.formatShortFileSize(this, total)));
            ((TextView) findViewById(R.id.tv_storage_percent)).setText(pct + "%");
            ((ProgressBar) findViewById(R.id.progress_storage)).setProgress(pct);
        } catch (Exception ignored) {
        }
    }

    // ---------------------------------------------------------------- Categorías

    private void buildCategories() {
        LinearLayout container = findViewById(R.id.categories_container);
        LayoutInflater inf = getLayoutInflater();
        Category[] cats = Category.values();
        LinearLayout row = null;
        for (int i = 0; i < cats.length; i++) {
            if (i % 3 == 0) {
                row = (LinearLayout) inf.inflate(R.layout.item_category_row, container, false);
                container.addView(row);
            }
            final Category c = cats[i];
            View cell = inf.inflate(R.layout.item_category, row, false);
            ImageView icon = cell.findViewById(R.id.cat_icon);
            icon.setImageResource(c.icon);
            icon.setBackgroundTintList(ColorStateList.valueOf(getColor(c.tint)));
            ((TextView) cell.findViewById(R.id.cat_label)).setText(c.label);
            cell.setOnClickListener(v -> openCategory(c));
            row.addView(cell);
        }
    }

    // ---------------------------------------------------------------- Herramientas

    private void buildTools() {
        LinearLayout container = findViewById(R.id.tools_container);
        addTool(container, R.drawable.ic_tool_trash, R.string.trash, R.color.cat_videos, () -> openList(BrowserActivity.MODE_TRASH));
        addTool(container, R.drawable.ic_tool_analyze, R.string.analyzer, R.color.cat_docs, this::openAnalyzer);
        addTool(container, R.drawable.ic_tool_recent, R.string.recent, R.color.cat_archives, () -> openList(BrowserActivity.MODE_RECENT));
        addTool(container, R.drawable.ic_tool_fav, R.string.favorites, R.color.cat_apk, () -> openList(BrowserActivity.MODE_FAVORITES));
    }

    private void addTool(LinearLayout container, int icon, int label, int tint, Runnable action) {
        View cell = getLayoutInflater().inflate(R.layout.item_category, container, false);
        ImageView iv = cell.findViewById(R.id.cat_icon);
        iv.setImageResource(icon);
        iv.setBackgroundTintList(ColorStateList.valueOf(getColor(tint)));
        ((TextView) cell.findViewById(R.id.cat_label)).setText(label);
        cell.setOnClickListener(v -> action.run());
        container.addView(cell);
    }

    private void openList(int listMode) {
        closeDrawer();
        startActivity(BrowserActivity.forList(this, listMode));
    }

    private void openAnalyzer() {
        closeDrawer();
        if (lastTrees.isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.analyzer)
                    .setMessage(R.string.category_needs_folder)
                    .setPositiveButton(R.string.add_folder, (d, w) -> pickFolder())
                    .setNegativeButton(R.string.cancel, null)
                    .show();
            return;
        }
        if (lastTrees.size() == 1) {
            startActivity(AnalyzerActivity.intent(this, lastTrees.get(0), null, lastNames.get(0)));
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.analyze_which)
                .setItems(lastNames.toArray(new CharSequence[0]), (d, which) ->
                        startActivity(AnalyzerActivity.intent(this, lastTrees.get(which), null, lastNames.get(which))))
                .show();
    }

    private void refreshFavorites() {
        LinearLayout container = findViewById(R.id.favorites_container);
        container.removeAllViews();
        List<Store.Entry> favs = Store.get(this, Store.FAVORITES);
        int shown = Math.min(favs.size(), 5);
        findViewById(R.id.favorites_title).setVisibility(shown > 0 ? View.VISIBLE : View.GONE);
        container.setVisibility(shown > 0 ? View.VISIBLE : View.GONE);
        LayoutInflater inf = getLayoutInflater();
        for (int i = 0; i < shown; i++) {
            final Store.Entry e = favs.get(i);
            final FileItem f = e.toItem();
            View row = inf.inflate(R.layout.item_location, container, false);
            ((ImageView) row.findViewById(R.id.loc_icon)).setImageResource(FileTypes.iconFor(f));
            ((TextView) row.findViewById(R.id.loc_name)).setText(e.name);
            ((TextView) row.findViewById(R.id.loc_detail)).setText(
                    e.doc != null ? e.doc.replaceFirst("^primary:", getString(R.string.internal_short) + "/") : "");
            row.setOnClickListener(v -> {
                if (f.isDir() && f.treeUri != null) {
                    startActivity(BrowserActivity.forTreeAt(this, f.treeUri, f.docId, f.name));
                } else {
                    FileOpener.open(this, f, null);
                }
            });
            row.findViewById(R.id.loc_remove).setOnClickListener(v -> {
                Store.remove(this, Store.FAVORITES, e.uri);
                refreshFavorites();
            });
            container.addView(row);
        }
        if (favs.size() > shown) {
            TextView more = (TextView) inf.inflate(R.layout.item_drawer, container, false);
            more.setText(R.string.see_all);
            more.setTextColor(getColor(R.color.primary));
            more.setOnClickListener(v -> openList(BrowserActivity.MODE_FAVORITES));
            container.addView(more);
        }
    }

    private void openCategory(Category c) {
        if (c.media) {
            String perm = c.permission();
            if (checkSelfPermission(perm) != PackageManager.PERMISSION_GRANTED) {
                pendingCategory = c;
                requestPermissions(new String[]{perm}, REQ_MEDIA_PERMISSION);
                return;
            }
        } else if (SafOps.grantedTrees(getContentResolver()).isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle(c.label)
                    .setMessage(R.string.category_needs_folder)
                    .setPositiveButton(R.string.add_folder, (d, w) -> pickFolder())
                    .setNegativeButton(R.string.cancel, null)
                    .show();
            return;
        }
        startActivity(BrowserActivity.forCategory(this, c));
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQ_MEDIA_PERMISSION || pendingCategory == null) return;
        Category c = pendingCategory;
        pendingCategory = null;
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startActivity(BrowserActivity.forCategory(this, c));
        } else {
            Toast.makeText(this, R.string.permission_denied, Toast.LENGTH_LONG).show();
        }
    }

    // ---------------------------------------------------------------- Carpetas

    @SuppressWarnings("deprecation")
    private void pickFolder() {
        closeDrawer();
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try {
            startActivityForResult(i, REQ_PICK_FOLDER);
        } catch (Exception e) {
            Toast.makeText(this, R.string.no_picker, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_PICK_FOLDER || resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        Uri tree = data.getData();
        try {
            getContentResolver().takePersistableUriPermission(tree,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        } catch (SecurityException e) {
            Toast.makeText(this, R.string.folder_error, Toast.LENGTH_LONG).show();
            return;
        }
        refreshLocations();
        startActivity(BrowserActivity.forTree(this, tree));
    }

    private void refreshLocations() {
        final List<Uri> trees = SafOps.grantedTrees(getContentResolver());
        io.execute(() -> {
            final List<String> names = new ArrayList<>();
            for (Uri t : trees) {
                FileItem root = SafOps.query(getContentResolver(), t, SafOps.rootDocId(t), null);
                names.add(root != null ? root.name : t.getLastPathSegment());
            }
            main.post(() -> showLocations(trees, names));
        });
    }

    private void showLocations(List<Uri> trees, List<String> names) {
        if (isFinishing()) return;
        lastTrees = trees;
        lastNames = names;
        locationsContainer.removeAllViews();
        noLocations.setVisibility(trees.isEmpty() ? View.VISIBLE : View.GONE);
        LayoutInflater inf = getLayoutInflater();
        for (int i = 0; i < trees.size(); i++) {
            final Uri tree = trees.get(i);
            final String name = names.get(i);
            View row = inf.inflate(R.layout.item_location, locationsContainer, false);
            ((TextView) row.findViewById(R.id.loc_name)).setText(name);
            ((TextView) row.findViewById(R.id.loc_detail)).setText(describeTree(tree));
            row.setOnClickListener(v -> startActivity(BrowserActivity.forTree(this, tree)));
            row.findViewById(R.id.loc_remove).setOnClickListener(v -> confirmRemove(tree, name));
            locationsContainer.addView(row);
        }
        buildDrawer(trees, names);
    }

    /** Ruta legible de una carpeta concedida, p. ej. "primary:DCIM/Camera" → "Interno / DCIM/Camera". */
    private String describeTree(Uri tree) {
        String id = SafOps.rootDocId(tree);
        int colon = id.indexOf(':');
        if (colon < 0) return tree.getAuthority();
        String vol = id.substring(0, colon);
        String path = id.substring(colon + 1);
        String volName = "primary".equals(vol) ? getString(R.string.internal_short) : vol;
        return path.isEmpty() ? volName : volName + " / " + path;
    }

    private void confirmRemove(Uri tree, String name) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.remove_access)
                .setMessage(getString(R.string.remove_access_msg, name))
                .setPositiveButton(R.string.remove, (d, w) -> {
                    try {
                        getContentResolver().releasePersistableUriPermission(tree,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                    } catch (SecurityException ignored) {
                    }
                    refreshLocations();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    // ---------------------------------------------------------------- Búsqueda

    private void askGlobalSearch() {
        if (SafOps.grantedTrees(getContentResolver()).isEmpty()) {
            Toast.makeText(this, R.string.category_needs_folder, Toast.LENGTH_LONG).show();
            return;
        }
        View v = getLayoutInflater().inflate(R.layout.dialog_input, null);
        EditText input = v.findViewById(R.id.input);
        input.setHint(R.string.search_hint);
        new AlertDialog.Builder(this)
                .setTitle(R.string.search_all)
                .setView(v)
                .setPositiveButton(R.string.search, (d, w) -> {
                    String q = input.getText().toString().trim();
                    if (!q.isEmpty()) startActivity(BrowserActivity.forSearch(this, q));
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
        input.requestFocus();
    }

    // ---------------------------------------------------------------- Menú lateral

    private void buildDrawer(List<Uri> trees, List<String> names) {
        drawerItems.removeAllViews();
        addDrawerItem(R.drawable.ic_home, getString(R.string.home), this::closeDrawer);
        for (Category c : Category.values()) {
            addDrawerItem(c.icon, getString(c.label), () -> { closeDrawer(); openCategory(c); });
        }
        addDrawerDivider();
        for (int i = 0; i < trees.size(); i++) {
            final Uri t = trees.get(i);
            addDrawerItem(R.drawable.ic_folder, names.get(i),
                    () -> { closeDrawer(); startActivity(BrowserActivity.forTree(this, t)); });
        }
        addDrawerItem(R.drawable.ic_add, getString(R.string.add_folder), this::pickFolder);
        addDrawerDivider();
        addDrawerItem(R.drawable.ic_star, getString(R.string.favorites), () -> openList(BrowserActivity.MODE_FAVORITES));
        addDrawerItem(R.drawable.ic_restore, getString(R.string.recent), () -> openList(BrowserActivity.MODE_RECENT));
        addDrawerItem(R.drawable.ic_delete, getString(R.string.trash), () -> openList(BrowserActivity.MODE_TRASH));
        addDrawerItem(R.drawable.ic_grid, getString(R.string.analyzer), this::openAnalyzer);
        addDrawerDivider();
        addDrawerItem(R.drawable.ic_shield, getString(R.string.privacy), () -> { closeDrawer(); showPrivacy(); });
        addDrawerItem(R.drawable.ic_info, getString(R.string.about), () -> { closeDrawer(); showAbout(); });
    }

    private void addDrawerItem(int icon, String label, Runnable action) {
        TextView tv = (TextView) getLayoutInflater().inflate(R.layout.item_drawer, drawerItems, false);
        Drawable d = getDrawable(icon);
        if (d != null) {
            int size = (int) (24 * getResources().getDisplayMetrics().density);
            d = d.mutate();
            d.setBounds(0, 0, size, size);
        }
        tv.setCompoundDrawablesRelative(d, null, null, null);
        tv.setCompoundDrawableTintList(ColorStateList.valueOf(getColor(R.color.text_secondary)));
        tv.setText(label);
        tv.setOnClickListener(v -> action.run());
        drawerItems.addView(tv);
    }

    private void addDrawerDivider() {
        View line = new View(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, (int) getResources().getDisplayMetrics().density));
        int m = (int) (8 * getResources().getDisplayMetrics().density);
        lp.setMargins(0, m, 0, m);
        line.setLayoutParams(lp);
        line.setBackgroundColor(getColor(R.color.divider));
        drawerItems.addView(line);
    }

    private void openDrawer() {
        drawer.setVisibility(View.VISIBLE);
        scrim.setVisibility(View.VISIBLE);
        drawer.post(() -> {
            drawer.setTranslationX(-drawer.getWidth());
            drawer.animate().translationX(0).setDuration(220).start();
            scrim.animate().alpha(1f).setDuration(220).start();
        });
    }

    private void closeDrawer() {
        if (drawer.getVisibility() != View.VISIBLE) return;
        drawer.animate().translationX(-drawer.getWidth()).setDuration(200)
                .withEndAction(() -> drawer.setVisibility(View.GONE)).start();
        scrim.animate().alpha(0f).setDuration(200)
                .withEndAction(() -> scrim.setVisibility(View.GONE)).start();
    }

    // ---------------------------------------------------------------- Diálogos

    private void showPrivacy() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.privacy)
                .setMessage(R.string.privacy_long)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private void showAbout() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.app_name)
                .setMessage(getString(R.string.about_msg, BuildInfo.versionName(this)))
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }
}
