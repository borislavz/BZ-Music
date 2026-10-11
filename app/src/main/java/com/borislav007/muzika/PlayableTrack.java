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
    /** MediaStore / filesystem Date Modified epoch millis, or 0 if unknown. */
    final long dateModifiedMillis;

    /** Tag-derived release instant (full date or Jan 1 of year), 0 if no YEAR tag. */
    private volatile long taggedReleaseMillis;
    private volatile boolean taggedYearOnly;
    private volatile long onlineReleaseMillis;
    private volatile boolean onlineYearOnly;
    private volatile boolean tagProbeDone;
    private volatile boolean onlineLookupDone;

    PlayableTrack(Uri uri, String displayName, String title, String artist, String album,
                  String folderKey) {
        this(uri, displayName, title, artist, album, folderKey, 0, 0L);
    }

    PlayableTrack(Uri uri, String displayName, String title, String artist, String album,
                  String folderKey, int releaseYear, long dateModifiedMillis) {
        this.uri = uri;
        this.displayName = displayName != null ? displayName : "";
        this.title = title != null && !title.trim().isEmpty() ? title.trim() : this.displayName;
        this.artist = artist;
        this.album = album;
        this.folderKey = folderKey != null ? folderKey : "";
        this.dateModifiedMillis = dateModifiedMillis > 0L ? dateModifiedMillis : 0L;
        TrackReleaseDate.TagRelease initial = TrackReleaseDate.fromYear(releaseYear);
        this.taggedReleaseMillis = initial.millis;
        this.taggedYearOnly = initial.yearOnly;
        this.onlineReleaseMillis = 0L;
        this.onlineYearOnly = false;
        this.tagProbeDone = false;
        this.onlineLookupDone = false;
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

    boolean needsTagProbe() {
        return !tagProbeDone;
    }

    boolean needsOnlineYearLookup() {
        return tagProbeDone
                && !hasTaggedRelease()
                && !onlineLookupDone
                && OnlineAlbumArtLookup.hasUsableAlbum(album)
                && !shouldSkipOnlineLookupForFolderAlbum();
    }

    /**
     * Skip online lookup only when Album matches the folder name and that name is a
     * generic label (mix/audio/songs/favourites) or digits-only — case ignored.
     * YEAR tag still takes priority when present.
     */
    boolean shouldSkipOnlineLookupForFolderAlbum() {
        if (album == null || folderKey == null) {
            return false;
        }
        String albumName = album.trim();
        if (albumName.isEmpty()) {
            return false;
        }
        String folder = folderKey.trim();
        if (folder.isEmpty()) {
            return false;
        }
        int slash = folder.lastIndexOf('/');
        if (slash >= 0 && slash < folder.length() - 1) {
            folder = folder.substring(slash + 1);
        }
        folder = folder.trim();
        if (folder.isEmpty() || !albumName.equalsIgnoreCase(folder)) {
            return false;
        }
        String lower = albumName.toLowerCase(Locale.US);
        return "mix".equals(lower)
                || "audio".equals(lower)
                || "songs".equals(lower)
                || "favourites".equals(lower)
                || lower.matches("\\d+");
    }

    boolean hasTaggedRelease() {
        return taggedReleaseMillis > 0L;
    }

    boolean hasOnlineRelease() {
        return onlineReleaseMillis > 0L;
    }

    /** Online album release lookup (shown in bold purple). */
    boolean isOnlineRelease() {
        return !hasTaggedRelease() && hasOnlineRelease();
    }

    /** True when showing Date Modified because YEAR tag and online year are missing. */
    boolean isDateModifiedFallback() {
        return !hasTaggedRelease() && !hasOnlineRelease() && dateModifiedMillis > 0L;
    }

    boolean isReleaseValueBold() {
        return hasTaggedRelease() || isOnlineRelease();
    }

    void applyTagRelease(TrackReleaseDate.TagRelease tag) {
        tagProbeDone = true;
        if (tag != null && tag.isPresent()) {
            taggedReleaseMillis = tag.millis;
            taggedYearOnly = tag.yearOnly;
            onlineLookupDone = true;
        } else if (!OnlineAlbumArtLookup.hasUsableAlbum(album)
                || shouldSkipOnlineLookupForFolderAlbum()) {
            // No usable album, or generic folder/album name — use Date Modified only.
            onlineLookupDone = true;
        }
    }

    void applyOnlineRelease(TrackReleaseDate.TagRelease release) {
        onlineLookupDone = true;
        if (release != null && release.isPresent()) {
            onlineReleaseMillis = release.millis;
            onlineYearOnly = release.yearOnly;
        }
    }

    long releaseSortKey() {
        if (hasTaggedRelease()) {
            return taggedReleaseMillis;
        }
        if (hasOnlineRelease()) {
            return onlineReleaseMillis;
        }
        return Math.max(0L, dateModifiedMillis);
    }

    String releaseDateDisplay() {
        if (hasTaggedRelease()) {
            return TrackReleaseDate.displayValue(taggedReleaseMillis, taggedYearOnly, 0L);
        }
        if (hasOnlineRelease()) {
            return TrackReleaseDate.displayValue(onlineReleaseMillis, onlineYearOnly, 0L);
        }
        if (dateModifiedMillis > 0L) {
            return TrackReleaseDate.formatYyyyMmmDd(dateModifiedMillis);
        }
        return "";
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
