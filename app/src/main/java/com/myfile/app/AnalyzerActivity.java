package com.myfile.app;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.text.format.Formatter;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** Analizador: qué ocupa más dentro de una carpeta, ordenado de mayor a menor. */
public class AnalyzerActivity extends Activity {

    private static final String EXTRA_TREE = "tree";
    private static final String EXTRA_DOC = "doc";
    private static final String EXTRA_NAME = "name";

    public static Intent intent(Context ctx, Uri tree, String docId, String name) {
        return new Intent(ctx, AnalyzerActivity.class).putExtra(EXTRA_TREE, tree)
                .putExtra(EXTRA_DOC, docId).putExtra(EXTRA_NAME, name);
    }

    private static final class Row {
        final FileItem item;
        long size = -1;
        long files;

        Row(FileItem item) {
            this.item = item;
            if (!item.isDir()) {
                size = Math.max(0, item.size);
                files = 1;
            }
        }
    }

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Map<String, long[]> cache = new HashMap<>();
    private final ArrayList<String> stackIds = new ArrayList<>();
    private final ArrayList<String> stackNames = new ArrayList<>();
    private final List<Row> rows = new ArrayList<>();
    private Uri tree;
    private AtomicBoolean cancel = new AtomicBoolean();
    private int generation;
    private TextView title, subtitle, total;
    private View loading;
    private RowAdapter adapter;

    @Override
    @SuppressWarnings("deprecation")
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_analyzer);
        tree = getIntent().getParcelableExtra(EXTRA_TREE);
        if (tree == null) { finish(); return; }
        String doc = getIntent().getStringExtra(EXTRA_DOC);
        String name = getIntent().getStringExtra(EXTRA_NAME);
        stackIds.add(doc != null ? doc : SafOps.rootDocId(tree));
        stackNames.add(name != null ? name : "");

        title = findViewById(R.id.tv_title);
        subtitle = findViewById(R.id.tv_subtitle);
        total = findViewById(R.id.tv_total);
        loading = findViewById(R.id.loading);
        findViewById(R.id.btn_back).setOnClickListener(v -> onBackPressed());
        ListView list = findViewById(R.id.list);
        adapter = new RowAdapter();
        list.setAdapter(adapter);
        list.setOnItemClickListener((p, v, pos, id) -> {
            Row r = rows.get(pos);
            if (r.item.isDir()) {
                stackIds.add(r.item.docId);
                stackNames.add(r.item.name);
                load();
            }
        });
        load();
    }

    @Override
    protected void onDestroy() {
        cancel.set(true);
        io.shutdownNow();
        super.onDestroy();
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (stackIds.size() > 1) {
            stackIds.remove(stackIds.size() - 1);
            stackNames.remove(stackNames.size() - 1);
            load();
        } else {
            super.onBackPressed();
        }
    }

    private void load() {
        cancel.set(true);
        final AtomicBoolean c = new AtomicBoolean();
        cancel = c;
        final int gen = ++generation;
        final String docId = stackIds.get(stackIds.size() - 1);
        title.setText(getString(R.string.analyzer));
        subtitle.setText(TextUtils.join(" / ", stackNames));
        total.setText(R.string.calculating);
        rows.clear();
        adapter.notifyDataSetChanged();
        loading.setVisibility(View.VISIBLE);

        io.execute(() -> {
            List<FileItem> children;
            try {
                children = SafOps.list(getContentResolver(), tree, docId);
            } catch (Exception e) {
                children = new ArrayList<>();
            }
            final List<Row> result = new ArrayList<>();
            for (FileItem f : children) {
                if (f.isDir() && SafOps.TRASH_NAME.equals(f.name)) continue;
                result.add(new Row(f));
            }
            post(gen, result, false);
            for (Row r : result) {
                if (c.get()) return;
                if (!r.item.isDir()) continue;
                long[] s = cache.get(r.item.docId);
                if (s == null) {
                    s = SafOps.sizeOf(getContentResolver(), tree, r.item.docId, c);
                    if (c.get()) return;
                    cache.put(r.item.docId, s);
                }
                r.size = s[0];
                r.files = s[1];
                post(gen, result, false);
            }
            post(gen, result, true);
        });
    }

    private void post(int gen, List<Row> result, boolean done) {
        final List<Row> snapshot = new ArrayList<>(result);
        Collections.sort(snapshot, (a, b) -> Long.compare(b.size, a.size));
        main.post(() -> {
            if (gen != generation || isDestroyed()) return;
            rows.clear();
            rows.addAll(snapshot);
            adapter.notifyDataSetChanged();
            long sum = 0, files = 0;
            for (Row r : rows) {
                sum += Math.max(0, r.size);
                files += r.files;
            }
            String totalText = Formatter.formatShortFileSize(this, sum) + "  ·  "
                    + getResources().getQuantityString(R.plurals.files, (int) Math.min(files, Integer.MAX_VALUE),
                    (int) Math.min(files, Integer.MAX_VALUE));
            total.setText(done ? totalText : getString(R.string.calculating_partial, totalText));
            if (done) loading.setVisibility(View.GONE);
        });
    }

    private long totalSize() {
        long sum = 0;
        for (Row r : rows) sum += Math.max(0, r.size);
        return sum;
    }

    private final class RowAdapter extends BaseAdapter {
        @Override public int getCount() { return rows.size(); }
        @Override public Object getItem(int i) { return rows.get(i); }
        @Override public long getItemId(int i) { return i; }

        @Override
        public View getView(int pos, View v, ViewGroup parent) {
            if (v == null) v = getLayoutInflater().inflate(R.layout.item_analyzer, parent, false);
            Row r = rows.get(pos);
            ((ImageView) v.findViewById(R.id.icon)).setImageResource(FileTypes.iconFor(r.item));
            ((TextView) v.findViewById(R.id.name)).setText(r.item.name);
            long sum = totalSize();
            int pct = (sum > 0 && r.size > 0) ? (int) (r.size * 100 / sum) : 0;
            ((TextView) v.findViewById(R.id.size)).setText(r.size < 0
                    ? getString(R.string.calculating)
                    : Formatter.formatShortFileSize(AnalyzerActivity.this, r.size) + "  ·  " + pct + "%");
            ((ProgressBar) v.findViewById(R.id.bar)).setProgress(pct);
            return v;
        }
    }
}
