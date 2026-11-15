package com.myfile.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.webkit.MimeTypeMap;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/** Decide con qué visor interno se abre cada archivo. */
public final class FileOpener {
    private FileOpener() {}

    public static String bestMime(FileItem f) {
        if (!"application/octet-stream".equals(f.mime)) return f.mime;
        String guess = MimeTypeMap.getSingleton().getMimeTypeFromExtension(f.extension());
        return guess != null ? guess : "*/*";
    }

    /** Abre con el visor interno si hay uno. {@code siblings} se usa para pasar fotos con el dedo. */
    public static void open(Activity a, FileItem f, List<FileItem> siblings) {
        Store.addRecent(a, f);
        if (FileTypes.isApk(f)) {
            new AlertDialog.Builder(a)
                    .setTitle(R.string.apk_title)
                    .setMessage(R.string.apk_msg)
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
            return;
        }
        if (FileTypes.isImage(f)) {
            ArrayList<Uri> uris = new ArrayList<>();
            ArrayList<String> names = new ArrayList<>();
            int index = 0;
            if (siblings != null) {
                for (FileItem s : siblings) {
                    if (!FileTypes.isImage(s)) continue;
                    if (s.uri.equals(f.uri)) index = uris.size();
                    uris.add(s.uri);
                    names.add(s.name);
                }
            }
            if (uris.isEmpty()) {
                uris.add(f.uri);
                names.add(f.name);
            }
            a.startActivity(ImageViewerActivity.intent(a, uris, names, index));
            return;
        }
        if (FileTypes.isVideo(f) || FileTypes.isAudio(f)) {
            a.startActivity(PlayerActivity.intent(a, f.uri, f.name, FileTypes.isAudio(f)));
            return;
        }
        if (FileTypes.isText(f)) {
            a.startActivity(EditorActivity.intent(a, f.uri, f.name, f.isDocument()));
            return;
        }
        openExternal(a, f);
    }

    public static void openExternal(Activity a, FileItem f) {
        Store.addRecent(a, f);
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setDataAndType(f.uri, bestMime(f));
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            a.startActivity(Intent.createChooser(i, a.getString(R.string.open_with)));
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(a, R.string.no_app, Toast.LENGTH_SHORT).show();
        }
    }
}
