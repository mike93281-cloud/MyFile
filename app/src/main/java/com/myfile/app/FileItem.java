package com.myfile.app;

import android.net.Uri;
import android.provider.DocumentsContract;

/** Un archivo o carpeta, venga del selector de carpetas (SAF) o de la galería (MediaStore). */
public final class FileItem {
    public final Uri uri;
    /** Carpeta raíz concedida por el usuario; null si viene de MediaStore. */
    public final Uri treeUri;
    public final String docId;
    public final String parentDocId;
    public final String name;
    public final String mime;
    public final long size;
    public final long modified;
    public final int flags;

    public FileItem(Uri uri, Uri treeUri, String docId, String parentDocId, String name,
                    String mime, long size, long modified, int flags) {
        this.uri = uri;
        this.treeUri = treeUri;
        this.docId = docId;
        this.parentDocId = parentDocId;
        this.name = name == null ? "" : name;
        this.mime = mime == null ? "application/octet-stream" : mime;
        this.size = size;
        this.modified = modified;
        this.flags = flags;
    }

    public boolean isDir() {
        return DocumentsContract.Document.MIME_TYPE_DIR.equals(mime);
    }

    /** true si el archivo está en una carpeta concedida (se puede copiar, mover, renombrar). */
    public boolean isDocument() {
        return treeUri != null;
    }

    public boolean has(int flag) {
        return (flags & flag) != 0;
    }

    public String extension() {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(java.util.Locale.ROOT);
    }
}
