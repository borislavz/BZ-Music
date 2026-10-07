package com.borislav007.muzika;

import android.net.Uri;
import java.util.Locale;
import java.util.Objects;

/**
 * One playable audio item discovered via MediaStore (Play-compatible).
 */
final class PlayableTrack {
    final Uri uri;
    final String displayName;
    final String title;
    final String artist;
    final String album;
    /** Folder key without trailing slash, e.g. Music/Audio/Acoustic */
    final String folderKey;

    PlayableTrack(Uri uri, String displayName, String title, String artist, String album,
                  String folderKey) {
        this.uri = uri;
        this.displayName = displayName != null ? displayName : "";
        this.title = title != null && !title.trim().isEmpty() ? title.trim() : this.displayName;
        this.artist = artist;
        this.album = album;
        this.folderKey = folderKey != null ? folderKey : "";
    }

    String extension() {
        String name = displayName;
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return "";
        }
        return name.substring(dot + 1).toLowerCase(Locale.US);
    }

    boolean needsVlc() {
        String ext = extension();
        return "wma".equals(ext) || "asf".equals(ext);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PlayableTrack)) {
            return false;
        }
        PlayableTrack that = (PlayableTrack) o;
        return Objects.equals(uri, that.uri);
    }

    @Override
    public int hashCode() {
        return uri != null ? uri.hashCode() : 0;
    }
}
