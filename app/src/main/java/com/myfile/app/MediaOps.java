package com.myfile.app;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;

import java.util.ArrayList;
import java.util.List;

/** Lectura de fotos, vídeos y música desde la galería del sistema (solo lectura). */
public final class MediaOps {
    private MediaOps() {}

    public static List<FileItem> query(ContentResolver cr, Category cat) {
        List<FileItem> out = new ArrayList<>();
        Uri coll = cat.collection();
        if (coll == null) return out;
        String[] projection = {
                MediaStore.MediaColumns._ID,
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.MIME_TYPE,
                MediaStore.MediaColumns.SIZE,
                MediaStore.MediaColumns.DATE_MODIFIED,
        };
        String order = MediaStore.MediaColumns.DATE_MODIFIED + " DESC";
        try (Cursor c = cr.query(coll, projection, null, null, order)) {
            if (c == null) return out;
            while (c.moveToNext()) {
                long id = c.getLong(0);
                out.add(new FileItem(ContentUris.withAppendedId(coll, id), null, null, null,
                        c.getString(1), c.getString(2),
                        c.isNull(3) ? -1 : c.getLong(3),
                        c.isNull(4) ? 0 : c.getLong(4) * 1000L, 0));
            }
        }
        return out;
    }
}
