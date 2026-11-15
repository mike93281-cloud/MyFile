package com.myfile.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Editor de texto integrado (archivos de hasta 2 MB; los mayores se abren en solo lectura). */
public class EditorActivity extends Activity {

    private static final String EXTRA_NAME = "name";
    private static final String EXTRA_WRITABLE = "writable";
    private static final int MAX_EDIT = 2 * 1024 * 1024;
    private static final int MAX_READ = 8 * 1024 * 1024;

    public static Intent intent(Context ctx, Uri uri, String name, boolean writable) {
        return new Intent(ctx, EditorActivity.class).setData(uri)
                .putExtra(EXTRA_NAME, name).putExtra(EXTRA_WRITABLE, writable);
    }

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private Uri uri;
    private EditText text;
    private View save;
    private View loading;
    private boolean writable;
    private boolean dirty;
    private boolean loaded;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_editor);
        uri = getIntent().getData();
        if (uri == null) { finish(); return; }
        writable = getIntent().getBooleanExtra(EXTRA_WRITABLE, false);

        ((TextView) findViewById(R.id.tv_title)).setText(getIntent().getStringExtra(EXTRA_NAME));
        text = findViewById(R.id.editor);
        save = findViewById(R.id.btn_save);
        loading = findViewById(R.id.loading);
        findViewById(R.id.btn_back).setOnClickListener(v -> onBackPressed());
        save.setOnClickListener(v -> save());
        save.setVisibility(View.GONE);
        text.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) {
                if (loaded) dirty = true;
            }
        });
        if (savedInstanceState == null) load();
        else { loaded = true; applyWritable(); }
    }

    @Override
    protected void onDestroy() {
        io.shutdown(); // deja terminar un guardado pendiente
        super.onDestroy();
    }

    private void applyWritable() {
        text.setFocusable(writable);
        text.setFocusableInTouchMode(writable);
        text.setTextIsSelectable(true);
        save.setVisibility(writable ? View.VISIBLE : View.GONE);
    }

    private void load() {
        loading.setVisibility(View.VISIBLE);
        io.execute(() -> {
            String content = null;
            boolean tooBig = false;
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                if (in != null) {
                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    byte[] buf = new byte[64 * 1024];
                    int n;
                    while ((n = in.read(buf)) > 0) {
                        bos.write(buf, 0, Math.min(n, MAX_READ - bos.size()));
                        if (bos.size() >= MAX_READ) break;
                    }
                    tooBig = bos.size() > MAX_EDIT;
                    content = new String(bos.toByteArray(), StandardCharsets.UTF_8);
                }
            } catch (Exception ignored) {
            }
            final String result = content;
            final boolean big = tooBig;
            main.post(() -> {
                if (isDestroyed()) return;
                loading.setVisibility(View.GONE);
                if (result == null) {
                    Toast.makeText(this, R.string.load_error, Toast.LENGTH_SHORT).show();
                    finish();
                    return;
                }
                if (big) {
                    writable = false;
                    Toast.makeText(this, R.string.editor_read_only, Toast.LENGTH_LONG).show();
                }
                text.setText(result);
                loaded = true;
                dirty = false;
                applyWritable();
            });
        });
    }

    private void save() {
        final String content = text.getText().toString();
        io.execute(() -> {
            boolean ok;
            try (OutputStream out = getContentResolver().openOutputStream(uri, "wt")) {
                if (out == null) throw new Exception();
                out.write(content.getBytes(StandardCharsets.UTF_8));
                ok = true;
            } catch (Exception e) {
                ok = false;
            }
            final boolean success = ok;
            main.post(() -> {
                if (success) dirty = false;
                Toast.makeText(this, success ? R.string.saved : R.string.op_failed, Toast.LENGTH_SHORT).show();
            });
        });
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (dirty && writable) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.unsaved_title)
                    .setMessage(R.string.unsaved_msg)
                    .setPositiveButton(R.string.save, (d, w) -> { save(); finish(); })
                    .setNegativeButton(R.string.discard, (d, w) -> finish())
                    .setNeutralButton(R.string.cancel, null)
                    .show();
            return;
        }
        super.onBackPressed();
    }
}
