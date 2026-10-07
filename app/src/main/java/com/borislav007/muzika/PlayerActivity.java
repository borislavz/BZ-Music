package com.borislav007.muzika;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.google.android.material.button.MaterialButton;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class PlayerActivity extends AppCompatActivity implements MusicPlayer.Listener {
    public static final String EXTRA_TRACK_URI = "track_uri";
    public static final String EXTRA_ALREADY_QUEUED = "already_queued";

    private TextView textTrackTitle;
    private TextView textTrackInfo;
    private TextView textPosition;
    private TextView textDuration;
    private ImageView imageAlbumArt;
    private MaterialButton buttonPrevious;
    private MaterialButton buttonPlayPause;
    private MaterialButton buttonNext;
    private MaterialButton buttonTogglePlaylist;
    private SeekBar seekProgress;
    private View panelNowPlaying;
    private View panelPlaylist;
    private ListView listPlaylist;

    private PlaylistAdapter playlistAdapter;
    private boolean showingPlaylist;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService artExecutor = Executors.newSingleThreadExecutor();
    private final AtomicInteger artRequestId = new AtomicInteger();
    private boolean userSeeking;
    private final Runnable progressRunnable = new Runnable() {
        @Override
        public void run() {
            updateProgressUi();
            handler.postDelayed(this, 400);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);

        // No back arrow: Start is leftmost, List/File Info centered, Exit rightmost.
        AppNavigation.bindAppBar(this, R.string.player_title, false, false, true);
        requestNotificationPermissionIfNeeded();
        // Replace the "Now Playing" title with centered List / File Info toggle.
        TextView titleView = findViewById(R.id.textToolbarTitle);
        if (titleView != null) {
            titleView.setVisibility(View.GONE);
        }
        buttonTogglePlaylist = findViewById(R.id.buttonAbout);
        if (buttonTogglePlaylist != null) {
            buttonTogglePlaylist.setVisibility(View.VISIBLE);
            buttonTogglePlaylist.setText(R.string.player_show_list);
            buttonTogglePlaylist.setOnClickListener(v -> setPlaylistVisible(!showingPlaylist));
        }

        textTrackTitle = findViewById(R.id.textTrackTitle);
        textTrackInfo = findViewById(R.id.textTrackInfo);
        textPosition = findViewById(R.id.textPosition);
        textDuration = findViewById(R.id.textDuration);
        imageAlbumArt = findViewById(R.id.imageAlbumArt);
        buttonPrevious = findViewById(R.id.buttonPrevious);
        buttonPlayPause = findViewById(R.id.buttonPlayPause);
        buttonNext = findViewById(R.id.buttonNext);
        seekProgress = findViewById(R.id.seekProgress);
        panelNowPlaying = findViewById(R.id.panelNowPlaying);
        panelPlaylist = findViewById(R.id.panelPlaylist);
        listPlaylist = findViewById(R.id.listPlaylist);

        playlistAdapter = new PlaylistAdapter(this);
        listPlaylist.setAdapter(playlistAdapter);
        listPlaylist.setOnItemClickListener((parent, view, position, id) -> {
            if (position == MusicPlayer.get().getQueueIndex()) {
                return;
            }
            MusicPlayer.get().playAtQueueIndex(position);
        });

        PlaybackControlsBinder.bind(this);

        buttonPlayPause.setOnClickListener(v -> togglePlayPause());
        buttonPrevious.setOnClickListener(v -> {
            MusicPlayer player = MusicPlayer.get();
            if (player.getPositionMs() > 3000) {
                player.seekTo(0);
            } else {
                player.playPrevious();
            }
        });
        buttonNext.setOnClickListener(v -> MusicPlayer.get().playNext());

        seekProgress.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser) {
                    return;
                }
                int duration = MusicPlayer.get().getDurationMs();
                if (duration > 0) {
                    int position = (int) ((progress / (float) seekBar.getMax()) * duration);
                    textPosition.setText(formatTime(position));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                userSeeking = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                int duration = MusicPlayer.get().getDurationMs();
                if (duration > 0) {
                    int position = (int) ((seekBar.getProgress() / (float) seekBar.getMax()) * duration);
                    MusicPlayer.get().seekTo(position);
                }
                userSeeking = false;
            }
        });

        MusicPlayer.get().setListener(this);
        boolean alreadyQueued = getIntent() != null
                && getIntent().getBooleanExtra(EXTRA_ALREADY_QUEUED, false);

        PlayableTrack current = MusicPlayer.get().getCurrentTrack();
        if (alreadyQueued && current != null) {
            showMetadata(current);
            setPlayPauseUi(true);
            updateTransportButtons();
            refreshPlaylistUi(false);
            return;
        }

        String uriString = getIntent() != null
                ? getIntent().getStringExtra(EXTRA_TRACK_URI) : null;
        if (uriString == null || uriString.isEmpty()) {
            Toast.makeText(this, R.string.player_error, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        if (current != null && uriString.equals(current.uri.toString())) {
            showMetadata(current);
        } else {
            showMetadataFromUri(Uri.parse(uriString), null);
        }
        setPlayPauseUi(true);
        updateTransportButtons();
        refreshPlaylistUi(false);
    }

    void onQueueOrderChanged() {
        refreshPlaylistUi(showingPlaylist);
        updateTransportButtons();
    }

    private void setPlaylistVisible(boolean showList) {
        showingPlaylist = showList;
        panelNowPlaying.setVisibility(showList ? View.GONE : View.VISIBLE);
        panelPlaylist.setVisibility(showList ? View.VISIBLE : View.GONE);
        if (buttonTogglePlaylist != null) {
            buttonTogglePlaylist.setText(showList
                    ? R.string.player_show_file_info
                    : R.string.player_show_list);
        }
        if (showList) {
            refreshPlaylistUi(true);
        }
    }

    private void refreshPlaylistUi(boolean scrollToCurrent) {
        List<PlayableTrack> queue = MusicPlayer.get().getQueueSnapshot();
        int currentIndex = MusicPlayer.get().getQueueIndex();
        playlistAdapter.setTracks(queue, currentIndex);
        if (buttonTogglePlaylist != null) {
            buttonTogglePlaylist.setEnabled(!queue.isEmpty());
        }
        if (queue.isEmpty()) {
            if (showingPlaylist) {
                setPlaylistVisible(false);
            }
            return;
        }
        if (showingPlaylist && scrollToCurrent && currentIndex >= 0 && currentIndex < queue.size()) {
            listPlaylist.post(() -> listPlaylist.smoothScrollToPosition(currentIndex));
        }
    }

    private void showMetadata(PlayableTrack track) {
        if (track == null) {
            return;
        }
        showMetadataFromUri(track.uri, track);
    }

    private void showMetadataFromUri(Uri uri, PlayableTrack known) {
        String title = known != null && known.title != null && !known.title.trim().isEmpty()
                ? known.title.trim()
                : (known != null ? known.displayName : getString(R.string.player_title));
        String artist = known != null ? known.artist : null;
        String album = known != null ? known.album : null;
        String displayName = known != null ? known.displayName : null;
        StringBuilder info = new StringBuilder();
        Bitmap albumArt = null;
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(this, uri);
            String metaTitle = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
            String metaArtist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
            String metaAlbum = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM);
            String genre = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE);
            String year = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR);
            String bitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE);
            String duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            String mime = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE);

            if (metaTitle != null && !metaTitle.trim().isEmpty()) {
                title = metaTitle.trim();
            }
            if (metaArtist != null && !metaArtist.trim().isEmpty()) {
                artist = metaArtist.trim();
            }
            if (metaAlbum != null && !metaAlbum.trim().isEmpty()) {
                album = metaAlbum.trim();
            }
            appendInfo(info, getString(R.string.player_artist), artist);
            appendInfo(info, getString(R.string.player_album), album);
            appendInfo(info, getString(R.string.player_genre), genre);
            appendInfo(info, getString(R.string.player_year), year);
            if (bitrate != null) {
                try {
                    int kbps = Integer.parseInt(bitrate) / 1000;
                    appendInfo(info, getString(R.string.player_bitrate), kbps + " kbps");
                } catch (NumberFormatException ignored) {
                }
            }
            if (mime != null && !mime.trim().isEmpty()) {
                appendInfo(info, getString(R.string.player_format), mime.trim());
            }
            if (displayName != null) {
                appendInfo(info, getString(R.string.player_file), displayName);
            }
            if (duration != null) {
                try {
                    textDuration.setText(formatTime(Integer.parseInt(duration)));
                } catch (NumberFormatException ignored) {
                }
            }

            byte[] picture = retriever.getEmbeddedPicture();
            if (picture != null && picture.length > 0) {
                albumArt = BitmapFactory.decodeByteArray(picture, 0, picture.length);
            }
        } catch (RuntimeException e) {
            if (displayName != null) {
                appendInfo(info, getString(R.string.player_file), displayName);
            }
            appendInfo(info, getString(R.string.player_artist), artist);
            appendInfo(info, getString(R.string.player_album), album);
        } finally {
            try {
                retriever.release();
            } catch (IOException ignored) {
            }
        }

        textTrackTitle.setText(title);
        textTrackInfo.setText(info.length() == 0
                ? getString(R.string.player_no_metadata)
                : info.toString().trim());

        final int requestId = artRequestId.incrementAndGet();
        if (albumArt != null) {
            imageAlbumArt.setImageBitmap(albumArt);
        } else {
            imageAlbumArt.setImageResource(R.drawable.ic_album_placeholder);
            final String lookupTitle = title;
            final String lookupArtist = artist;
            final String lookupAlbum = album;
            final String lookupFile = displayName;
            artExecutor.execute(() -> {
                Bitmap onlineArt = OnlineAlbumArtLookup.findCover(
                        lookupArtist, lookupAlbum, lookupTitle, lookupFile);
                if (onlineArt == null) {
                    return;
                }
                handler.post(() -> {
                    if (requestId != artRequestId.get() || isFinishing()) {
                        return;
                    }
                    imageAlbumArt.setImageBitmap(onlineArt);
                });
            });
        }
    }

    private void appendInfo(StringBuilder builder, String label, String value) {
        if (value == null) {
            return;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty() || "<unknown>".equalsIgnoreCase(trimmed)) {
            return;
        }
        if (builder.length() > 0) {
            builder.append('\n');
        }
        builder.append(label).append(": ").append(trimmed);
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return;
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) {
            return;
        }
        ActivityCompat.requestPermissions(
                this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, 3101);
    }

    private void togglePlayPause() {
        MusicPlayer player = MusicPlayer.get();
        if (!player.isPrepared()) {
            return;
        }
        if (player.isPlaying()) {
            player.pause();
            setPlayPauseUi(false);
        } else {
            player.play();
            setPlayPauseUi(true);
        }
    }

    private void setPlayPauseUi(boolean playing) {
        buttonPlayPause.setText(null);
        buttonPlayPause.setIconPadding(0);
        if (playing) {
            buttonPlayPause.setIconResource(R.drawable.ic_pause);
            buttonPlayPause.setContentDescription(getString(R.string.player_pause));
        } else {
            buttonPlayPause.setIconResource(R.drawable.ic_play);
            buttonPlayPause.setContentDescription(getString(R.string.player_play));
        }
    }

    private void updateTransportButtons() {
        MusicPlayer player = MusicPlayer.get();
        // Keep Previous enabled so restart/previous works like Bluetooth controls.
        buttonPrevious.setEnabled(player.isPrepared()
                && (player.hasPrevious() || player.getPositionMs() > 0));
        buttonNext.setEnabled(player.hasNext());
    }

    private void updateProgressUi() {
        MusicPlayer player = MusicPlayer.get();
        if (!player.isPrepared() || userSeeking) {
            return;
        }
        int duration = player.getDurationMs();
        int position = player.getPositionMs();
        textPosition.setText(formatTime(position));
        if (duration > 0) {
            textDuration.setText(formatTime(duration));
            int progress = (int) ((position / (float) duration) * seekProgress.getMax());
            seekProgress.setProgress(progress);
        }
        setPlayPauseUi(player.isPlaying());
    }

    private static String formatTime(int millis) {
        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis);
        long seconds = TimeUnit.MILLISECONDS.toSeconds(millis)
                - TimeUnit.MINUTES.toSeconds(minutes);
        return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds);
    }

    @Override
    public void onTrackChanged(PlayableTrack track) {
        runOnUiThread(() -> {
            showMetadata(track);
            seekProgress.setProgress(0);
            textPosition.setText(formatTime(0));
            setPlayPauseUi(true);
            updateTransportButtons();
            refreshPlaylistUi(showingPlaylist);
        });
    }

    @Override
    public void onPrepared() {
        runOnUiThread(() -> {
            setPlayPauseUi(true);
            updateProgressUi();
            updateTransportButtons();
            handler.removeCallbacks(progressRunnable);
            handler.post(progressRunnable);
        });
    }

    @Override
    public void onPlaylistFinished() {
        runOnUiThread(() -> {
            setPlayPauseUi(false);
            seekProgress.setProgress(0);
            textPosition.setText(formatTime(0));
            updateTransportButtons();
            refreshPlaylistUi(false);
            Toast.makeText(this, R.string.player_playlist_finished, Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    public void onError(String message) {
        runOnUiThread(() ->
                Toast.makeText(this, message, Toast.LENGTH_LONG).show());
    }

    @Override
    protected void onResume() {
        super.onResume();
        MusicPlayer.get().setListener(this);
        handler.removeCallbacks(progressRunnable);
        handler.post(progressRunnable);
        PlayableTrack current = MusicPlayer.get().getCurrentTrack();
        if (current != null) {
            showMetadata(current);
        }
        updateProgressUi();
        updateTransportButtons();
        refreshPlaylistUi(showingPlaylist);
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(progressRunnable);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(progressRunnable);
        artRequestId.incrementAndGet();
        artExecutor.shutdownNow();
        MusicPlayer.get().setListener(null);
        if (isFinishing()) {
            MusicPlayer.get().pause();
        }
        super.onDestroy();
    }

    private static final class PlaylistAdapter extends ArrayAdapter<PlayableTrack> {
        private int currentIndex = -1;

        PlaylistAdapter(Context context) {
            super(context, 0, new ArrayList<>());
        }

        void setTracks(List<PlayableTrack> tracks, int currentIndex) {
            this.currentIndex = currentIndex;
            clear();
            if (tracks != null && !tracks.isEmpty()) {
                addAll(tracks);
            }
            notifyDataSetChanged();
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view = convertView;
            if (view == null) {
                view = LayoutInflater.from(getContext())
                        .inflate(R.layout.item_playlist_track, parent, false);
            }
            PlayableTrack track = getItem(position);
            TextView titleView = view.findViewById(R.id.textPlaylistTitle);
            TextView subtitleView = view.findViewById(R.id.textPlaylistSubtitle);
            String title = track != null && track.title != null && !track.title.trim().isEmpty()
                    ? track.title.trim()
                    : (track != null ? track.displayName : "");
            String artist = track != null && track.artist != null ? track.artist.trim() : "";
            if (artist.isEmpty() || "<unknown>".equalsIgnoreCase(artist)) {
                artist = track != null ? track.displayName : "";
            }
            titleView.setText(title);
            subtitleView.setText(artist);
            boolean isCurrent = position == currentIndex;
            view.setBackgroundColor(isCurrent ? 0xFFB2DFDB : 0x00000000);
            titleView.setTypeface(null, isCurrent ? Typeface.BOLD : Typeface.NORMAL);
            return view;
        }
    }
}
