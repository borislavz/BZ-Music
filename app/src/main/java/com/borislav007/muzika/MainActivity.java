package com.borislav007.muzika;

import android.Manifest;
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
import com.google.android.material.button.MaterialButton;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final int REQ_READ = 2001;

    private TextView textCurrentPath;
    private TextView textMusicCount;
    private TextView textEmpty;
    private ListView listFolders;
    private MaterialButton buttonPlayEverything;

    private String currentFolderKey = "";
    private List<PlayableTrack> allTracks = new ArrayList<>();
    private final List<BrowserEntry> listEntries = new ArrayList<>();
    private ArrayAdapter<String> adapter;

    private static final class BrowserEntry {
        enum Type { UP, FOLDER, TRACK }

        final Type type;
        final String folderKey;
        final PlayableTrack track;

        BrowserEntry(Type type, String folderKey, PlayableTrack track) {
            this.type = type;
            this.folderKey = folderKey;
            this.track = track;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        AppNavigation.bindAppBar(this, R.string.app_name, false, true, false);

        textCurrentPath = findViewById(R.id.textCurrentPath);
        textMusicCount = findViewById(R.id.textMusicCount);
        textEmpty = findViewById(R.id.textEmpty);
        listFolders = findViewById(R.id.listFolders);
        buttonPlayEverything = findViewById(R.id.buttonPlayEverything);

        adapter = new ArrayAdapter<>(this, R.layout.item_folder, android.R.id.text1, new ArrayList<>());
        listFolders.setAdapter(adapter);

        listFolders.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (position < 0 || position >= listEntries.size()) {
                    return;
                }
                BrowserEntry entry = listEntries.get(position);
                if (entry.type == BrowserEntry.Type.UP) {
                    String parentKey = MediaStoreAudioLibrary.parentFolderKey(currentFolderKey);
                    if (parentKey != null) {
                        openFolder(parentKey);
                    }
                } else if (entry.type == BrowserEntry.Type.FOLDER) {
                    openFolder(entry.folderKey);
                } else if (entry.type == BrowserEntry.Type.TRACK) {
                    startTrackPlayback(entry.track);
                }
            }
        });

        buttonPlayEverything.setOnClickListener(v -> startFolderPlayback());
        PlaybackControlsBinder.bind(this);
        ensureAccessThenBrowse();

        // Safety net: if Main was opened while a session is already running (e.g. splash/launcher
        // race), restore the player UI unless Start explicitly requested the folder browser.
        if (savedInstanceState == null && shouldRestorePlayerUi()) {
            openPlayer(true);
        }
    }

    private boolean shouldRestorePlayerUi() {
        if (!MusicPlayer.get().hasRestorableSession()) {
            return false;
        }
        Intent intent = getIntent();
        return intent == null
                || !intent.getBooleanExtra(AppNavigation.EXTRA_OPEN_INITIAL, false);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent != null && intent.getBooleanExtra(AppNavigation.EXTRA_OPEN_INITIAL, false)) {
            currentFolderKey = "";
            intent.removeExtra(AppNavigation.EXTRA_OPEN_INITIAL);
            if (hasAudioPermission()) {
                refreshLibraryAndUi();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Intent intent = getIntent();
        if (intent != null && intent.getBooleanExtra(AppNavigation.EXTRA_OPEN_INITIAL, false)) {
            currentFolderKey = "";
            intent.removeExtra(AppNavigation.EXTRA_OPEN_INITIAL);
        }
        if (hasAudioPermission()) {
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
                    .setNeutralButton(android.R.string.cancel, null)
                    .show();
        }
    }

    private void refreshLibraryAndUi() {
        allTracks = MediaStoreAudioLibrary.loadAll(this);
        currentFolderKey = MediaStoreAudioLibrary.normalizeFolderKey(currentFolderKey);
        if (!MediaStoreAudioLibrary.folderExistsInLibrary(allTracks, currentFolderKey)
                && !isAllowedRootOrDescendant(currentFolderKey)) {
            currentFolderKey = "";
        }
        openFolder(currentFolderKey);
    }

    private boolean isAllowedRootOrDescendant(String folderKey) {
        if (folderKey == null || folderKey.isEmpty()) {
            return true;
        }
        int slash = folderKey.indexOf('/');
        String root = slash < 0 ? folderKey : folderKey.substring(0, slash);
        return MediaStoreAudioLibrary.isAllowedRootAudioFolder(root);
    }

    private void openFolder(String folderKey) {
        currentFolderKey = folderKey != null ? folderKey : "";
        textCurrentPath.setText(currentFolderKey.isEmpty()
                ? getString(R.string.folder_root_label)
                : currentFolderKey);

        listEntries.clear();
        List<String> labels = new ArrayList<>();

        if (!currentFolderKey.isEmpty()) {
            listEntries.add(new BrowserEntry(BrowserEntry.Type.UP, null, null));
            labels.add("⬆ " + getString(R.string.folder_up));
        }

        List<String> childFolders = currentFolderKey.isEmpty()
                ? MediaStoreAudioLibrary.rootAudioFolders(allTracks)
                : MediaStoreAudioLibrary.childFoldersWithAudio(allTracks, currentFolderKey);
        for (String child : childFolders) {
            String childKey = currentFolderKey.isEmpty() ? child : currentFolderKey + "/" + child;
            int count = MediaStoreAudioLibrary.countInFolder(allTracks, childKey, true);
            listEntries.add(new BrowserEntry(BrowserEntry.Type.FOLDER, childKey, null));
            labels.add("📁 " + child
                    + "  (" + getString(R.string.folder_music_count, count) + ")");
        }

        if (!currentFolderKey.isEmpty()) {
            List<PlayableTrack> tracksHere =
                    MediaStoreAudioLibrary.tracksInFolder(allTracks, currentFolderKey, false);
            for (PlayableTrack track : tracksHere) {
                listEntries.add(new BrowserEntry(BrowserEntry.Type.TRACK, track.folderKey, track));
                String name = track.displayName != null && !track.displayName.isEmpty()
                        ? track.displayName
                        : track.title;
                labels.add("🎵 " + name);
            }
        }

        int musicHere;
        if (currentFolderKey.isEmpty()) {
            musicHere = 0;
            for (String child : childFolders) {
                musicHere += MediaStoreAudioLibrary.countInFolder(allTracks, child, true);
            }
        } else {
            musicHere = MediaStoreAudioLibrary.countInFolder(allTracks, currentFolderKey, true);
        }
        textMusicCount.setText(getString(R.string.folder_music_count_recursive, musicHere));

        adapter.clear();
        adapter.addAll(labels);
        adapter.notifyDataSetChanged();

        boolean empty = adapter.getCount() == 0;
        textEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        listFolders.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    private void startTrackPlayback(PlayableTrack track) {
        if (track == null) {
            return;
        }
        MusicFolderPrefs.saveFolderPath(this, track.folderKey);
        List<PlayableTrack> playlist =
                MediaStoreAudioLibrary.tracksInFolder(allTracks, track.folderKey, false);
        if (playlist.isEmpty()) {
            playlist = new ArrayList<>();
            playlist.add(track);
        }
        MusicPlayer.get().playTracks(this, playlist, track);
        openPlayer(true);
    }

    private void startFolderPlayback() {
        List<PlayableTrack> playlist;
        if (currentFolderKey.isEmpty()) {
            playlist = new ArrayList<>();
            for (String child : MediaStoreAudioLibrary.rootAudioFolders(allTracks)) {
                playlist.addAll(MediaStoreAudioLibrary.tracksInFolder(allTracks, child, true));
            }
        } else {
            playlist = MediaStoreAudioLibrary.tracksInFolder(allTracks, currentFolderKey, true);
        }
        if (playlist.isEmpty()) {
            Toast.makeText(this, R.string.folder_no_music, Toast.LENGTH_SHORT).show();
            return;
        }
        MusicFolderPrefs.saveFolderPath(this, currentFolderKey);
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
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
    }
}
