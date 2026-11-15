package com.myfile.app;

import android.app.Activity;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Visor de fotos integrado: zoom, deslizar entre fotos de la carpeta y compartir. */
public class ImageViewerActivity extends Activity {

    private static final String EXTRA_URIS = "uris";
    private static final String EXTRA_NAMES = "names";
    private static final String EXTRA_INDEX = "index";

    public static Intent intent(Context ctx, ArrayList<Uri> uris, ArrayList<String> names, int index) {
        return new Intent(ctx, ImageViewerActivity.class)
                .putParcelableArrayListExtra(EXTRA_URIS, uris)
                .putStringArrayListExtra(EXTRA_NAMES, names)
                .putExtra(EXTRA_INDEX, index);
    }

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private ArrayList<Uri> uris;
    private ArrayList<String> names;
    private int index;
    private int generation;
    private ZoomImageView image;
    private TextView title;
    private TextView counter;
    private View bar;
    private View loading;

    @Override
    @SuppressWarnings("deprecation")
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_viewer);
        uris = getIntent().getParcelableArrayListExtra(EXTRA_URIS);
        names = getIntent().getStringArrayListExtra(EXTRA_NAMES);
        index = savedInstanceState != null ? savedInstanceState.getInt(EXTRA_INDEX)
                : getIntent().getIntExtra(EXTRA_INDEX, 0);
        if (uris == null || uris.isEmpty() || names == null) { finish(); return; }

        image = findViewById(R.id.image);
        title = findViewById(R.id.tv_title);
        counter = findViewById(R.id.tv_counter);
        bar = findViewById(R.id.viewer_bar);
        loading = findViewById(R.id.loading);
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        findViewById(R.id.btn_share).setOnClickListener(v -> share());
        findViewById(R.id.btn_open_with).setOnClickListener(v -> openWith());
        image.setListener(new ZoomImageView.Listener() {
            @Override
            public void onSwipe(int direction) {
                int next = index + direction;
                if (next >= 0 && next < uris.size()) {
                    index = next;
                    show();
                }
            }

            @Override
            public void onTap() {
                bar.setVisibility(bar.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
            }
        });
        show();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt(EXTRA_INDEX, index);
    }

    @Override
    protected void onDestroy() {
        io.shutdownNow();
        super.onDestroy();
    }

    private void show() {
        final Uri uri = uris.get(index);
        title.setText(names.get(index));
        counter.setText((index + 1) + " / " + uris.size());
        loading.setVisibility(View.VISIBLE);
        final int gen = ++generation;
        DisplayMetrics dm = getResources().getDisplayMetrics();
        final int max = Math.max(dm.widthPixels, dm.heightPixels);
        io.execute(() -> {
            Bitmap bm = null;
            try {
                bm = decode(uri, max);
            } catch (Throwable ignored) {
            }
            final Bitmap result = bm;
            main.post(() -> {
                if (gen != generation || isDestroyed()) return;
                loading.setVisibility(View.GONE);
                if (result == null) {
                    Toast.makeText(this, R.string.load_error, Toast.LENGTH_SHORT).show();
                    image.setImageDrawable(null);
                } else {
                    image.setImageBitmap(result);
                }
            });
        });
    }

    private Bitmap decode(Uri uri, int max) throws Exception {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            BitmapFactory.decodeStream(in, null, o);
        }
        int sample = 1;
        while (o.outWidth / (sample * 2) >= max || o.outHeight / (sample * 2) >= max) sample *= 2;
        BitmapFactory.Options o2 = new BitmapFactory.Options();
        o2.inSampleSize = sample;
        Bitmap bm;
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            bm = BitmapFactory.decodeStream(in, null, o2);
        }
        if (bm == null) return null;
        int rotation = 0;
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in != null) {
                int orient = new ExifInterface(in).getAttributeInt(ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL);
                if (orient == ExifInterface.ORIENTATION_ROTATE_90) rotation = 90;
                else if (orient == ExifInterface.ORIENTATION_ROTATE_180) rotation = 180;
                else if (orient == ExifInterface.ORIENTATION_ROTATE_270) rotation = 270;
            }
        } catch (Exception ignored) {
        }
        if (rotation != 0) {
            Matrix m = new Matrix();
            m.postRotate(rotation);
            bm = Bitmap.createBitmap(bm, 0, 0, bm.getWidth(), bm.getHeight(), m, true);
        }
        return bm;
    }

    private void share() {
        Uri uri = uris.get(index);
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("image/*");
        i.putExtra(Intent.EXTRA_STREAM, uri);
        i.setClipData(ClipData.newRawUri("", uri));
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivity(Intent.createChooser(i, getString(R.string.share)));
        } catch (Exception e) {
            Toast.makeText(this, R.string.no_app, Toast.LENGTH_SHORT).show();
        }
    }

    private void openWith() {
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setDataAndType(uris.get(index), "image/*");
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivity(Intent.createChooser(i, getString(R.string.open_with)));
        } catch (Exception e) {
            Toast.makeText(this, R.string.no_app, Toast.LENGTH_SHORT).show();
        }
    }
}
