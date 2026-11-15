package com.myfile.app;

import android.content.ContentResolver;
import android.content.UriPermission;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.provider.DocumentsContract.Document;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Operaciones sobre las carpetas que el usuario concedió con el selector del sistema.
 * La app nunca ve nada fuera de esas carpetas.
 */
public final class SafOps {
    private SafOps() {}

    private static final String[] PROJECTION = {
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_SIZE,
            Document.COLUMN_LAST_MODIFIED,
            Document.COLUMN_FLAGS,
    };

    /** Carpeta oculta (en la raíz de cada carpeta concedida) que hace de papelera. */
    public static final String TRASH_NAME = ".MyFile-Papelera";

    public interface Filter {
        boolean accept(FileItem f);
    }

    /** Carpetas concedidas que siguen siendo válidas. */
    public static List<Uri> grantedTrees(ContentResolver cr) {
        List<Uri> out = new ArrayList<>();
        for (UriPermission p : cr.getPersistedUriPermissions()) {
            if (p.isReadPermission() && DocumentsContract.isTreeUri(p.getUri())) {
                out.add(p.getUri());
            }
        }
        return out;
    }

    public static String rootDocId(Uri treeUri) {
        return DocumentsContract.getTreeDocumentId(treeUri);
    }

    /** Datos de un documento concreto (por ejemplo la raíz de una carpeta concedida). */
    public static FileItem query(ContentResolver cr, Uri treeUri, String docId, String parentDocId) {
        Uri uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId);
        try (Cursor c = cr.query(uri, PROJECTION, null, null, null)) {
            if (c != null && c.moveToFirst()) return fromCursor(c, treeUri, parentDocId);
        } catch (Exception ignored) {
        }
        return null;
    }

    public static List<FileItem> list(ContentResolver cr, Uri treeUri, String parentDocId) {
        List<FileItem> out = new ArrayList<>();
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId);
        try (Cursor c = cr.query(children, PROJECTION, null, null, null)) {
            if (c != null) {
                while (c.moveToNext()) out.add(fromCursor(c, treeUri, parentDocId));
            }
        }
        return out;
    }

    /** Búsqueda recursiva. Se detiene al llegar a {@code limit} resultados o si se cancela. */
    public static void search(ContentResolver cr, Uri treeUri, String docId, Filter filter,
                              List<FileItem> out, int limit, AtomicBoolean cancel) {
        List<FileItem> children;
        try {
            children = list(cr, treeUri, docId);
        } catch (Exception e) {
            return;
        }
        for (FileItem f : children) {
            if (cancel.get() || out.size() >= limit) return;
            if (f.isDir() && TRASH_NAME.equals(f.name)) continue;
            if (filter.accept(f)) out.add(f);
            if (f.isDir()) search(cr, treeUri, f.docId, filter, out, limit, cancel);
        }
    }

    public static Filter nameContains(String query) {
        final String q = query.toLowerCase(Locale.getDefault());
        return f -> f.name.toLowerCase(Locale.getDefault()).contains(q);
    }

    public static Uri createFolder(ContentResolver cr, Uri treeUri, String parentDocId, String name)
            throws FileNotFoundException {
        Uri parent = DocumentsContract.buildDocumentUriUsingTree(treeUri, parentDocId);
        return DocumentsContract.createDocument(cr, parent, Document.MIME_TYPE_DIR, name);
    }

    public static Uri rename(ContentResolver cr, FileItem f, String newName) throws FileNotFoundException {
        return DocumentsContract.renameDocument(cr, f.uri, newName);
    }

    public static boolean delete(ContentResolver cr, FileItem f) throws FileNotFoundException {
        return DocumentsContract.deleteDocument(cr, f.uri);
    }

    /** ¿Destino dentro del propio origen? (no se puede copiar una carpeta dentro de sí misma) */
    public static boolean isInside(FileItem src, Uri destTree, String destDocId) {
        if (!src.isDir() || src.treeUri == null) return false;
        if (!src.treeUri.getAuthority().equals(destTree.getAuthority())) return false;
        return destDocId.equals(src.docId) || destDocId.startsWith(src.docId + "/");
    }

    public static Uri copy(ContentResolver cr, FileItem src, Uri destTree, String destDocId,
                           AtomicBoolean cancel) throws IOException {
        if (cancel.get()) return null;
        Uri destParent = DocumentsContract.buildDocumentUriUsingTree(destTree, destDocId);
        if (src.isDir()) {
            Uri newDir = DocumentsContract.createDocument(cr, destParent, Document.MIME_TYPE_DIR, src.name);
            if (newDir == null) throw new IOException("No se pudo crear " + src.name);
            String newDirId = DocumentsContract.getDocumentId(newDir);
            for (FileItem child : list(cr, src.treeUri, src.docId)) {
                copy(cr, child, destTree, newDirId, cancel);
            }
            return newDir;
        }
        Uri target = DocumentsContract.createDocument(cr, destParent, src.mime, src.name);
        if (target == null) throw new IOException("No se pudo crear " + src.name);
        try (InputStream in = cr.openInputStream(src.uri);
             OutputStream out = cr.openOutputStream(target, "w")) {
            if (in == null || out == null) throw new IOException("No se pudo abrir " + src.name);
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) {
                if (cancel.get()) break;
                out.write(buf, 0, n);
            }
        }
        if (cancel.get()) {
            try { DocumentsContract.deleteDocument(cr, target); } catch (Exception ignored) { }
            return null;
        }
        return target;
    }

    /** Mueve y devuelve la nueva Uri (o null si se canceló). */
    public static Uri move(ContentResolver cr, FileItem src, Uri destTree, String destDocId,
                           AtomicBoolean cancel) throws IOException {
        boolean sameProvider = src.treeUri.getAuthority().equals(destTree.getAuthority());
        if (sameProvider && src.parentDocId != null && src.has(Document.FLAG_SUPPORTS_MOVE)) {
            try {
                Uri srcParent = DocumentsContract.buildDocumentUriUsingTree(src.treeUri, src.parentDocId);
                Uri destParent = DocumentsContract.buildDocumentUriUsingTree(destTree, destDocId);
                Uri moved = DocumentsContract.moveDocument(cr, src.uri, srcParent, destParent);
                if (moved != null) return moved;
            } catch (Exception ignored) {
                // Si el proveedor no lo permite, se copia y se borra.
            }
        }
        Uri copied = copy(cr, src, destTree, destDocId, cancel);
        if (copied == null || cancel.get()) return null;
        DocumentsContract.deleteDocument(cr, src.uri);
        return copied;
    }

    /** Id de la carpeta papelera de esta carpeta concedida; la crea si no existe. */
    public static String trashDir(ContentResolver cr, Uri treeUri) throws IOException {
        String root = rootDocId(treeUri);
        for (FileItem f : list(cr, treeUri, root)) {
            if (f.isDir() && TRASH_NAME.equals(f.name)) return f.docId;
        }
        Uri created = createFolder(cr, treeUri, root, TRASH_NAME);
        if (created == null) throw new IOException("No se pudo crear la papelera");
        return DocumentsContract.getDocumentId(created);
    }

    /** Tamaño total y número de archivos de una carpeta (recursivo). */
    public static long[] sizeOf(ContentResolver cr, Uri treeUri, String docId, AtomicBoolean cancel) {
        long[] acc = new long[2];
        sizeOf(cr, treeUri, docId, cancel, acc);
        return acc;
    }

    private static void sizeOf(ContentResolver cr, Uri treeUri, String docId, AtomicBoolean cancel, long[] acc) {
        List<FileItem> children;
        try {
            children = list(cr, treeUri, docId);
        } catch (Exception e) {
            return;
        }
        for (FileItem f : children) {
            if (cancel.get()) return;
            if (f.isDir()) {
                sizeOf(cr, treeUri, f.docId, cancel, acc);
            } else {
                acc[0] += Math.max(0, f.size);
                acc[1]++;
            }
        }
    }

    private static FileItem fromCursor(Cursor c, Uri treeUri, String parentDocId) {
        String id = c.getString(0);
        return new FileItem(
                DocumentsContract.buildDocumentUriUsingTree(treeUri, id),
                treeUri, id, parentDocId,
                c.getString(1), c.getString(2),
                c.isNull(3) ? -1 : c.getLong(3),
                c.isNull(4) ? 0 : c.getLong(4),
                c.isNull(5) ? 0 : c.getInt(5));
    }
}
