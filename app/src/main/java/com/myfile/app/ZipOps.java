package com.myfile.app;

import android.content.ContentResolver;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.webkit.MimeTypeMap;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/** Comprimir y descomprimir ZIP sin librerías externas (java.util.zip). */
public final class ZipOps {
    private ZipOps() {}

    public static boolean isZip(FileItem f) {
        return "zip".equals(f.extension()) || "application/zip".equals(f.mime);
    }

    public static Uri compress(ContentResolver cr, List<FileItem> src, Uri tree, String destDocId,
                               String zipName, AtomicBoolean cancel) throws IOException {
        Uri parent = DocumentsContract.buildDocumentUriUsingTree(tree, destDocId);
        Uri zip = DocumentsContract.createDocument(cr, parent, "application/zip", zipName);
        if (zip == null) throw new IOException("No se pudo crear " + zipName);
        try (OutputStream os = cr.openOutputStream(zip, "w")) {
            if (os == null) throw new IOException("No se pudo escribir " + zipName);
            try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(os))) {
                for (FileItem f : src) add(cr, zos, f, "", cancel);
            }
        } catch (IOException e) {
            try { DocumentsContract.deleteDocument(cr, zip); } catch (Exception ignored) { }
            throw e;
        }
        if (cancel.get()) {
            try { DocumentsContract.deleteDocument(cr, zip); } catch (Exception ignored) { }
            return null;
        }
        return zip;
    }

    private static void add(ContentResolver cr, ZipOutputStream zos, FileItem f, String prefix,
                            AtomicBoolean cancel) throws IOException {
        if (cancel.get()) return;
        String path = prefix + f.name;
        if (f.isDir()) {
            ZipEntry dir = new ZipEntry(path + "/");
            if (f.modified > 0) dir.setTime(f.modified);
            zos.putNextEntry(dir);
            zos.closeEntry();
            for (FileItem child : SafOps.list(cr, f.treeUri, f.docId)) add(cr, zos, child, path + "/", cancel);
            return;
        }
        ZipEntry e = new ZipEntry(path);
        if (f.modified > 0) e.setTime(f.modified);
        zos.putNextEntry(e);
        try (InputStream in = cr.openInputStream(f.uri)) {
            if (in == null) throw new IOException("No se pudo leer " + f.name);
            pipe(in, zos, cancel);
        }
        zos.closeEntry();
    }

    /** Extrae en una carpeta nueva con el nombre del ZIP. Devuelve la Uri de esa carpeta. */
    public static Uri extract(ContentResolver cr, FileItem zip, Uri tree, String destParentDocId,
                              AtomicBoolean cancel) throws IOException {
        String base = zip.name.replaceFirst("(?i)\\.zip$", "");
        if (base.isEmpty()) base = "zip";
        Uri dir = SafOps.createFolder(cr, tree, destParentDocId, base);
        if (dir == null) throw new IOException("No se pudo crear la carpeta");
        String rootId = DocumentsContract.getDocumentId(dir);
        try {
            extractWith(cr, zip.uri, tree, rootId, StandardCharsets.UTF_8, cancel);
        } catch (IllegalArgumentException badNames) {
            // ZIP antiguos (Windows) usan otra codificación para los nombres.
            extractWith(cr, zip.uri, tree, rootId, Charset.forName("IBM437"), cancel);
        }
        return dir;
    }

    private static void extractWith(ContentResolver cr, Uri zipUri, Uri tree, String rootId, Charset cs,
                                    AtomicBoolean cancel) throws IOException {
        Map<String, String> dirs = new HashMap<>();
        dirs.put("", rootId);
        try (InputStream in = cr.openInputStream(zipUri)) {
            if (in == null) throw new IOException("No se pudo leer el ZIP");
            ZipInputStream zis = new ZipInputStream(new BufferedInputStream(in), cs);
            ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                if (cancel.get()) break;
                List<String> parts = safeParts(e.getName());
                if (parts == null || parts.isEmpty()) continue;
                boolean isDir = e.isDirectory();
                String parentId = ensureDirs(cr, tree, dirs, parts, isDir ? parts.size() : parts.size() - 1);
                if (isDir) continue;
                String name = parts.get(parts.size() - 1);
                Uri parent = DocumentsContract.buildDocumentUriUsingTree(tree, parentId);
                Uri out = DocumentsContract.createDocument(cr, parent, guessMime(name), name);
                if (out == null) throw new IOException("No se pudo crear " + name);
                try (OutputStream os = cr.openOutputStream(out, "w")) {
                    if (os == null) throw new IOException("No se pudo escribir " + name);
                    pipe(zis, os, cancel);
                }
            }
        }
    }

    /** Divide la ruta y rechaza entradas que intenten salir de la carpeta ("../"). */
    static List<String> safeParts(String raw) {
        List<String> parts = new ArrayList<>();
        for (String p : raw.replace('\\', '/').split("/")) {
            if (p.isEmpty() || p.equals(".")) continue;
            if (p.equals("..")) return null;
            parts.add(p);
        }
        return parts;
    }

    private static String ensureDirs(ContentResolver cr, Uri tree, Map<String, String> dirs,
                                     List<String> parts, int count) throws IOException {
        String key = "";
        String id = dirs.get("");
        for (int i = 0; i < count; i++) {
            key = key + "/" + parts.get(i);
            String existing = dirs.get(key);
            if (existing == null) {
                Uri created = SafOps.createFolder(cr, tree, id, parts.get(i));
                if (created == null) throw new IOException("No se pudo crear " + parts.get(i));
                existing = DocumentsContract.getDocumentId(created);
                dirs.put(key, existing);
            }
            id = existing;
        }
        return id;
    }

    static String guessMime(String name) {
        int dot = name.lastIndexOf('.');
        if (dot >= 0) {
            String m = MimeTypeMap.getSingleton().getMimeTypeFromExtension(
                    name.substring(dot + 1).toLowerCase(Locale.ROOT));
            if (m != null) return m;
        }
        return "application/octet-stream";
    }

    private static void pipe(InputStream in, OutputStream out, AtomicBoolean cancel) throws IOException {
        byte[] buf = new byte[64 * 1024];
        int n;
        while ((n = in.read(buf)) > 0) {
            if (cancel.get()) return;
            out.write(buf, 0, n);
        }
    }
}
