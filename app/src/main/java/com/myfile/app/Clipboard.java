package com.myfile.app;

import java.util.ArrayList;
import java.util.List;

/** Portapapeles interno para copiar/mover entre carpetas. Solo vive en memoria. */
public final class Clipboard {
    private Clipboard() {}

    private static final List<FileItem> items = new ArrayList<>();
    private static boolean cut;

    public static synchronized void set(List<FileItem> files, boolean isCut) {
        items.clear();
        items.addAll(files);
        cut = isCut;
    }

    public static synchronized List<FileItem> items() { return new ArrayList<>(items); }
    public static synchronized boolean isCut() { return cut; }
    public static synchronized boolean isEmpty() { return items.isEmpty(); }
    public static synchronized void clear() { items.clear(); }
}
