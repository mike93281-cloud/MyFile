package com.myfile.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.text.format.Formatter;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.MimeTypeMap;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** Explorador: contenido de una carpeta, una categoría o una búsqueda. */
public class BrowserActivity extends Activity {

    private static final String EXTRA_MODE = "mode";
    private static final String EXTRA_TREE = "tree";
    private static final String EXTRA_DOC = "doc";
    private static final String EXTRA_NAME = "name";
    private static final String EXTRA_CATEGORY = "category";
    private static final String EXTRA_QUERY = "query";

    private static final int MODE_TREE = 0;
    private static final int MODE_CATEGORY = 1;
    private static final int MODE_SEARCH = 2;
    public static final int MODE_FAVORITES = 3;
    public static final int MODE_RECENT = 4;
    public static final int MODE_TRASH = 5;

    private static final int SORT_NAME = 0;
    private static final int SORT_DATE = 1;
    private static final int SORT_SIZE = 2;

    private static final int REQ_MEDIA_DELETE = 20;
    private static final int SEARCH_LIMIT = 2000;

    public static Intent forTree(Context ctx, Uri tree) {
        return new Intent(ctx, BrowserActivity.class).putExtra(EXTRA_MODE, MODE_TREE).putExtra(EXTRA_TREE, tree);
    }

    public static Intent forTreeAt(Context ctx, Uri tree, String docId, String name) {
        return forTree(ctx, tree).putExtra(EXTRA_DOC, docId).putExtra(EXTRA_NAME, name);
    }

    public static Intent forCategory(Context ctx, Category c) {
        return new Intent(ctx, BrowserActivity.class).putExtra(EXTRA_MODE, MODE_CATEGORY)
                .putExtra(EXTRA_CATEGORY, c.name());
    }

    /** Favoritos, recientes o papelera. */
    public static Intent forList(Context ctx, int listMode) {
        return new Intent(ctx, BrowserActivity.class).putExtra(EXTRA_MODE, listMode);
    }

    public static Intent forSearch(Context ctx, String query) {
        return new Intent(ctx, BrowserActivity.class).putExtra(EXTRA_MODE, MODE_SEARCH)
                .putExtra(EXTRA_QUERY, query);
    }

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Set<String> selection = FileAdapter.newSelection();
    private final ArrayList<String> stackIds = new ArrayList<>();
    private final ArrayList<String> stackNames = new ArrayList<>();
    private final List<FileItem> items = new ArrayList<>();

    private int mode;
    private Category category;
    private String globalQuery;
    private String localQuery;
    private Uri tree;

    private SharedPreferences prefs;
    private ThumbLoader thumbs;
    private FileAdapter listAdapter;
    private FileAdapter gridAdapter;
    private ListView listView;
    private GridView gridView;
    private View empty;
    private TextView emptyText;
    private View loading;
    private View toolbar;
    private View selectionBar;
    private View searchBar;
    private EditText searchInput;
    private TextView title;
    private TextView subtitle;
    private TextView selectionCount;
    private ImageButton viewButton;
    private View fab;
    private View pasteBar;
    private TextView pasteInfo;
    private LinearLayout actionBar;
    private TextView actCopy, actMove, actRename, actShare, actDelete, actMore;
    private TextView actRestore, actPurge, actInfo;

    private AtomicBoolean loadCancel = new AtomicBoolean();
    private int generation;

    // ================================================================ Ciclo de vida

    @Override
    @SuppressWarnings("deprecation")
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_browser);
        prefs = getSharedPreferences("settings", MODE_PRIVATE);
        thumbs = new ThumbLoader(getContentResolver());

        Intent in = getIntent();
        mode = in.getIntExtra(EXTRA_MODE, MODE_TREE);
        if (mode == MODE_TREE) {
            tree = in.getParcelableExtra(EXTRA_TREE);
            if (tree == null) { finish(); return; }
            String doc = in.getStringExtra(EXTRA_DOC);
            if (doc == null) {
                doc = SafOps.rootDocId(tree);
                FileItem root = SafOps.query(getContentResolver(), tree, doc, null);
                stackNames.add(root != null ? root.name : doc);
            } else {
                stackNames.add(in.getStringExtra(EXTRA_NAME));
            }
            stackIds.add(doc);
        } else if (mode == MODE_CATEGORY) {
            category = Category.valueOf(in.getStringExtra(EXTRA_CATEGORY));
        } else if (mode == MODE_SEARCH) {
            globalQuery = in.getStringExtra(EXTRA_QUERY);
        }

        bindViews();
        setupActions();
        applyViewMode();
        load();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updatePasteBar();
    }

    @Override
    protected void onDestroy() {
        loadCancel.set(true);
        io.shutdownNow();
        if (thumbs != null) thumbs.shutdown();
        super.onDestroy();
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (!selection.isEmpty()) {
            clearSelection();
        } else if (searchBar.getVisibility() == View.VISIBLE) {
            closeSearch();
        } else if (mode == MODE_TREE && stackIds.size() > 1) {
            stackIds.remove(stackIds.size() - 1);
            stackNames.remove(stackNames.size() - 1);
            load();
        } else {
            super.onBackPressed();
        }
    }

    // ================================================================ Vistas

    private void bindViews() {
        listView = findViewById(R.id.list);
        gridView = findViewById(R.id.grid);
        empty = findViewById(R.id.empty);
        emptyText = findViewById(R.id.tv_empty);
        loading = findViewById(R.id.loading);
        toolbar = findViewById(R.id.toolbar);
        selectionBar = findViewById(R.id.selection_bar);
        searchBar = findViewById(R.id.search_bar);
        searchInput = findViewById(R.id.et_search);
        title = findViewById(R.id.tv_title);
        subtitle = findViewById(R.id.tv_subtitle);
        selectionCount = findViewById(R.id.tv_selection_count);
        viewButton = findViewById(R.id.btn_view);
        fab = findViewById(R.id.fab_new_folder);
        pasteBar = findViewById(R.id.paste_bar);
        pasteInfo = findViewById(R.id.tv_paste_info);
        actionBar = findViewById(R.id.action_bar);

        listAdapter = new FileAdapter(this, thumbs, false, selection);
        gridAdapter = new FileAdapter(this, thumbs, true, selection);
        listView.setAdapter(listAdapter);
        gridView.setAdapter(gridAdapter);

        AdapterView.OnItemClickListener click = (p, v, pos, id) -> onItemClick(items.get(pos));
        AdapterView.OnItemLongClickListener longClick = (p, v, pos, id) -> {
            toggleSelection(items.get(pos));
            return true;
        };
        listView.setOnItemClickListener(click);
        gridView.setOnItemClickListener(click);
        listView.setOnItemLongClickListener(longClick);
        gridView.setOnItemLongClickListener(longClick);
        listView.setChoiceMode(AbsListView.CHOICE_MODE_NONE);

        findViewById(R.id.btn_back).setOnClickListener(v -> onBackPressed());
        findViewById(R.id.btn_search).setOnClickListener(v -> openSearch());
        findViewById(R.id.btn_more).setOnClickListener(this::showMoreMenu);
        findViewById(R.id.btn_close_selection).setOnClickListener(v -> clearSelection());
        findViewById(R.id.btn_select_all).setOnClickListener(v -> selectAll());
        findViewById(R.id.btn_close_search).setOnClickListener(v -> closeSearch());
        findViewById(R.id.btn_paste).setOnClickListener(v -> paste());
        findViewById(R.id.btn_paste_cancel).setOnClickListener(v -> { Clipboard.clear(); updatePasteBar(); });
        viewButton.setOnClickListener(v -> {
            prefs.edit().putBoolean("grid", !isGrid()).apply();
            applyViewMode();
        });
        fab.setOnClickListener(v -> askNewFolder());

        searchInput.setOnEditorActionListener((v, actionId, event) -> {
            boolean enter = event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    && event.getAction() == KeyEvent.ACTION_DOWN;
            if (actionId == EditorInfo.IME_ACTION_SEARCH || enter) {
                String q = searchInput.getText().toString().trim();
                if (!q.isEmpty()) {
                    localQuery = q;
                    hideKeyboard();
                    load();
                }
                return true;
            }
            return false;
        });
    }

    private void setupActions() {
        if (mode == MODE_TRASH) {
            actRestore = addAction(R.drawable.ic_restore, R.string.restore, v -> restoreSelected());
            actPurge = addAction(R.drawable.ic_delete, R.string.delete_forever, v -> confirmPurge(selectedItems()));
            actInfo = addAction(R.drawable.ic_info, R.string.details, v -> showDetails());
            return;
        }
        actCopy = addAction(R.drawable.ic_copy, R.string.copy, v -> toClipboard(false));
        actMove = addAction(R.drawable.ic_move, R.string.move, v -> toClipboard(true));
        actRename = addAction(R.drawable.ic_rename, R.string.rename, v -> askRename());
        actShare = addAction(R.drawable.ic_share, R.string.share, v -> share());
        actDelete = addAction(R.drawable.ic_delete, R.string.delete, v -> confirmDelete());
        actMore = addAction(R.drawable.ic_more, R.string.more, this::showSelectionMenu);
    }

    private TextView addAction(int icon, int label, View.OnClickListener l) {
        TextView tv = (TextView) getLayoutInflater().inflate(R.layout.item_action, actionBar, false);
        Drawable d = getDrawable(icon);
        if (d != null) {
            int size = (int) (22 * getResources().getDisplayMetrics().density);
            d = d.mutate();
            d.setBounds(0, 0, size, size);
        }
        tv.setCompoundDrawablesRelative(null, d, null, null);
        tv.setCompoundDrawableTintList(ColorStateList.valueOf(getColor(R.color.text_primary)));
        tv.setText(label);
        tv.setOnClickListener(l);
        actionBar.addView(tv);
        return tv;
    }

    private boolean isGrid() {
        return prefs.getBoolean("grid", false);
    }

    private void applyViewMode() {
        boolean grid = isGrid();
        listView.setVisibility(grid ? View.GONE : View.VISIBLE);
        gridView.setVisibility(grid ? View.VISIBLE : View.GONE);
        viewButton.setImageResource(grid ? R.drawable.ic_list : R.drawable.ic_grid);
        refreshAdapters();
    }

    private void refreshAdapters() {
        listAdapter.setItems(items);
        gridAdapter.setItems(items);
    }

    private void updateHeader() {
        if (localQuery != null) {
            title.setText(getString(R.string.results_for, localQuery));
            subtitle.setText(getResources().getQuantityString(R.plurals.items, items.size(), items.size()));
            return;
        }
        switch (mode) {
            case MODE_TREE:
                title.setText(stackNames.get(stackNames.size() - 1));
                subtitle.setText(TextUtils.join(" / ", stackNames));
                break;
            case MODE_CATEGORY:
                title.setText(category.label);
                subtitle.setText(getResources().getQuantityString(R.plurals.items, items.size(), items.size()));
                break;
            case MODE_FAVORITES:
            case MODE_RECENT:
            case MODE_TRASH:
                title.setText(mode == MODE_FAVORITES ? R.string.favorites
                        : mode == MODE_RECENT ? R.string.recent : R.string.trash);
                subtitle.setText(getResources().getQuantityString(R.plurals.items, items.size(), items.size()));
                break;
            default:
                title.setText(getString(R.string.results_for, globalQuery));
                subtitle.setText(getResources().getQuantityString(R.plurals.items, items.size(), items.size()));
        }
    }

    private void updateChrome() {
        boolean selecting = !selection.isEmpty();
        boolean searching = searchBar.getVisibility() == View.VISIBLE;
        toolbar.setVisibility(selecting || searching ? View.GONE : View.VISIBLE);
        selectionBar.setVisibility(selecting ? View.VISIBLE : View.GONE);
        if (selecting) searchBar.setVisibility(View.GONE);
        else if (localQuery != null) searchBar.setVisibility(View.VISIBLE);
        actionBar.setVisibility(selecting ? View.VISIBLE : View.GONE);
        fab.setVisibility(mode == MODE_TREE && !selecting && localQuery == null && !searching
                ? View.VISIBLE : View.GONE);
        if (selecting) {
            selectionCount.setText(getString(R.string.selected_count, selection.size()));
            updateActionStates();
        }
        updatePasteBar();
    }

    private void updatePasteBar() {
        boolean show = mode == MODE_TREE && !Clipboard.isEmpty() && selection.isEmpty() && localQuery == null;
        pasteBar.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show) {
            int n = Clipboard.items().size();
            pasteInfo.setText(getResources().getQuantityString(
                    Clipboard.isCut() ? R.plurals.to_move : R.plurals.to_copy, n, n));
        }
    }

    private void updateActionStates() {
        List<FileItem> sel = selectedItems();
        if (mode == MODE_TRASH) {
            enable(actRestore, !sel.isEmpty());
            enable(actPurge, !sel.isEmpty());
            enable(actInfo, sel.size() == 1);
            return;
        }
        boolean allDocs = true, anyDir = false;
        for (FileItem f : sel) {
            if (!f.isDocument()) allDocs = false;
            if (f.isDir()) anyDir = true;
        }
        enable(actCopy, allDocs && !sel.isEmpty());
        enable(actMove, allDocs && !sel.isEmpty());
        enable(actRename, allDocs && sel.size() == 1);
        enable(actShare, !anyDir && !sel.isEmpty());
        enable(actDelete, !sel.isEmpty());
        enable(actMore, !sel.isEmpty());
    }

    private static void enable(View v, boolean on) {
        v.setEnabled(on);
        v.setAlpha(on ? 1f : 0.35f);
    }

    // ================================================================ Carga

    private String currentDocId() {
        return stackIds.get(stackIds.size() - 1);
    }

    private void load() {
        loadCancel.set(true);
        final AtomicBoolean cancel = new AtomicBoolean();
        loadCancel = cancel;
        final int gen = ++generation;
        selection.clear();
        loading.setVisibility(View.VISIBLE);
        empty.setVisibility(View.GONE);
        items.clear();
        refreshAdapters();
        updateHeader();
        updateChrome();

        final ContentResolver cr = getContentResolver();
        io.execute(() -> {
            List<FileItem> result = new ArrayList<>();
            boolean failed = false;
            try {
                fetch(cr, result, cancel);
            } catch (Exception e) {
                failed = true;
            }
            final List<FileItem> shown = visible(result);
            if (mode != MODE_RECENT && mode != MODE_TRASH) sort(shown);
            else Collections.sort(shown, (a, b) -> Long.compare(b.modified, a.modified));
            final boolean error = failed;
            main.post(() -> {
                if (gen != generation || isFinishing() || isDestroyed()) return;
                showResult(shown, error);
            });
        });
    }

    private void fetch(ContentResolver cr, List<FileItem> out, AtomicBoolean cancel) {
        if (localQuery != null) {
            SafOps.Filter f = SafOps.nameContains(localQuery);
            if (mode == MODE_TREE) {
                SafOps.search(cr, tree, currentDocId(), f, out, SEARCH_LIMIT, cancel);
            } else {
                // En categorías y búsquedas, se filtra lo que ya hay.
                List<FileItem> base = new ArrayList<>();
                fetchBase(cr, base, cancel);
                for (FileItem it : base) if (f.accept(it)) out.add(it);
            }
            return;
        }
        fetchBase(cr, out, cancel);
    }

    private void fetchBase(ContentResolver cr, List<FileItem> out, AtomicBoolean cancel) {
        switch (mode) {
            case MODE_TREE:
                out.addAll(SafOps.list(cr, tree, currentDocId()));
                break;
            case MODE_CATEGORY:
                if (category.media) {
                    out.addAll(MediaOps.query(cr, category));
                } else {
                    final Category c = category;
                    for (Uri t : SafOps.grantedTrees(cr)) {
                        SafOps.search(cr, t, SafOps.rootDocId(t), f -> !f.isDir() && c.matches(f),
                                out, SEARCH_LIMIT, cancel);
                    }
                }
                break;
            case MODE_FAVORITES:
                out.addAll(resolveEntries(cr, Store.FAVORITES));
                break;
            case MODE_RECENT:
                out.addAll(resolveEntries(cr, Store.RECENT));
                break;
            case MODE_TRASH:
                out.addAll(resolveEntries(cr, Store.TRASH));
                break;
            default:
                SafOps.Filter f = SafOps.nameContains(globalQuery);
                for (Uri t : SafOps.grantedTrees(cr)) {
                    SafOps.search(cr, t, SafOps.rootDocId(t), f, out, SEARCH_LIMIT, cancel);
                }
        }
    }

    /** Convierte lo guardado en elementos reales; descarta lo que ya no existe. */
    private List<FileItem> resolveEntries(ContentResolver cr, String key) {
        List<FileItem> out = new ArrayList<>();
        List<Store.Entry> entries = Store.get(this, key);
        boolean changed = false;
        for (Store.Entry e : entries.toArray(new Store.Entry[0])) {
            FileItem f = null;
            if (e.tree != null) {
                FileItem live = SafOps.query(cr, e.treeUri(), e.doc, e.parent);
                if (live != null) {
                    // En la papelera se muestra el nombre y la fecha de borrado.
                    f = Store.TRASH.equals(key)
                            ? new FileItem(live.uri, live.treeUri, live.docId, e.trashDir, e.name,
                                    live.mime, live.size, e.time, live.flags)
                            : live;
                }
            } else {
                f = e.toItem();
            }
            if (f == null) {
                entries.remove(e);
                changed = true;
            } else {
                out.add(f);
            }
        }
        if (changed) Store.put(this, key, entries);
        return out;
    }

    private List<FileItem> visible(List<FileItem> list) {
        boolean hidden = Store.showHidden(this);
        List<FileItem> out = new ArrayList<>();
        for (FileItem f : list) {
            if (f.isDir() && SafOps.TRASH_NAME.equals(f.name) && mode != MODE_TRASH) continue;
            if (!hidden && mode != MODE_TRASH && f.name.startsWith(".")) continue;
            out.add(f);
        }
        return out;
    }

    private void sort(List<FileItem> list) {
        final int by = prefs.getInt("sort", SORT_NAME);
        final boolean asc = prefs.getBoolean("asc", true);
        Comparator<FileItem> cmp;
        if (by == SORT_DATE) {
            cmp = (a, b) -> Long.compare(a.modified, b.modified);
        } else if (by == SORT_SIZE) {
            cmp = (a, b) -> Long.compare(a.size, b.size);
        } else {
            cmp = (a, b) -> a.name.compareToIgnoreCase(b.name);
        }
        final Comparator<FileItem> ordered = asc ? cmp : Collections.reverseOrder(cmp);
        Collections.sort(list, (a, b) -> {
            if (a.isDir() != b.isDir()) return a.isDir() ? -1 : 1;
            return ordered.compare(a, b);
        });
    }

    private void showResult(List<FileItem> result, boolean error) {
        loading.setVisibility(View.GONE);
        items.clear();
        items.addAll(result);
        refreshAdapters();
        updateHeader();
        updateChrome();
        if (items.isEmpty()) {
            empty.setVisibility(View.VISIBLE);
            if (error) emptyText.setText(R.string.load_error);
            else if (localQuery != null || mode == MODE_SEARCH) emptyText.setText(R.string.no_results);
            else if (mode == MODE_FAVORITES) emptyText.setText(R.string.no_favorites);
            else if (mode == MODE_RECENT) emptyText.setText(R.string.no_recent);
            else if (mode == MODE_TRASH) emptyText.setText(R.string.trash_empty);
            else emptyText.setText(R.string.empty_folder);
        }
    }

    // ================================================================ Clics y selección

    private void onItemClick(FileItem f) {
        if (!selection.isEmpty()) {
            toggleSelection(f);
            return;
        }
        if (f.isDir()) {
            if (mode == MODE_TREE && localQuery == null) {
                stackIds.add(f.docId);
                stackNames.add(f.name);
                load();
            } else {
                startActivity(forTreeAt(this, f.treeUri, f.docId, f.name));
            }
            return;
        }
        if (mode == MODE_TRASH) {
            toggleSelection(f);
            return;
        }
        if (ZipOps.isZip(f) && f.isDocument()) {
            new AlertDialog.Builder(this)
                    .setTitle(f.name)
                    .setItems(new CharSequence[]{getString(R.string.extract_here), getString(R.string.open_with)},
                            (d, which) -> {
                                if (which == 0) extract(f);
                                else FileOpener.openExternal(this, f);
                            })
                    .show();
            return;
        }
        FileOpener.open(this, f, items);
    }

    private void toggleSelection(FileItem f) {
        String key = f.uri.toString();
        if (!selection.remove(key)) selection.add(key);
        refreshAdapters();
        updateChrome();
    }

    private void selectAll() {
        for (FileItem f : items) selection.add(f.uri.toString());
        refreshAdapters();
        updateChrome();
    }

    private void clearSelection() {
        selection.clear();
        refreshAdapters();
        updateChrome();
    }

    private List<FileItem> selectedItems() {
        List<FileItem> out = new ArrayList<>();
        for (FileItem f : items) if (selection.contains(f.uri.toString())) out.add(f);
        return out;
    }

    // ================================================================ Búsqueda

    private void openSearch() {
        searchBar.setVisibility(View.VISIBLE);
        searchInput.setText(localQuery == null ? "" : localQuery);
        searchInput.requestFocus();
        InputMethodManager imm = getSystemService(InputMethodManager.class);
        if (imm != null) imm.showSoftInput(searchInput, InputMethodManager.SHOW_IMPLICIT);
        updateChrome();
    }

    private void closeSearch() {
        hideKeyboard();
        searchBar.setVisibility(View.GONE);
        if (localQuery != null) {
            localQuery = null;
            load();
        } else {
            updateChrome();
        }
    }

    private void hideKeyboard() {
        InputMethodManager imm = getSystemService(InputMethodManager.class);
        if (imm != null) imm.hideSoftInputFromWindow(searchInput.getWindowToken(), 0);
    }

    // ================================================================ Menú "más"

    private void showMoreMenu(View anchor) {
        PopupMenu pm = new PopupMenu(this, anchor);
        Menu m = pm.getMenu();
        int sort = prefs.getInt("sort", SORT_NAME);
        boolean asc = prefs.getBoolean("asc", true);
        if (mode == MODE_TREE) m.add(0, 1, 0, R.string.new_folder);
        m.add(0, 2, 1, R.string.select_all);
        m.add(1, 10, 2, R.string.sort_name).setCheckable(true).setChecked(sort == SORT_NAME);
        m.add(1, 11, 3, R.string.sort_date).setCheckable(true).setChecked(sort == SORT_DATE);
        m.add(1, 12, 4, R.string.sort_size).setCheckable(true).setChecked(sort == SORT_SIZE);
        m.setGroupCheckable(1, true, true);
        m.add(2, 13, 5, asc ? R.string.sort_desc : R.string.sort_asc);
        m.add(2, 3, 6, R.string.refresh);
        m.add(3, 20, 7, R.string.show_hidden).setCheckable(true).setChecked(Store.showHidden(this));
        if (mode == MODE_TREE) {
            m.add(3, 21, 8, R.string.analyze_folder);
            m.add(3, 22, 9, R.string.add_current_favorite);
        }
        if (mode == MODE_TRASH) m.add(3, 23, 10, R.string.empty_trash);
        if (mode == MODE_RECENT) m.add(3, 24, 10, R.string.clear_recent);
        pm.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1: askNewFolder(); return true;
                case 2: selectAll(); return true;
                case 3: load(); return true;
                case 10: setSort(SORT_NAME); return true;
                case 11: setSort(SORT_DATE); return true;
                case 12: setSort(SORT_SIZE); return true;
                case 20:
                    Store.setShowHidden(this, !Store.showHidden(this));
                    load();
                    return true;
                case 21:
                    startActivity(AnalyzerActivity.intent(this, tree, currentDocId(),
                            stackNames.get(stackNames.size() - 1)));
                    return true;
                case 22: {
                    FileItem cur = SafOps.query(getContentResolver(), tree, currentDocId(),
                            stackIds.size() > 1 ? stackIds.get(stackIds.size() - 2) : null);
                    if (cur != null && !Store.contains(this, Store.FAVORITES, cur.uri.toString())) {
                        Store.toggleFavorite(this, cur);
                    }
                    Toast.makeText(this, R.string.added_favorite, Toast.LENGTH_SHORT).show();
                    return true;
                }
                case 23:
                    confirmPurge(new ArrayList<>(items));
                    return true;
                case 24:
                    Store.put(this, Store.RECENT, new ArrayList<>());
                    load();
                    return true;
                case 13:
                    prefs.edit().putBoolean("asc", !prefs.getBoolean("asc", true)).apply();
                    resort();
                    return true;
                default: return false;
            }
        });
        pm.show();
    }

    private void setSort(int by) {
        prefs.edit().putInt("sort", by).apply();
        resort();
    }

    private void resort() {
        sort(items);
        refreshAdapters();
    }

    // ================================================================ Abrir y compartir

    private String bestMime(FileItem f) {
        return FileOpener.bestMime(f);
    }

    private void share() {
        List<FileItem> sel = selectedItems();
        if (sel.isEmpty()) return;
        ArrayList<Uri> uris = new ArrayList<>();
        for (FileItem f : sel) uris.add(f.uri);
        Intent i;
        if (uris.size() == 1) {
            i = new Intent(Intent.ACTION_SEND);
            i.setType(bestMime(sel.get(0)));
            i.putExtra(Intent.EXTRA_STREAM, uris.get(0));
        } else {
            i = new Intent(Intent.ACTION_SEND_MULTIPLE);
            i.setType("*/*");
            i.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
        }
        ClipData clip = ClipData.newRawUri("", uris.get(0));
        for (int k = 1; k < uris.size(); k++) clip.addItem(new ClipData.Item(uris.get(k)));
        i.setClipData(clip);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivity(Intent.createChooser(i, getString(R.string.share)));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.no_app, Toast.LENGTH_SHORT).show();
        }
    }

    // ================================================================ Crear y renombrar

    private EditText inputDialogView(AlertDialog.Builder b, String initial) {
        View v = getLayoutInflater().inflate(R.layout.dialog_input, null);
        EditText input = v.findViewById(R.id.input);
        input.setText(initial);
        b.setView(v);
        return input;
    }

    private static boolean validName(String n) {
        return !n.isEmpty() && !n.contains("/") && !n.equals(".") && !n.equals("..");
    }

    private void askNewFolder() {
        AlertDialog.Builder b = new AlertDialog.Builder(this).setTitle(R.string.new_folder);
        EditText input = inputDialogView(b, "");
        input.setHint(R.string.folder_name);
        b.setPositiveButton(R.string.create, (d, w) -> {
            String name = input.getText().toString().trim();
            if (!validName(name)) {
                Toast.makeText(this, R.string.invalid_name, Toast.LENGTH_SHORT).show();
                return;
            }
            final String parent = currentDocId();
            runOp(() -> SafOps.createFolder(getContentResolver(), tree, parent, name), R.string.folder_created);
        });
        b.setNegativeButton(R.string.cancel, null);
        b.show();
        input.requestFocus();
    }

    private void askRename() {
        List<FileItem> sel = selectedItems();
        if (sel.size() != 1) return;
        final FileItem f = sel.get(0);
        AlertDialog.Builder b = new AlertDialog.Builder(this).setTitle(R.string.rename);
        EditText input = inputDialogView(b, f.name);
        int dot = f.name.lastIndexOf('.');
        input.setSelection(0, (dot > 0 && !f.isDir()) ? dot : f.name.length());
        b.setPositiveButton(R.string.rename, (d, w) -> {
            String name = input.getText().toString().trim();
            if (!validName(name)) {
                Toast.makeText(this, R.string.invalid_name, Toast.LENGTH_SHORT).show();
                return;
            }
            if (name.equals(f.name)) return;
            runOp(() -> SafOps.rename(getContentResolver(), f, name), R.string.renamed);
        });
        b.setNegativeButton(R.string.cancel, null);
        b.show();
        input.requestFocus();
    }

    private interface Op {
        void run() throws Exception;
    }

    private void runOp(Op op, int okMessage) {
        io.execute(() -> {
            boolean ok;
            try {
                op.run();
                ok = true;
            } catch (Exception e) {
                ok = false;
            }
            final boolean success = ok;
            main.post(() -> {
                if (isDestroyed()) return;
                Toast.makeText(this, success ? okMessage : R.string.op_failed, Toast.LENGTH_SHORT).show();
                load();
            });
        });
    }

    // ================================================================ Borrar

    private void confirmDelete() {
        final List<FileItem> sel = selectedItems();
        if (sel.isEmpty()) return;
        String what = sel.size() == 1 ? "«" + sel.get(0).name + "»"
                : getResources().getQuantityString(R.plurals.items, sel.size(), sel.size());
        new AlertDialog.Builder(this)
                .setTitle(R.string.delete)
                .setMessage(getString(R.string.delete_to_trash_msg, what))
                .setPositiveButton(R.string.move_to_trash, (d, w) -> delete(sel, false))
                .setNeutralButton(R.string.delete_forever, (d, w) -> confirmPurge(sel))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void confirmPurge(List<FileItem> sel) {
        if (sel.isEmpty()) return;
        String msg = sel.size() == 1
                ? getString(R.string.delete_one, sel.get(0).name)
                : getResources().getQuantityString(R.plurals.delete_many, sel.size(), sel.size());
        new AlertDialog.Builder(this)
                .setTitle(R.string.delete_forever)
                .setMessage(msg)
                .setPositiveButton(R.string.delete, (d, w) -> delete(sel, true))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /**
     * Borra. Sin {@code forever}, los archivos de tus carpetas van a la papelera de MyFile y las
     * fotos/vídeos/música a la papelera del sistema (Android 11+), que pide confirmación.
     */
    @SuppressWarnings("deprecation")
    private void delete(List<FileItem> sel, boolean forever) {
        List<Uri> media = new ArrayList<>();
        List<FileItem> docs = new ArrayList<>();
        for (FileItem f : sel) {
            if (f.isDocument()) docs.add(f);
            else media.add(f.uri);
        }
        if (!media.isEmpty() && Build.VERSION.SDK_INT >= 30) {
            PendingIntent pi = forever
                    ? MediaStore.createDeleteRequest(getContentResolver(), media)
                    : MediaStore.createTrashRequest(getContentResolver(), media, true);
            try {
                startIntentSenderForResult(pi.getIntentSender(), REQ_MEDIA_DELETE, null, 0, 0, 0);
            } catch (Exception e) {
                Toast.makeText(this, R.string.op_failed, Toast.LENGTH_SHORT).show();
            }
            media.clear();
        }
        final List<Uri> legacyMedia = media;
        final boolean inTrashView = mode == MODE_TRASH;
        runOp(() -> {
            int failed = 0;
            for (FileItem f : docs) {
                try {
                    if (forever || inTrashView) {
                        if (!SafOps.delete(getContentResolver(), f)) failed++;
                        Store.remove(this, Store.TRASH, f.uri.toString());
                    } else {
                        moveToTrash(f);
                    }
                    Store.remove(this, Store.FAVORITES, f.uri.toString());
                    Store.remove(this, Store.RECENT, f.uri.toString());
                } catch (Exception e) {
                    failed++;
                }
            }
            for (Uri u : legacyMedia) {
                try {
                    getContentResolver().delete(u, null, null);
                    Store.remove(this, Store.RECENT, u.toString());
                } catch (Exception e) {
                    failed++;
                }
            }
            if (failed > 0) throw new Exception(failed + " fallidos");
        }, forever || inTrashView ? R.string.deleted : R.string.moved_to_trash);
    }

    private void moveToTrash(FileItem f) throws Exception {
        String trashId = SafOps.trashDir(getContentResolver(), f.treeUri);
        Uri moved = SafOps.move(getContentResolver(), f, f.treeUri, trashId, new AtomicBoolean());
        if (moved == null) throw new Exception("move");
        Store.Entry e = new Store.Entry();
        e.uri = moved.toString();
        e.tree = f.treeUri.toString();
        e.doc = android.provider.DocumentsContract.getDocumentId(moved);
        e.parent = f.parentDocId;
        e.trashDir = trashId;
        e.name = f.name;
        e.mime = f.mime;
        e.time = System.currentTimeMillis();
        Store.add(this, Store.TRASH, e, 0);
    }

    private void restoreSelected() {
        final List<FileItem> sel = selectedItems();
        if (sel.isEmpty()) return;
        final List<Store.Entry> entries = Store.get(this, Store.TRASH);
        runOp(() -> {
            int failed = 0;
            for (FileItem f : sel) {
                Store.Entry match = null;
                for (Store.Entry e : entries) if (e.uri.equals(f.uri.toString())) match = e;
                try {
                    String dest = match != null && match.parent != null ? match.parent : SafOps.rootDocId(f.treeUri);
                    if (SafOps.query(getContentResolver(), f.treeUri, dest, null) == null) {
                        dest = SafOps.rootDocId(f.treeUri);
                    }
                    Uri back = SafOps.move(getContentResolver(), f, f.treeUri, dest, new AtomicBoolean());
                    if (back == null) throw new Exception();
                    Store.remove(this, Store.TRASH, f.uri.toString());
                } catch (Exception e) {
                    failed++;
                }
            }
            if (failed > 0) throw new Exception(failed + " fallidos");
        }, R.string.restored);
    }

    // ================================================================ Más acciones (selección)

    private void showSelectionMenu(View anchor) {
        final List<FileItem> sel = selectedItems();
        if (sel.isEmpty()) return;
        boolean allDocs = true;
        for (FileItem f : sel) if (!f.isDocument()) allDocs = false;
        FileItem one = sel.size() == 1 ? sel.get(0) : null;
        PopupMenu pm = new PopupMenu(this, anchor);
        Menu m = pm.getMenu();
        if (one != null) {
            boolean fav = Store.contains(this, Store.FAVORITES, one.uri.toString());
            m.add(0, 1, 0, fav ? R.string.remove_favorite : R.string.add_favorite);
        }
        if (allDocs && compressTarget(sel.get(0)) != null) m.add(0, 2, 1, R.string.compress);
        if (one != null && one.isDocument() && ZipOps.isZip(one)) m.add(0, 3, 2, R.string.extract_here);
        if (one != null && !one.isDir()) m.add(0, 4, 3, R.string.open_with);
        if (one != null) m.add(0, 5, 4, R.string.details);
        pm.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1: {
                    boolean nowFav = Store.toggleFavorite(this, one);
                    Toast.makeText(this, nowFav ? R.string.added_favorite : R.string.removed_favorite,
                            Toast.LENGTH_SHORT).show();
                    clearSelection();
                    if (mode == MODE_FAVORITES) load();
                    return true;
                }
                case 2: askCompress(sel); return true;
                case 3: extract(one); return true;
                case 4: FileOpener.openExternal(this, one); return true;
                case 5: showDetails(); return true;
                default: return false;
            }
        });
        pm.show();
    }

    /** Carpeta donde dejar un ZIP o una extracción: la actual o la del propio archivo. */
    private String compressTarget(FileItem f) {
        if (mode == MODE_TREE && localQuery == null) return currentDocId();
        return f.parentDocId;
    }

    private void askCompress(List<FileItem> sel) {
        final FileItem first = sel.get(0);
        final String dest = compressTarget(first);
        if (dest == null) return;
        String suggested = (sel.size() == 1 ? first.name.replaceFirst("\\.[^.]+$", "") : getString(R.string.archive_default)) + ".zip";
        if (sel.size() == 1 && first.isDir()) suggested = first.name + ".zip";
        AlertDialog.Builder b = new AlertDialog.Builder(this).setTitle(R.string.compress);
        EditText input = inputDialogView(b, suggested);
        b.setPositiveButton(R.string.compress, (d, w) -> {
            String name = input.getText().toString().trim();
            if (!validName(name)) {
                Toast.makeText(this, R.string.invalid_name, Toast.LENGTH_SHORT).show();
                return;
            }
            if (!name.toLowerCase(Locale.ROOT).endsWith(".zip")) name += ".zip";
            final String zipName = name;
            runWithProgress(R.string.compressing,
                    cancel -> ZipOps.compress(getContentResolver(), sel, first.treeUri, dest, zipName, cancel),
                    R.string.compressed);
        });
        b.setNegativeButton(R.string.cancel, null);
        b.show();
    }

    private void extract(FileItem zip) {
        final String dest = compressTarget(zip);
        if (dest == null) return;
        runWithProgress(R.string.extracting,
                cancel -> ZipOps.extract(getContentResolver(), zip, zip.treeUri, dest, cancel),
                R.string.extracted);
    }

    private interface CancellableOp {
        void run(AtomicBoolean cancel) throws Exception;
    }

    private void runWithProgress(int titleRes, CancellableOp op, int okMessage) {
        clearSelection();
        final AtomicBoolean cancel = new AtomicBoolean();
        final AlertDialog progress = new AlertDialog.Builder(this)
                .setTitle(titleRes)
                .setMessage(R.string.please_wait)
                .setCancelable(false)
                .setNegativeButton(R.string.cancel, (d, w) -> cancel.set(true))
                .show();
        io.execute(() -> {
            boolean ok;
            try {
                op.run(cancel);
                ok = true;
            } catch (Exception e) {
                ok = false;
            }
            final boolean success = ok;
            main.post(() -> {
                if (isDestroyed()) return;
                progress.dismiss();
                int msg = cancel.get() ? R.string.cancelled : (success ? okMessage : R.string.op_failed);
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
                load();
            });
        });
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_MEDIA_DELETE) load();
    }

    // ================================================================ Copiar / mover

    private void toClipboard(boolean cut) {
        List<FileItem> sel = selectedItems();
        if (sel.isEmpty()) return;
        Clipboard.set(sel, cut);
        clearSelection();
        Toast.makeText(this, R.string.clipboard_hint, Toast.LENGTH_LONG).show();
    }

    private void paste() {
        final List<FileItem> src = Clipboard.items();
        final boolean cut = Clipboard.isCut();
        final String destId = currentDocId();
        final Uri destTree = tree;
        for (FileItem f : src) {
            if (SafOps.isInside(f, destTree, destId)) {
                Toast.makeText(this, R.string.paste_inside, Toast.LENGTH_LONG).show();
                return;
            }
        }
        final AtomicBoolean cancel = new AtomicBoolean();
        final AlertDialog progress = new AlertDialog.Builder(this)
                .setTitle(cut ? R.string.moving : R.string.copying)
                .setMessage(R.string.please_wait)
                .setCancelable(false)
                .setNegativeButton(R.string.cancel, (d, w) -> cancel.set(true))
                .show();
        io.execute(() -> {
            int failed = 0;
            for (FileItem f : src) {
                if (cancel.get()) break;
                try {
                    if (cut) SafOps.move(getContentResolver(), f, destTree, destId, cancel);
                    else SafOps.copy(getContentResolver(), f, destTree, destId, cancel);
                } catch (Exception e) {
                    failed++;
                }
            }
            final int fails = failed;
            main.post(() -> {
                if (isDestroyed()) return;
                progress.dismiss();
                if (cut && !cancel.get()) Clipboard.clear();
                int msg = cancel.get() ? R.string.cancelled : (fails == 0 ? R.string.paste_done : R.string.op_failed);
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
                load();
            });
        });
    }

    // ================================================================ Detalles

    private void showDetails() {
        List<FileItem> sel = selectedItems();
        if (sel.size() != 1) return;
        FileItem f = sel.get(0);
        StringBuilder sb = new StringBuilder();
        line(sb, R.string.d_name, f.name);
        line(sb, R.string.d_type, f.isDir() ? getString(R.string.folder) : bestMime(f));
        if (!f.isDir() && f.size >= 0) {
            line(sb, R.string.d_size, Formatter.formatFileSize(this, f.size)
                    + String.format(Locale.getDefault(), " (%,d bytes)", f.size));
        }
        if (f.modified > 0) {
            Date d = new Date(f.modified);
            line(sb, R.string.d_modified, DateFormat.getLongDateFormat(this).format(d) + " "
                    + DateFormat.getTimeFormat(this).format(d));
        }
        if (f.isDocument() && f.docId != null) {
            line(sb, R.string.d_location, f.docId.replaceFirst("^primary:", getString(R.string.internal_short) + "/"));
        }
        final String base = sb.toString().trim();
        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.details)
                .setMessage(f.isDir() && f.isDocument() ? base + "\n\n" + getString(R.string.calculating) : base)
                .setPositiveButton(android.R.string.ok, null)
                .show();
        if (f.isDir() && f.isDocument()) {
            final AtomicBoolean cancel = new AtomicBoolean();
            dialog.setOnDismissListener(d -> cancel.set(true));
            new Thread(() -> {
                long[] r = SafOps.sizeOf(getContentResolver(), f.treeUri, f.docId, cancel);
                main.post(() -> {
                    if (cancel.get() || !dialog.isShowing()) return;
                    dialog.setMessage(base + "\n\n" + getString(R.string.d_size) + ": "
                            + Formatter.formatFileSize(this, r[0]) + "\n"
                            + getResources().getQuantityString(R.plurals.files, (int) r[1], (int) r[1]));
                });
            }).start();
        }
    }

    private void line(StringBuilder sb, int label, String value) {
        sb.append(getString(label)).append(": ").append(value).append("\n\n");
    }
}
