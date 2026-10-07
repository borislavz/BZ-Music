package com.borislav007.muzika;

import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Loads device audio through MediaStore (Play-compatible; no All files access).
 */
final class MediaStoreAudioLibrary {
    private MediaStoreAudioLibrary() {
    }

    static List<PlayableTrack> loadAll(Context context) {
        List<PlayableTrack> tracks = new ArrayList<>();
        List<Uri> collections = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            for (String volume : MediaStore.getExternalVolumeNames(context)) {
                collections.add(MediaStore.Audio.Media.getContentUri(volume));
            }
            if (collections.isEmpty()) {
                collections.add(MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL));
            }
        } else {
            collections.add(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI);
        }

        for (Uri collection : collections) {
            loadFromCollection(context, collection, tracks);
        }
        // Android often omits WMA/ASF from the Audio collection — pull them from Files.
        loadWmaAsfFromFiles(context, tracks);
        return tracks;
    }

    private static void loadFromCollection(Context context, Uri collection,
                                           List<PlayableTrack> tracks) {
        String[] projection;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            projection = new String[]{
                    MediaStore.Audio.Media._ID,
                    MediaStore.Audio.Media.DISPLAY_NAME,
                    MediaStore.Audio.Media.TITLE,
                    MediaStore.Audio.Media.ARTIST,
                    MediaStore.Audio.Media.ALBUM,
                    MediaStore.Audio.Media.RELATIVE_PATH,
                    MediaStore.Audio.Media.MIME_TYPE
            };
        } else {
            projection = new String[]{
                    MediaStore.Audio.Media._ID,
                    MediaStore.Audio.Media.DISPLAY_NAME,
                    MediaStore.Audio.Media.TITLE,
                    MediaStore.Audio.Media.ARTIST,
                    MediaStore.Audio.Media.ALBUM,
                    MediaStore.Audio.Media.DATA,
                    MediaStore.Audio.Media.MIME_TYPE
            };
        }

        // Do NOT require IS_MUSIC=1 — many real files (and emulator demos) leave it null.
        try (Cursor cursor = context.getContentResolver().query(
                collection, projection, null, null,
                MediaStore.Audio.Media.DISPLAY_NAME + " COLLATE NOCASE ASC")) {
            if (cursor == null) {
                return;
            }
            int idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
            int nameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME);
            int titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE);
            int artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST);
            int albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM);
            int mimeCol = cursor.getColumnIndex(MediaStore.Audio.Media.MIME_TYPE);
            int pathCol = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                    ? cursor.getColumnIndex(MediaStore.Audio.Media.RELATIVE_PATH)
                    : cursor.getColumnIndex(MediaStore.Audio.Media.DATA);

            while (cursor.moveToNext()) {
                long id = cursor.getLong(idCol);
                String displayName = cursor.getString(nameCol);
                String mime = mimeCol >= 0 ? cursor.getString(mimeCol) : null;
                if (!isAudioCandidate(displayName, mime)) {
                    continue;
                }
                String title = cursor.getString(titleCol);
                String artist = cursor.getString(artistCol);
                String album = cursor.getString(albumCol);
                String folderKey = "";
                if (pathCol >= 0) {
                    folderKey = toFolderKey(cursor.getString(pathCol),
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q);
                }
                Uri contentUri = ContentUris.withAppendedId(collection, id);
                tracks.add(new PlayableTrack(contentUri, displayName, title, artist, album, folderKey));
            }
        } catch (SecurityException ignored) {
            // Permission not granted yet.
        }
    }

    /**
     * WMA is rarely indexed as MediaStore Audio (Android has no WMA decoder),
     * but files may still appear in MediaStore.Files after a scan.
     */
    private static void loadWmaAsfFromFiles(Context context, List<PlayableTrack> tracks) {
        Set<String> seen = new HashSet<>();
        for (PlayableTrack existing : tracks) {
            seen.add(dedupeKey(existing.folderKey, existing.displayName));
        }

        List<Uri> collections = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            for (String volume : MediaStore.getExternalVolumeNames(context)) {
                collections.add(MediaStore.Files.getContentUri(volume));
            }
            if (collections.isEmpty()) {
                collections.add(MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL));
            }
        } else {
            collections.add(MediaStore.Files.getContentUri("external"));
        }

        String selection = MediaStore.Files.FileColumns.DISPLAY_NAME + " LIKE ?"
                + " OR " + MediaStore.Files.FileColumns.DISPLAY_NAME + " LIKE ?"
                + " OR " + MediaStore.Files.FileColumns.DISPLAY_NAME + " LIKE ?"
                + " OR " + MediaStore.Files.FileColumns.DISPLAY_NAME + " LIKE ?";
        String[] args = new String[]{"%.wma", "%.WMA", "%.asf", "%.ASF"};

        for (Uri collection : collections) {
            String[] projection;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                projection = new String[]{
                        MediaStore.Files.FileColumns._ID,
                        MediaStore.Files.FileColumns.DISPLAY_NAME,
                        MediaStore.Files.FileColumns.TITLE,
                        MediaStore.Files.FileColumns.RELATIVE_PATH,
                        MediaStore.Files.FileColumns.MIME_TYPE
                };
            } else {
                projection = new String[]{
                        MediaStore.Files.FileColumns._ID,
                        MediaStore.Files.FileColumns.DISPLAY_NAME,
                        MediaStore.Files.FileColumns.TITLE,
                        MediaStore.Files.FileColumns.DATA,
                        MediaStore.Files.FileColumns.MIME_TYPE
                };
            }
            try (Cursor cursor = context.getContentResolver().query(
                    collection, projection, selection, args,
                    MediaStore.Files.FileColumns.DISPLAY_NAME + " COLLATE NOCASE ASC")) {
                if (cursor == null) {
                    continue;
                }
                int idCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID);
                int nameCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME);
                int titleCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.TITLE);
                int pathCol = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                        ? cursor.getColumnIndex(MediaStore.Files.FileColumns.RELATIVE_PATH)
                        : cursor.getColumnIndex(MediaStore.Files.FileColumns.DATA);
                while (cursor.moveToNext()) {
                    String displayName = cursor.getString(nameCol);
                    if (displayName == null || !isSupportedName(displayName)) {
                        continue;
                    }
                    String folderKey = "";
                    if (pathCol >= 0) {
                        folderKey = toFolderKey(cursor.getString(pathCol),
                                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q);
                    }
                    String key = dedupeKey(folderKey, displayName);
                    if (!seen.add(key)) {
                        continue;
                    }
                    long id = cursor.getLong(idCol);
                    String title = titleCol >= 0 ? cursor.getString(titleCol) : displayName;
                    Uri contentUri = ContentUris.withAppendedId(collection, id);
                    tracks.add(new PlayableTrack(contentUri, displayName, title, null, null, folderKey));
                }
            } catch (SecurityException | IllegalArgumentException ignored) {
                // Files table may be restricted on some Android versions.
            }
        }
    }

    private static String dedupeKey(String folderKey, String displayName) {
        return (folderKey != null ? folderKey : "").toLowerCase(Locale.US)
                + "\0"
                + (displayName != null ? displayName : "").toLowerCase(Locale.US);
    }

    static List<String> childFolders(List<PlayableTrack> all, String currentFolderKey) {
        Set<String> children = new LinkedHashSet<>();
        String prefix = currentFolderKey.isEmpty() ? "" : currentFolderKey + "/";
        for (PlayableTrack track : all) {
            String key = track.folderKey;
            if (currentFolderKey.isEmpty()) {
                if (key.isEmpty()) {
                    continue;
                }
                int slash = key.indexOf('/');
                children.add(slash < 0 ? key : key.substring(0, slash));
            } else if (key.equals(currentFolderKey)) {
                // track in this folder
            } else if (key.startsWith(prefix)) {
                String rest = key.substring(prefix.length());
                int slash = rest.indexOf('/');
                children.add(slash < 0 ? rest : rest.substring(0, slash));
            }
        }
        List<String> list = new ArrayList<>(children);
        Collections.sort(list, String.CASE_INSENSITIVE_ORDER);
        return list;
    }

    /**
     * Root folders shown in Open file / Open folder: Android audio directories
     * (Music, Podcasts, …) plus AUDIO and My Music when present on the device.
     */
    static List<String> rootAudioFolders(List<PlayableTrack> all) {
        Map<String, String> byLower = new LinkedHashMap<>();
        for (String child : childFolders(all, "")) {
            if (!isAllowedRootAudioFolder(child)) {
                continue;
            }
            if (countInFolder(all, child, true) <= 0) {
                continue;
            }
            byLower.put(child.toLowerCase(Locale.US), child);
        }
        List<String> list = new ArrayList<>(byLower.values());
        Collections.sort(list, String.CASE_INSENSITIVE_ORDER);
        return list;
    }

    /** Immediate child folders that contain at least one audio file (including in subfolders). */
    static List<String> childFoldersWithAudio(List<PlayableTrack> all, String currentFolderKey) {
        List<String> children = childFolders(all, currentFolderKey);
        List<String> withAudio = new ArrayList<>();
        for (String child : children) {
            String childKey = currentFolderKey == null || currentFolderKey.isEmpty()
                    ? child
                    : currentFolderKey + "/" + child;
            if (countInFolder(all, childKey, true) > 0) {
                withAudio.add(child);
            }
        }
        return withAudio;
    }

    static boolean isAllowedRootAudioFolder(String name) {
        if (name == null) {
            return false;
        }
        String trimmed = name.trim();
        if (trimmed.isEmpty()) {
            return false;
        }
        for (String allowed : preferredRootFolderNames()) {
            if (allowed.equalsIgnoreCase(trimmed)) {
                return true;
            }
        }
        return false;
    }

    private static List<String> preferredRootFolderNames() {
        List<String> names = new ArrayList<>();
        names.add(Environment.DIRECTORY_MUSIC);
        names.add(Environment.DIRECTORY_PODCASTS);
        // Audiobooks constant is API 29+; use the public path name on all levels.
        names.add("Audiobooks");
        names.add(Environment.DIRECTORY_NOTIFICATIONS);
        names.add(Environment.DIRECTORY_ALARMS);
        names.add(Environment.DIRECTORY_RINGTONES);
        // Common OEM / Windows-style music folders (show only if present).
        names.add("AUDIO");
        names.add("My Music");
        return names;
    }

    static List<PlayableTrack> tracksInFolder(List<PlayableTrack> all, String folderKey,
                                              boolean recursive) {
        List<PlayableTrack> out = new ArrayList<>();
        String prefix = folderKey.isEmpty() ? "" : folderKey + "/";
        for (PlayableTrack track : all) {
            if (recursive) {
                if (folderKey.isEmpty()
                        || track.folderKey.equals(folderKey)
                        || track.folderKey.startsWith(prefix)) {
                    out.add(track);
                }
            } else if (track.folderKey.equals(folderKey)) {
                out.add(track);
            }
        }
        Collections.sort(out, new Comparator<PlayableTrack>() {
            @Override
            public int compare(PlayableTrack a, PlayableTrack b) {
                int byFolder = a.folderKey.compareToIgnoreCase(b.folderKey);
                if (byFolder != 0) {
                    return byFolder;
                }
                return a.displayName.compareToIgnoreCase(b.displayName);
            }
        });
        return out;
    }

    static int countInFolder(List<PlayableTrack> all, String folderKey, boolean recursive) {
        return tracksInFolder(all, folderKey, recursive).size();
    }

    static String parentFolderKey(String folderKey) {
        if (folderKey == null || folderKey.isEmpty()) {
            return null;
        }
        int slash = folderKey.lastIndexOf('/');
        if (slash < 0) {
            return "";
        }
        return folderKey.substring(0, slash);
    }

    /**
     * Converts old absolute paths (from the pre-Play file browser) and MediaStore
     * relative paths into the same folder-key format.
     */
    static String normalizeFolderKey(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return "";
        }
        String path = raw.trim().replace('\\', '/');
        String lower = path.toLowerCase(Locale.US);
        String[] roots = {
                "/storage/emulated/0/",
                "/sdcard/",
                "/mnt/sdcard/",
                "file:///storage/emulated/0/",
                "file:///sdcard/"
        };
        for (String root : roots) {
            if (lower.startsWith(root)) {
                path = path.substring(root.length());
                break;
            }
        }
        while (path.startsWith("/")) {
            path = path.substring(1);
        }
        while (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        return path;
    }

    static boolean folderExistsInLibrary(List<PlayableTrack> all, String folderKey) {
        if (folderKey == null || folderKey.isEmpty()) {
            return true;
        }
        if (!childFolders(all, folderKey).isEmpty()) {
            return true;
        }
        return countInFolder(all, folderKey, true) > 0;
    }

    private static String toFolderKey(String raw, boolean relativePath) {
        if (raw == null || raw.trim().isEmpty()) {
            return "";
        }
        String value = raw.trim().replace('\\', '/');
        if (relativePath) {
            while (value.startsWith("/")) {
                value = value.substring(1);
            }
            while (value.endsWith("/")) {
                value = value.substring(0, value.length() - 1);
            }
            return value;
        }
        File file = new File(value);
        File parent = file.getParentFile();
        if (parent == null) {
            return "";
        }
        return normalizeFolderKey(parent.getAbsolutePath());
    }

    private static boolean isAudioCandidate(String displayName, String mime) {
        if (displayName != null && isSupportedName(displayName)) {
            return true;
        }
        if (mime != null) {
            String m = mime.toLowerCase(Locale.US);
            if (m.startsWith("audio/") || m.equals("application/ogg")) {
                return displayName != null && !displayName.trim().isEmpty();
            }
        }
        return false;
    }

    private static boolean isSupportedName(String name) {
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return false;
        }
        String ext = name.substring(dot + 1).toLowerCase(Locale.US);
        return MusicPlayer.supportedExtensions().contains(ext);
    }
}
