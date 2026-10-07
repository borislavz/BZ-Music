package com.borislav007.muzika;

import android.content.Context;
import android.content.SharedPreferences;

final class MusicFolderPrefs {
    private static final String PREFS = "muzika_prefs";
    private static final String KEY_FOLDER = "music_folder_path";
    private static final String KEY_SAF_TREE = "saf_tree_uri";

    private MusicFolderPrefs() {
    }

    static void saveFolderPath(Context context, String path) {
        prefs(context).edit().putString(KEY_FOLDER, path).apply();
    }

    static String getFolderPath(Context context) {
        return prefs(context).getString(KEY_FOLDER, null);
    }

    static void saveSafTreeUri(Context context, String uri) {
        prefs(context).edit().putString(KEY_SAF_TREE, uri).apply();
    }

    static String getSafTreeUri(Context context) {
        return prefs(context).getString(KEY_SAF_TREE, null);
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
