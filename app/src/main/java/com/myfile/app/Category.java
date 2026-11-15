package com.myfile.app;

import android.Manifest;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;

/** Categorías del inicio. Las tres primeras usan la galería; las demás buscan en tus carpetas. */
public enum Category {
    IMAGES(R.string.cat_images, R.drawable.ic_image, R.color.cat_images, true),
    VIDEOS(R.string.cat_videos, R.drawable.ic_video, R.color.cat_videos, true),
    MUSIC(R.string.cat_music, R.drawable.ic_music, R.color.cat_music, true),
    DOCUMENTS(R.string.cat_documents, R.drawable.ic_doc, R.color.cat_docs, false),
    APKS(R.string.cat_apks, R.drawable.ic_apk, R.color.cat_apk, false),
    ARCHIVES(R.string.cat_archives, R.drawable.ic_archive, R.color.cat_archives, false);

    public final int label;
    public final int icon;
    public final int tint;
    public final boolean media;

    Category(int label, int icon, int tint, boolean media) {
        this.label = label;
        this.icon = icon;
        this.tint = tint;
        this.media = media;
    }

    /** Permiso de solo lectura que necesita esta categoría (null si ninguno). */
    public String permission() {
        if (!media) return null;
        if (Build.VERSION.SDK_INT >= 33) {
            switch (this) {
                case IMAGES: return "android.permission.READ_MEDIA_IMAGES";
                case VIDEOS: return "android.permission.READ_MEDIA_VIDEO";
                default: return "android.permission.READ_MEDIA_AUDIO";
            }
        }
        return Manifest.permission.READ_EXTERNAL_STORAGE;
    }

    public Uri collection() {
        switch (this) {
            case IMAGES: return MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
            case VIDEOS: return MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
            case MUSIC: return MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
            default: return null;
        }
    }

    public boolean matches(FileItem f) {
        switch (this) {
            case IMAGES: return FileTypes.isImage(f);
            case VIDEOS: return FileTypes.isVideo(f);
            case MUSIC: return FileTypes.isAudio(f);
            case DOCUMENTS: return FileTypes.isDocument(f);
            case APKS: return FileTypes.isApk(f);
            case ARCHIVES: return FileTypes.isArchive(f);
            default: return false;
        }
    }
}
