package com.borislav007.muzika;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.documentfile.provider.DocumentFile;
import com.google.android.material.button.MaterialButton;
import java.util.ArrayList;
import java.util.List;

public class FolderBrowserActivity extends AppCompatActivity {
    public static final String EXTRA_OPEN_MODE = "open_mode";
    public static final String OPEN_FILE = "open_file";
    public static final String OPEN_FOLDER_ONLY = "open_folder_only";
    public static final String OPEN_FOLDER_ALL = "open_folder_all";

    private static final int REQ_READ = 1001;
    private static final int REQ_SAF_TREE = 1002;

    private TextView textCurrentPath;
    private TextView textMusicCount;
    private TextView textEmpty;
    private ListView listFolders;
    private MaterialButton buttonUseFolder;
    private MaterialButton buttonPickFolder;

    private String openMode = OPEN_FILE;
    private String currentFolderKey = "";
    private List<PlayableTrack> allTracks = new ArrayList<>();
    private final List<BrowserEntry> listEntries = new ArrayList<>();
    private ArrayAdapter<String> adapter;

    private boolean usingSaf;
    private Uri safTreeUri;
    private DocumentFile safRoot;
    private DocumentFile safCurrent;
    private final List<String> safSegments = new ArrayList<>();

    private static final class BrowserEntry {
        enum Type { UP, FOLDER, TRACK }

        final Type type;
        final String folderKey;
        final PlayableTrack track;
        final DocumentFile documentDir;

        BrowserEntry(Type type, String folderKey, PlayableTrack track, DocumentFile documentDir) {
            this.type = type;
            this.folderKey = folderKey;
            this.track = track;
            this.documentDir = documentDir;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_folder_browser);

        if (getIntent() != null && getIntent().hasExtra(EXTRA_OPEN_MODE)) {
            openMode = getIntent().getStringExtra(EXTRA_OPEN_MODE);
        }
        if (openMode == null) {
            openMode = OPEN_FILE;
        }

        AppNavigation.bindAppBar(this, titleForMode(), true);

        textCurrentPath = findViewById(R.id.textCurrentPath);
        textMusicCount = findViewById(R.id.textMusicCount);
        textEmpty = findViewById(R.id.textEmpty);
        listFolders = findViewById(R.id.listFolders);
        buttonUseFolder = findViewById(R.id.buttonUseFolder);
        buttonPickFolder = findViewById(R.id.buttonPickFolder);

        adapter = new ArrayAdapter<>(this, R.layout.item_folder, android.R.id.text1, new ArrayList<>());
        listFolders.setAdapter(adapter);

        listFolders.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (position < 0 || position >= listEntries.size()) {
                    return;
                }
                BrowserEntry entry = listEntries.get(position);
                if (usingSaf) {
                    handleSafClick(entry);
                } else if (entry.type == BrowserEntry.Type.UP) {
                    String parentKey = MediaStoreAudioLibrary.parentFolderKey(currentFolderKey);
                    if (parentKey != null) {
                        openMediaFolder(parentKey);
                    }
                } else if (entry.type == BrowserEntry.Type.FOLDER) {
                    openMediaFolder(entry.folderKey);
                } else if (entry.type == BrowserEntry.Type.TRACK) {
                    startTrackPlayback(entry.track);
                }
            }
        });

        if (OPEN_FILE.equals(openMode)) {
            buttonUseFolder.setVisibility(View.GONE);
        } else if (OPEN_FOLDER_ALL.equals(openMode)) {
            buttonUseFolder.setText(R.string.folder_play_all_here);
            buttonUseFolder.setOnClickListener(v -> startFolderPlayback(true));
        } else {
            buttonUseFolder.setText(R.string.folder_play_here);
            buttonUseFolder.setOnClickListener(v -> startFolderPlayback(false));
        }

        buttonPickFolder.setOnClickListener(v -> launchSafPicker());

        PlaybackControlsBinder.bind(this);

        // Always open at library root so every MediaStore folder is visible.
        // Last-used folder is still shown on the home screen.
        currentFolderKey = "";

        String savedTree = MusicFolderPrefs.getSafTreeUri(this);
        if (savedTree != null) {
            safTreeUri = Uri.parse(savedTree);
        }

        ensureAccessThenBrowse();
    }

    private int titleForMode() {
        if (OPEN_FOLDER_ALL.equals(openMode)) {
            return R.string.folder_browser_title_folder_all;
        }
        if (OPEN_FOLDER_ONLY.equals(openMode)) {
            return R.string.folder_browser_title_folder_only;
        }
        return R.string.folder_browser_title_file;
    }

    private void launchSafPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try {
            startActivityForResult(intent, REQ_SAF_TREE);
        } catch (Exception e) {
            Toast.makeText(this, R.string.folder_pick_saf_hint, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_SAF_TREE || resultCode != Activity.RESULT_OK || data == null) {
            return;
        }
        Uri tree = data.getData();
        if (tree == null) {
            return;
        }
        SafAudioLibrary.takePersistablePermission(this, tree);
        safTreeUri = tree;
        MusicFolderPrefs.saveSafTreeUri(this, tree.toString());
        usingSaf = true;
        safSegments.clear();
        refreshSafUi();
    }

    private void handleSafClick(BrowserEntry entry) {
        if (entry.type == BrowserEntry.Type.UP) {
            if (!safSegments.isEmpty()) {
                safSegments.remove(safSegments.size() - 1);
            } else {
                usingSaf = false;
                refreshLibraryAndUi();
                return;
            }
            refreshSafUi();
        } else if (entry.type == BrowserEntry.Type.FOLDER && entry.documentDir != null) {
            String name = entry.documentDir.getName();
            if (name != null) {
                safSegments.add(name);
            }
            refreshSafUi();
        } else if (entry.type == BrowserEntry.Type.TRACK) {
            startTrackPlayback(entry.track);
        }
    }

    private void startTrackPlayback(PlayableTrack track) {
        if (track == null) {
            return;
        }
        MusicFolderPrefs.saveFolderPath(this, track.folderKey);
        List<PlayableTrack> playlist;
        if (usingSaf) {
            boolean recursive = OPEN_FOLDER_ALL.equals(openMode);
            DocumentFile base = safCurrent != null ? safCurrent : safRoot;
            playlist = SafAudioLibrary.audioFilesIn(base, recursive);
        } else if (OPEN_FOLDER_ALL.equals(openMode)) {
            playlist = MediaStoreAudioLibrary.tracksInFolder(allTracks, currentFolderKey, true);
        } else {
            playlist = MediaStoreAudioLibrary.tracksInFolder(allTracks, track.folderKey, false);
        }
        if (playlist.isEmpty()) {
            playlist = new ArrayList<>();
            playlist.add(track);
        }
        MusicPlayer.get().playTracks(this, playlist, track);
        openPlayer(true);
    }

    private void startFolderPlayback(boolean recursive) {
        List<PlayableTrack> playlist;
        if (usingSaf) {
            playlist = SafAudioLibrary.audioFilesIn(
                    safCurrent != null ? safCurrent : safRoot, recursive);
        } else {
            playlist = MediaStoreAudioLibrary.tracksInFolder(allTracks, currentFolderKey, recursive);
        }
        if (playlist.isEmpty()) {
            Toast.makeText(this, R.string.folder_no_music, Toast.LENGTH_SHORT).show();
            return;
        }
        if (!usingSaf) {
            MusicFolderPrefs.saveFolderPath(this, currentFolderKey);
        }
        MusicPlayer.get().playTracks(this, playlist, null);
        openPlayer(true);
    }

    private void openPlayer(boolean alreadyQueued) {
        PlayableTrack current = MusicPlayer.get().getCurrentTrack();
        if (current == null) {
            Toast.makeText(this, R.string.folder_no_music, Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(this, PlayerActivity.class);
        intent.putExtra(PlayerActivity.EXTRA_TRACK_URI, current.uri.toString());
        intent.putExtra(PlayerActivity.EXTRA_ALREADY_QUEUED, alreadyQueued);
        startActivity(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (usingSaf) {
            refreshSafUi();
        } else if (hasAudioPermission()) {
            refreshLibraryAndUi();
        }
    }

    private void ensureAccessThenBrowse() {
        if (hasAudioPermission()) {
            refreshLibraryAndUi();
            return;
        }
        requestAudioPermission();
    }

    private boolean hasAudioPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO)
                    == PackageManager.PERMISSION_GRANTED;
        }
        return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestAudioPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.READ_MEDIA_AUDIO},
                    REQ_READ);
        } else {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                    REQ_READ);
        }
    }

    private void openAppSettings() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(Uri.parse("package:" + getPackageName()));
        startActivity(intent);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQ_READ) {
            return;
        }
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            refreshLibraryAndUi();
        } else {
            new AlertDialog.Builder(this)
                    .setMessage(R.string.folder_permission_needed)
                    .setPositiveButton(R.string.folder_open_settings, (d, w) -> openAppSettings())
                    .setNegativeButton(R.string.folder_pick_saf, (d, w) -> launchSafPicker())
                    .setNeutralButton(android.R.string.cancel, (d, w) -> finish())
                    .show();
        }
    }

    private void refreshLibraryAndUi() {
        usingSaf = false;
        allTracks = MediaStoreAudioLibrary.loadAll(this);
        currentFolderKey = MediaStoreAudioLibrary.normalizeFolderKey(currentFolderKey);
        if (!MediaStoreAudioLibrary.folderExistsInLibrary(allTracks, currentFolderKey)) {
            currentFolderKey = "";
        }
        openMediaFolder(currentFolderKey);
    }

    private void openMediaFolder(String folderKey) {
        currentFolderKey = folderKey != null ? folderKey : "";
        textCurrentPath.setText(currentFolderKey.isEmpty()
                ? getString(R.string.folder_root_label)
                : currentFolderKey);

        listEntries.clear();
        List<String> labels = new ArrayList<>();

        if (!currentFolderKey.isEmpty()) {
            listEntries.add(new BrowserEntry(BrowserEntry.Type.UP, null, null, null));
            labels.add("⬆ " + getString(R.string.folder_up));
        }

        boolean recursiveCounts = OPEN_FOLDER_ALL.equals(openMode);
        List<String> childFolders = currentFolderKey.isEmpty()
                ? MediaStoreAudioLibrary.rootAudioFolders(allTracks)
                : MediaStoreAudioLibrary.childFoldersWithAudio(allTracks, currentFolderKey);
        for (String child : childFolders) {
            String childKey = currentFolderKey.isEmpty() ? child : currentFolderKey + "/" + child;
            int count = MediaStoreAudioLibrary.countInFolder(allTracks, childKey, true);
            listEntries.add(new BrowserEntry(BrowserEntry.Type.FOLDER, childKey, null, null));
            labels.add("📁 " + child
                    + "  (" + getString(R.string.folder_music_count, count) + ")");
        }

        // At library root, only offer audio folders — do not dump every device track.
        if (!currentFolderKey.isEmpty() || !recursiveCounts) {
            List<PlayableTrack> tracksHere =
                    MediaStoreAudioLibrary.tracksInFolder(allTracks, currentFolderKey, recursiveCounts);
            for (PlayableTrack track : tracksHere) {
                listEntries.add(new BrowserEntry(BrowserEntry.Type.TRACK, track.folderKey, track, null));
                labels.add("🎵 " + labelForListedTrack(track, currentFolderKey));
            }
        }

        int musicHere;
        if (currentFolderKey.isEmpty()) {
            musicHere = 0;
            for (String child : childFolders) {
                musicHere += MediaStoreAudioLibrary.countInFolder(
                        allTracks, child, true);
            }
        } else {
            musicHere = MediaStoreAudioLibrary.countInFolder(
                    allTracks, currentFolderKey, recursiveCounts);
        }
        if (recursiveCounts) {
            textMusicCount.setText(getString(R.string.folder_music_count_recursive, musicHere));
        } else {
            textMusicCount.setText(getString(R.string.folder_music_count, musicHere));
        }

        applyList(labels);
    }

    private String labelForListedTrack(PlayableTrack track, String folderKey) {
        String name = track.displayName != null && !track.displayName.isEmpty()
                ? track.displayName
                : track.title;
        if (!OPEN_FOLDER_ALL.equals(openMode)) {
            return name;
        }
        String prefix = folderKey == null || folderKey.isEmpty() ? "" : folderKey + "/";
        if (track.folderKey != null && !track.folderKey.isEmpty()
                && !track.folderKey.equals(folderKey)) {
            String relative = prefix.isEmpty() || !track.folderKey.startsWith(prefix)
                    ? track.folderKey
                    : track.folderKey.substring(prefix.length());
            if (!relative.isEmpty()) {
                return relative + "/" + name;
            }
        }
        return name;
    }

    private void refreshSafUi() {
        if (safTreeUri == null) {
            usingSaf = false;
            refreshLibraryAndUi();
            return;
        }
        safRoot = SafAudioLibrary.root(this, safTreeUri);
        if (safRoot == null || !safRoot.exists()) {
            usingSaf = false;
            Toast.makeText(this, R.string.folder_pick_saf_hint, Toast.LENGTH_LONG).show();
            refreshLibraryAndUi();
            return;
        }
        safCurrent = SafAudioLibrary.navigate(safRoot, safSegments);
        if (safCurrent == null) {
            safSegments.clear();
            safCurrent = safRoot;
        }

        textCurrentPath.setText(SafAudioLibrary.displayPath(safRoot, safSegments));

        listEntries.clear();
        List<String> labels = new ArrayList<>();

        listEntries.add(new BrowserEntry(BrowserEntry.Type.UP, null, null, null));
        if (safSegments.isEmpty()) {
            labels.add("⬆ " + getString(R.string.folder_library_mode));
        } else {
            labels.add("⬆ " + getString(R.string.folder_up));
        }

        boolean recursive = OPEN_FOLDER_ALL.equals(openMode);
        for (DocumentFile child : SafAudioLibrary.childDirectories(safCurrent)) {
            listEntries.add(new BrowserEntry(BrowserEntry.Type.FOLDER, child.getName(), null, child));
            int count = SafAudioLibrary.audioFilesIn(child, recursive).size();
            String name = child.getName() != null ? child.getName() : "?";
            if (count > 0) {
                labels.add("📁 " + name
                        + "  (" + getString(R.string.folder_music_count, count) + ")");
            } else {
                labels.add("📁 " + name);
            }
        }

        List<PlayableTrack> tracksHere = SafAudioLibrary.audioFilesIn(safCurrent, recursive);
        for (PlayableTrack track : tracksHere) {
            listEntries.add(new BrowserEntry(BrowserEntry.Type.TRACK, track.folderKey, track, null));
            labels.add("🎵 " + (track.displayName != null ? track.displayName : track.title));
        }

        int musicHere = SafAudioLibrary.audioFilesIn(safCurrent, recursive).size();
        if (recursive) {
            textMusicCount.setText(getString(R.string.folder_music_count_recursive, musicHere));
        } else {
            textMusicCount.setText(getString(R.string.folder_music_count, musicHere));
        }

        applyList(labels);
    }

    private void applyList(List<String> labels) {
        adapter.clear();
        adapter.addAll(labels);
        adapter.notifyDataSetChanged();

        boolean empty = adapter.getCount() == 0;
        textEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        listFolders.setVisibility(empty ? View.GONE : View.VISIBLE);
    }
}
