package com.myfile.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Favoritos, recientes y papelera. Se guardan solo en el almacenamiento privado de la app
 * (sin copia en la nube: allowBackup=false).
 */
public final class Store {
    private Store() {}

    public static final String FAVORITES = "favorites";
    public static final String RECENT = "recent";
    public static final String TRASH = "trash";

    private static final int MAX_RECENT = 40;

    public static final class Entry {
        public String uri;
        public String tree;
        public String doc;
        public String parent;
        public String origin;
        public String trashDir;
        public String name;
        public String mime;
        public long time;

        public static Entry of(FileItem f) {
            Entry e = new Entry();
            e.uri = f.uri.toString();
            e.tree = f.treeUri == null ? null : f.treeUri.toString();
            e.doc = f.docId;
            e.parent = f.parentDocId;
            e.name = f.name;
            e.mime = f.mime;
            e.time = System.currentTimeMillis();
            return e;
        }

        public Uri treeUri() { return tree == null ? null : Uri.parse(tree); }

        public FileItem toItem() {
            return new FileItem(Uri.parse(uri), treeUri(), doc, parent, name, mime, -1, time, 0);
        }

        JSONObject json() throws Exception {
            JSONObject o = new JSONObject();
            o.put("uri", uri);
            o.putOpt("tree", tree);
            o.putOpt("doc", doc);
            o.putOpt("parent", parent);
            o.putOpt("origin", origin);
            o.putOpt("trashDir", trashDir);
            o.putOpt("name", name);
            o.putOpt("mime", mime);
            o.put("time", time);
            return o;
        }

        static Entry from(JSONObject o) {
            Entry e = new Entry();
            e.uri = o.optString("uri");
            e.tree = o.has("tree") ? o.optString("tree") : null;
            e.doc = o.has("doc") ? o.optString("doc") : null;
            e.parent = o.has("parent") ? o.optString("parent") : null;
            e.origin = o.has("origin") ? o.optString("origin") : null;
            e.trashDir = o.has("trashDir") ? o.optString("trashDir") : null;
            e.name = o.optString("name");
            e.mime = o.optString("mime", "application/octet-stream");
            e.time = o.optLong("time");
            return e;
        }
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("store", Context.MODE_PRIVATE);
    }

    public static synchronized List<Entry> get(Context c, String key) {
        List<Entry> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(prefs(c).getString(key, "[]"));
            for (int i = 0; i < a.length(); i++) out.add(Entry.from(a.getJSONObject(i)));
        } catch (Exception ignored) {
        }
        return out;
    }

    public static synchronized void put(Context c, String key, List<Entry> list) {
        JSONArray a = new JSONArray();
        try {
            for (Entry e : list) a.put(e.json());
        } catch (Exception ignored) {
        }
        prefs(c).edit().putString(key, a.toString()).apply();
    }

    public static synchronized void remove(Context c, String key, String uri) {
        List<Entry> list = get(c, key);
        for (int i = list.size() - 1; i >= 0; i--) {
            if (list.get(i).uri.equals(uri)) list.remove(i);
        }
        put(c, key, list);
    }

    public static synchronized void add(Context c, String key, Entry e, int max) {
        List<Entry> list = get(c, key);
        for (int i = list.size() - 1; i >= 0; i--) {
            if (list.get(i).uri.equals(e.uri)) list.remove(i);
        }
        list.add(0, e);
        while (max > 0 && list.size() > max) list.remove(list.size() - 1);
        put(c, key, list);
    }

    public static boolean contains(Context c, String key, String uri) {
        for (Entry e : get(c, key)) if (e.uri.equals(uri)) return true;
        return false;
    }

    public static void addRecent(Context c, FileItem f) {
        if (f.isDir()) return;
        add(c, RECENT, Entry.of(f), MAX_RECENT);
    }

    /** Añade o quita de favoritos. Devuelve true si queda como favorito. */
    public static boolean toggleFavorite(Context c, FileItem f) {
        String key = f.uri.toString();
        if (contains(c, FAVORITES, key)) {
            remove(c, FAVORITES, key);
            return false;
        }
        add(c, FAVORITES, Entry.of(f), 0);
        return true;
    }

    public static boolean showHidden(Context c) {
        return c.getSharedPreferences("settings", Context.MODE_PRIVATE).getBoolean("hidden", false);
    }

    public static void setShowHidden(Context c, boolean on) {
        c.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putBoolean("hidden", on).apply();
    }
}
