package com.borislav007.muzika;

import android.content.Context;
import android.content.Intent;
import android.content.UriPermission;
import android.net.Uri;
import androidx.documentfile.provider.DocumentFile;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Play-safe access to a user-granted folder tree via Storage Access Framework.
 */
final class SafAudioLibrary {
    private SafAudioLibrary() {
    }

    static void takePersistablePermission(Context context, Uri treeUri) {
        final int flags = Intent.FLAG_GRANT_READ_URI_PERMISSION;
        try {
            context.getContentResolver().takePersistableUriPermission(treeUri, flags);
        } catch (SecurityException ignored) {
            // Some providers only grant one-shot access.
        }
    }

    static Uri findPersistedTree(Context context) {
        List<UriPermission> perms = context.getContentResolver().getPersistedUriPermissions();
        for (UriPermission perm : perms) {
            if (perm.isReadPermission() && perm.getUri() != null) {
                return perm.getUri();
            }
        }
        return null;
    }

    static DocumentFile root(Context context, Uri treeUri) {
        if (treeUri == null) {
            return null;
        }
        return DocumentFile.fromTreeUri(context, treeUri);
    }

    static List<DocumentFile> childDirectories(DocumentFile dir) {
        List<DocumentFile> out = new ArrayList<>();
        if (dir == null || !dir.isDirectory()) {
            return out;
        }
        DocumentFile[] children = dir.listFiles();
        if (children == null) {
            return out;
        }
        for (DocumentFile child : children) {
            if (child != null && child.isDirectory() && child.getName() != null) {
                out.add(child);
            }
        }
        Collections.sort(out, new Comparator<DocumentFile>() {
            @Override
            public int compare(DocumentFile a, DocumentFile b) {
                return safeName(a).compareToIgnoreCase(safeName(b));
            }
        });
        return out;
    }

    static List<PlayableTrack> audioFilesIn(DocumentFile dir, boolean recursive) {
        List<PlayableTrack> out = new ArrayList<>();
        collect(dir, dir, recursive, out);
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

    private static void collect(DocumentFile root, DocumentFile dir, boolean recursive,
                                List<PlayableTrack> out) {
        if (dir == null || !dir.isDirectory()) {
            return;
        }
        DocumentFile[] children = dir.listFiles();
        if (children == null) {
            return;
        }
        for (DocumentFile child : children) {
            if (child == null) {
                continue;
            }
            if (child.isDirectory()) {
                if (recursive) {
                    collect(root, child, true, out);
                }
            } else if (child.isFile()) {
                String name = child.getName();
                if (name == null || !isSupportedName(name)) {
                    continue;
                }
                Uri uri = child.getUri();
                if (uri == null) {
                    continue;
                }
                out.add(new PlayableTrack(uri, name, name, null, null, relativeKey(root, dir)));
            }
        }
    }

    static String relativeKey(DocumentFile root, DocumentFile dir) {
        if (root == null || dir == null) {
            return "";
        }
        if (root.getUri() != null && root.getUri().equals(dir.getUri())) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        DocumentFile walk = dir;
        int guard = 0;
        while (walk != null && guard++ < 64) {
            if (root.getUri() != null && root.getUri().equals(walk.getUri())) {
                break;
            }
            String name = walk.getName();
            if (name != null && !name.isEmpty()) {
                parts.add(name);
            }
            // DocumentFile has no getParent(); rebuild from tree is imperfect.
            // For direct children we only need one level; for nested, use name chain via path.
            break;
        }
        // Prefer display path built while collecting — for one-level browser we pass folder name.
        if (parts.isEmpty()) {
            return safeName(dir);
        }
        Collections.reverse(parts);
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (sb.length() > 0) {
                sb.append('/');
            }
            sb.append(part);
        }
        return sb.toString();
    }

    static String displayPath(DocumentFile root, List<String> relativeSegments) {
        String rootName = root != null && root.getName() != null ? root.getName() : "Folder";
        if (relativeSegments == null || relativeSegments.isEmpty()) {
            return rootName;
        }
        StringBuilder sb = new StringBuilder(rootName);
        for (String segment : relativeSegments) {
            sb.append('/').append(segment);
        }
        return sb.toString();
    }

    static DocumentFile navigate(DocumentFile root, List<String> relativeSegments) {
        DocumentFile current = root;
        if (relativeSegments == null) {
            return current;
        }
        for (String segment : relativeSegments) {
            if (current == null) {
                return null;
            }
            DocumentFile next = null;
            for (DocumentFile child : childDirectories(current)) {
                if (segment.equals(child.getName())) {
                    next = child;
                    break;
                }
            }
            current = next;
        }
        return current;
    }

    private static String safeName(DocumentFile file) {
        String name = file.getName();
        return name != null ? name : "";
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
