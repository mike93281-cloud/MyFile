package com.myfile.app;

import android.content.Context;

final class BuildInfo {
    private BuildInfo() {}

    @SuppressWarnings("deprecation")
    static String versionName(Context ctx) {
        try {
            return ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "";
        }
    }
}
