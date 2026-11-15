package com.myfile.app;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.util.LruCache;
import android.util.Size;
import android.widget.ImageView;

import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Miniaturas de fotos y vídeos, generadas en local y guardadas solo en memoria. */
public final class ThumbLoader {
    private final ContentResolver cr;
    private final ExecutorService pool = Executors.newFixedThreadPool(2);
    private final Handler main = new Handler(Looper.getMainLooper());
    private final LruCache<String, Bitmap> cache = new LruCache<String, Bitmap>(8 * 1024 * 1024) {
        @Override
        protected int sizeOf(String key, Bitmap value) {
            return value.getByteCount();
        }
    };

    public ThumbLoader(ContentResolver cr) {
        this.cr = cr;
    }

    public void load(ImageView view, FileItem f, int px) {
        final String key = f.uri.toString();
        view.setTag(key);
        Bitmap cached = cache.get(key);
        if (cached != null) {
            view.setImageBitmap(cached);
            return;
        }
        pool.execute(() -> {
            Bitmap bm = null;
            try {
                bm = create(f, px);
            } catch (Throwable ignored) {
            }
            if (bm == null) return;
            cache.put(key, bm);
            final Bitmap result = bm;
            main.post(() -> {
                if (key.equals(view.getTag())) view.setImageBitmap(result);
            });
        });
    }

    @SuppressWarnings("deprecation")
    private Bitmap create(FileItem f, int px) throws Exception {
        if (!f.isDocument()) {
            if (Build.VERSION.SDK_INT >= 29) return cr.loadThumbnail(f.uri, new Size(px, px), null);
            long id = ContentUris.parseId(f.uri);
            if (FileTypes.isVideo(f)) {
                return MediaStore.Video.Thumbnails.getThumbnail(cr, id, MediaStore.Video.Thumbnails.MINI_KIND, null);
            }
            return MediaStore.Images.Thumbnails.getThumbnail(cr, id, MediaStore.Images.Thumbnails.MINI_KIND, null);
        }
        if (f.has(DocumentsContract.Document.FLAG_SUPPORTS_THUMBNAIL)) {
            return DocumentsContract.getDocumentThumbnail(cr, f.uri, new Point(px, px), null);
        }
        if (FileTypes.isImage(f) && f.size > 0 && f.size < 30L * 1024 * 1024) {
            return decodeSampled(f, px);
        }
        return null;
    }

    private Bitmap decodeSampled(FileItem f, int px) throws Exception {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        try (InputStream in = cr.openInputStream(f.uri)) {
            BitmapFactory.decodeStream(in, null, o);
        }
        int sample = 1;
        while (o.outWidth / (sample * 2) >= px && o.outHeight / (sample * 2) >= px) sample *= 2;
        o = new BitmapFactory.Options();
        o.inSampleSize = sample;
        try (InputStream in = cr.openInputStream(f.uri)) {
            return BitmapFactory.decodeStream(in, null, o);
        }
    }

    public void shutdown() {
        pool.shutdownNow();
        cache.evictAll();
    }
}
