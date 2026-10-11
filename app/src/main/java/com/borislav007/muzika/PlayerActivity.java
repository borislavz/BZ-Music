package com.borislav007.muzika;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
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
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
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
    private static final int TAG_YEAR_COLOR = 0xFF2E7D32;
    private static final int ONLINE_RELEASE_COLOR = 0xFF6A1B9A;
    private static final int FILE_DATE_COLOR = 0xFFC62828;

    private TextView textTrackTitle;
    private TextView textTrackInfo;
    private TextView textPosition;
    private TextView textDuration;
    private ImageView imageAlbumArt;
    private MaterialButton buttonPrevious;
    private MaterialButton buttonPlayPause;
    private MaterialButton buttonNext;
    private MaterialButton buttonTogglePlaylist;
    private MaterialButton buttonSortName;
    private MaterialButton buttonSortAuthor;
    private MaterialButton buttonSortDate;
    private SeekBar seekProgress;
    private View panelNowPlaying;
    private View panelPlaylist;
    private ListView listPlaylist;

    private PlaylistAdapter playlistAdapter;
    private boolean showingPlaylist;
    private enum ActiveSort { NONE, NAME, AUTHOR, DATE }
    private ActiveSort activeSort = ActiveSort.NONE;
    private boolean nameAscending = true;
    private boolean authorAscending = true;
    private boolean dateNewest = true;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService artExecutor = Executors.newSingleThreadExecutor();
    private final AtomicInteger artRequestId = new AtomicInteger();
    private final AtomicInteger enrichRequestId = new AtomicInteger();
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

        // Start | About | List/Song info | Exit — equal width.
        AppNavigation.bindAppBar(this, R.string.player_title, false, true, true);
        requestNotificationPermissionIfNeeded();
        buttonTogglePlaylist = findViewById(R.id.buttonTogglePlaylist);
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
        buttonSortName = findViewById(R.id.buttonSortName);
        buttonSortAuthor = findViewById(R.id.buttonSortAuthor);
        buttonSortDate = findViewById(R.id.buttonSortDate);

        playlistAdapter = new PlaylistAdapter(this);
        listPlaylist.setAdapter(playlistAdapter);
        listPlaylist.setOnItemClickListener((parent, view, position, id) -> {
            if (position == MusicPlayer.get().getQueueIndex()) {
                return;
            }
            MusicPlayer.get().playAtQueueIndex(position);
        });

        if (buttonSortName != null) {
            buttonSortName.setOnClickListener(v -> onSortNameClicked());
        }
        if (buttonSortAuthor != null) {
            buttonSortAuthor.setOnClickListener(v -> onSortAuthorClicked());
        }
        if (buttonSortDate != null) {
            buttonSortDate.setOnClickListener(v -> onSortDateClicked());
        }
        updateSortButtonsUi();

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
            bindExistingSession(current);
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

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        MusicPlayer.get().setListener(this);
        PlayableTrack current = MusicPlayer.get().getCurrentTrack();
        if (current != null) {
            bindExistingSession(current);
        }
    }

    private void bindExistingSession(PlayableTrack current) {
        showMetadata(current);
        setPlayPauseUi(true);
        updateTransportButtons();
        refreshPlaylistUi(showingPlaylist);
    }

    void onQueueOrderChanged() {
        if (PlaybackPrefs.isShuffleEnabled(this) && activeSort != ActiveSort.NONE) {
            activeSort = ActiveSort.NONE;
            nameAscending = true;
            authorAscending = true;
            dateNewest = true;
            updateSortButtonsUi();
        }
        refreshPlaylistUi(showingPlaylist);
        updateTransportButtons();
    }

    private void onSortNameClicked() {
        PlaybackControlsBinder.turnShuffleOff(this);
        if (activeSort != ActiveSort.NAME) {
            activeSort = ActiveSort.NAME;
            nameAscending = true;
        } else {
            nameAscending = !nameAscending;
        }
        MusicPlayer.get().sortQueueByName(nameAscending);
        updateSortButtonsUi();
        onQueueOrderChanged();
    }

    private void onSortAuthorClicked() {
        PlaybackControlsBinder.turnShuffleOff(this);
        if (activeSort != ActiveSort.AUTHOR) {
            activeSort = ActiveSort.AUTHOR;
            authorAscending = true;
        } else {
            authorAscending = !authorAscending;
        }
        MusicPlayer.get().sortQueueByAuthor(authorAscending);
        updateSortButtonsUi();
        onQueueOrderChanged();
    }

    private void onSortDateClicked() {
        PlaybackControlsBinder.turnShuffleOff(this);
        if (activeSort != ActiveSort.DATE) {
            activeSort = ActiveSort.DATE;
            dateNewest = true;
        } else {
            dateNewest = !dateNewest;
        }
        MusicPlayer.get().sortQueueByReleaseDate(dateNewest);
        updateSortButtonsUi();
        onQueueOrderChanged();
    }

    private void updateSortButtonsUi() {
        if (buttonSortName != null) {
            boolean nameActive = activeSort == ActiveSort.NAME;
            buttonSortName.setChecked(nameActive);
            buttonSortName.setText(nameActive && !nameAscending
                    ? R.string.player_sort_name_za
                    : R.string.player_sort_name_az);
        }
        if (buttonSortAuthor != null) {
            boolean authorActive = activeSort == ActiveSort.AUTHOR;
            buttonSortAuthor.setChecked(authorActive);
            buttonSortAuthor.setText(authorActive && !authorAscending
                    ? R.string.player_sort_author_za
                    : R.string.player_sort_author_az);
        }
        if (buttonSortDate != null) {
            boolean dateActive = activeSort == ActiveSort.DATE;
            buttonSortDate.setChecked(dateActive);
            buttonSortDate.setText(dateActive && !dateNewest
                    ? R.string.player_sort_oldest
                    : R.string.player_sort_newest);
        }
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
        enrichReleaseDatesAsync(queue);
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

    /**
     * Probe YEAR/DATE tags, then online album year, then leave Date Modified.
     * Updates list (and re-applies Newest/Oldest) when values change.
     */
    private void enrichReleaseDatesAsync(List<PlayableTrack> tracks) {
        if (tracks == null || tracks.isEmpty()) {
            return;
        }
        boolean needsWork = false;
        for (PlayableTrack track : tracks) {
            if (track != null && (track.needsTagProbe() || track.needsOnlineYearLookup())) {
                needsWork = true;
                break;
            }
        }
        if (!needsWork) {
            return;
        }
        final int requestId = enrichRequestId.incrementAndGet();
        final List<PlayableTrack> snapshot = new ArrayList<>(tracks);
        artExecutor.execute(() -> {
            boolean changed = false;
            for (PlayableTrack track : snapshot) {
                if (isFinishing() || requestId != enrichRequestId.get()) {
                    return;
                }
                if (track == null) {
                    continue;
                }
                String before = track.releaseDateDisplay();
                if (track.needsTagProbe()) {
                    TrackReleaseDate.TagRelease tag = TrackReleaseDate.readFromUri(
                            PlayerActivity.this, track.uri, track.dateModifiedMillis);
                    track.applyTagRelease(tag);
                }
                if (track.needsOnlineYearLookup()) {
                    TrackReleaseDate.TagRelease online =
                            OnlineAlbumArtLookup.findAlbumReleaseDate(
                                    track.artist, track.album);
                    track.applyOnlineRelease(online);
                }
                if (!before.equals(track.releaseDateDisplay())) {
                    changed = true;
                }
            }
            if (!changed || isFinishing() || requestId != enrichRequestId.get()) {
                return;
            }
            handler.post(() -> {
                if (isFinishing() || requestId != enrichRequestId.get()) {
                    return;
                }
                if (activeSort == ActiveSort.DATE) {
                    MusicPlayer.get().sortQueueByReleaseDate(dateNewest);
                }
                List<PlayableTrack> queue = MusicPlayer.get().getQueueSnapshot();
                playlistAdapter.setTracks(queue, MusicPlayer.get().getQueueIndex());
                PlayableTrack current = MusicPlayer.get().getCurrentTrack();
                if (current != null && !showingPlaylist) {
                    showMetadata(current);
                }
            });
        });
    }

    private static int releaseDateColor(PlayableTrack track) {
        if (track == null) {
            return 0;
        }
        if (track.hasTaggedRelease()) {
            return TAG_YEAR_COLOR;
        }
        if (track.isOnlineRelease()) {
            return ONLINE_RELEASE_COLOR;
        }
        if (track.isDateModifiedFallback()) {
            return FILE_DATE_COLOR;
        }
        return 0;
    }

    private String releaseDateLabel(PlayableTrack track) {
        if (track != null && track.isDateModifiedFallback()) {
            return getString(R.string.player_date_modified);
        }
        return getString(R.string.player_release_date);
    }

    private void appendReleaseInfo(SpannableStringBuilder info, PlayableTrack track,
                                   String releaseDate) {
        if (track == null) {
            return;
        }
        appendInfo(info, releaseDateLabel(track), releaseDate, releaseDateColor(track),
                track.isReleaseValueBold());
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
        SpannableStringBuilder info = new SpannableStringBuilder();
        Bitmap albumArt = null;
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(this, uri);
            String metaTitle = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
            String metaArtist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
            String metaAlbum = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM);
            String genre = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE);
            String bitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE);
            String duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);

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

            long dateModifiedMillis = known != null ? known.dateModifiedMillis : 0L;
            TrackReleaseDate.TagRelease tag =
                    TrackReleaseDate.readFromRetriever(retriever, dateModifiedMillis);
            if (known != null) {
                known.applyTagRelease(tag);
            }
            String releaseDate = known != null
                    ? known.releaseDateDisplay()
                    : TrackReleaseDate.displayValue(tag.millis, tag.yearOnly, dateModifiedMillis);
            if (known != null) {
                appendReleaseInfo(info, known, releaseDate);
            } else {
                boolean modifiedFallback = !tag.isPresent() && dateModifiedMillis > 0L;
                appendInfo(info,
                        getString(modifiedFallback
                                ? R.string.player_date_modified
                                : R.string.player_release_date),
                        releaseDate,
                        modifiedFallback ? FILE_DATE_COLOR
                                : (tag.isPresent() ? TAG_YEAR_COLOR : 0),
                        tag.isPresent());
            }

            if (bitrate != null) {
                try {
                    int kbps = Integer.parseInt(bitrate) / 1000;
                    appendInfo(info, getString(R.string.player_bitrate), kbps + " kbps", 0, false);
                } catch (NumberFormatException ignored) {
                }
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
            appendInfo(info, getString(R.string.player_artist), artist);
            appendInfo(info, getString(R.string.player_album), album);
            if (known != null) {
                appendReleaseInfo(info, known, known.releaseDateDisplay());
            }
        } finally {
            try {
                retriever.release();
            } catch (IOException ignored) {
            }
        }

        textTrackTitle.setText(title);
        textTrackInfo.setText(info.length() == 0
                ? getString(R.string.player_no_metadata)
                : info);

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

    private void appendInfo(SpannableStringBuilder builder, String label, String value) {
        appendInfo(builder, label, value, 0, false);
    }

    private void appendInfo(SpannableStringBuilder builder, String label, String value,
                            int valueColor, boolean valueBold) {
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
        builder.append(label).append(": ");
        int valueStart = builder.length();
        builder.append(trimmed);
        if (valueColor != 0) {
            builder.setSpan(new ForegroundColorSpan(valueColor), valueStart, builder.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        if (valueBold) {
            builder.setSpan(new StyleSpan(Typeface.BOLD), valueStart, builder.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
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
            TextView artistView = view.findViewById(R.id.textPlaylistArtist);
            View albumRow = view.findViewById(R.id.rowPlaylistAlbum);
            TextView albumView = view.findViewById(R.id.textPlaylistAlbum);
            TextView dateView = view.findViewById(R.id.textPlaylistReleaseDate);
            String title = track != null && track.title != null && !track.title.trim().isEmpty()
                    ? track.title.trim()
                    : (track != null ? track.displayName : "");
            titleView.setText(title);

            String artist = usablePlaylistMeta(track != null ? track.artist : null);
            if (!artist.isEmpty()) {
                artistView.setVisibility(View.VISIBLE);
                artistView.setText(getContext().getString(R.string.player_list_artist, artist));
            } else {
                artistView.setVisibility(View.GONE);
                artistView.setText("");
            }

            String album = usablePlaylistMeta(track != null ? track.album : null);
            String albumLine = "";
            if (!album.isEmpty()) {
                albumLine = getContext().getString(R.string.player_list_album, album);
            } else {
                String folder = folderLabel(track != null ? track.folderKey : null);
                if (!folder.isEmpty()) {
                    albumLine = getContext().getString(R.string.player_list_folder, folder);
                }
            }
            String releaseDate = track != null ? track.releaseDateDisplay() : "";
            if (!albumLine.isEmpty() || !releaseDate.isEmpty()) {
                albumRow.setVisibility(View.VISIBLE);
                albumView.setText(albumLine);
                albumView.setVisibility(albumLine.isEmpty() ? View.GONE : View.VISIBLE);
                if (!releaseDate.isEmpty()) {
                    dateView.setVisibility(View.VISIBLE);
                    dateView.setText(releaseDate);
                    int dateColor = releaseDateColor(track);
                    dateView.setTextColor(dateColor != 0 ? dateColor : 0xFF4A6267);
                    dateView.setTypeface(null,
                            track != null && track.isReleaseValueBold()
                                    ? Typeface.BOLD : Typeface.NORMAL);
                } else {
                    dateView.setVisibility(View.GONE);
                    dateView.setText("");
                }
            } else {
                albumRow.setVisibility(View.GONE);
                albumView.setText("");
                dateView.setText("");
            }

            boolean isCurrent = position == currentIndex;
            view.setBackgroundColor(isCurrent ? 0xFFB2DFDB : 0x00000000);
            titleView.setTypeface(null, isCurrent ? Typeface.BOLD : Typeface.NORMAL);
            return view;
        }

        private static String usablePlaylistMeta(String value) {
            if (value == null) {
                return "";
            }
            String trimmed = value.trim();
            if (trimmed.isEmpty() || "<unknown>".equalsIgnoreCase(trimmed)
                    || "unknown".equalsIgnoreCase(trimmed)) {
                return "";
            }
            return trimmed;
        }

        private static String folderLabel(String folderKey) {
            if (folderKey == null) {
                return "";
            }
            String key = folderKey.trim();
            if (key.isEmpty()) {
                return "";
            }
            int slash = key.lastIndexOf('/');
            if (slash >= 0 && slash < key.length() - 1) {
                return key.substring(slash + 1);
            }
            return key;
        }
    }
}
