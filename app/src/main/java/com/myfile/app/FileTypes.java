package com.myfile.app;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** Clasificación de archivos por tipo para iconos y categorías. */
public final class FileTypes {
    private FileTypes() {}

    public static final Set<String> DOCUMENT_EXT = new HashSet<>(Arrays.asList(
            "pdf", "doc", "docx", "odt", "rtf", "txt", "md", "csv",
            "xls", "xlsx", "ods", "ppt", "pptx", "odp", "epub", "json", "xml", "html", "htm"));
    public static final Set<String> ARCHIVE_EXT = new HashSet<>(Arrays.asList(
            "zip", "rar", "7z", "tar", "gz", "tgz", "bz2", "xz", "zst"));
    public static final Set<String> APK_EXT = new HashSet<>(Arrays.asList("apk", "apks", "xapk"));

    public static boolean isImage(FileItem f) { return f.mime.startsWith("image/"); }
    public static boolean isVideo(FileItem f) { return f.mime.startsWith("video/"); }
    public static boolean isAudio(FileItem f) { return f.mime.startsWith("audio/"); }
    public static boolean isApk(FileItem f) {
        return "application/vnd.android.package-archive".equals(f.mime) || APK_EXT.contains(f.extension());
    }
    public static boolean isArchive(FileItem f) { return ARCHIVE_EXT.contains(f.extension()); }
    public static boolean isDocument(FileItem f) {
        return DOCUMENT_EXT.contains(f.extension()) || f.mime.startsWith("text/");
    }

    public static int iconFor(FileItem f) {
        if (f.isDir()) return R.drawable.ic_folder;
        if (isImage(f)) return R.drawable.ic_image;
        if (isVideo(f)) return R.drawable.ic_video;
        if (isAudio(f)) return R.drawable.ic_music;
        if (isApk(f)) return R.drawable.ic_apk;
        if (isArchive(f)) return R.drawable.ic_archive;
        if ("pdf".equals(f.extension())) return R.drawable.ic_pdf;
        if (isDocument(f)) return R.drawable.ic_doc;
        return R.drawable.ic_file;
    }

    /** ¿Tiene sentido intentar sacar miniatura? */
    public static boolean wantsThumb(FileItem f) {
        return !f.isDir() && (isImage(f) || isVideo(f));
    }
}
