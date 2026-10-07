package com.borislav007.muzika;

import android.content.Context;
import android.content.SharedPreferences;

final class PlaybackPrefs {
    private static final String PREFS = "muzika_playback_prefs";
    private static final String KEY_SHUFFLE = "shuffle";
    private static final String KEY_REPEAT = "repeat";

    private PlaybackPrefs() {
    }

    static boolean isShuffleEnabled(Context context) {
        return prefs(context).getBoolean(KEY_SHUFFLE, false);
    }

    static void setShuffleEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_SHUFFLE, enabled).commit();
    }

    static boolean isRepeatEnabled(Context context) {
        return prefs(context).getBoolean(KEY_REPEAT, false);
    }

    static void setRepeatEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_REPEAT, enabled).commit();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
