package com.borislav007.muzika;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.Media;

/**
 * App-wide player with playlist, shuffle, and repeat support.
 * Uses Android MediaPlayer for common formats and LibVLC for WMA.
 */
final class MusicPlayer {
    interface Listener {
        void onPrepared();

        void onTrackChanged(PlayableTrack track);

        void onPlaylistFinished();

        void onError(String message);
    }

    private static MusicPlayer instance;

    private android.media.MediaPlayer mediaPlayer;
    private LibVLC libVlc;
    private org.videolan.libvlc.MediaPlayer vlcPlayer;
    private boolean usingVlc;
    private AssetFileDescriptor vlcAfd;
    private ParcelFileDescriptor vlcPfd;
    private File vlcTempFile;

    private Context appContext;
    private PlayableTrack currentTrack;
    private Listener listener;
    private boolean prepared;

    private final List<PlayableTrack> libraryTracks = new ArrayList<>();
    private final List<PlayableTrack> playQueue = new ArrayList<>();
    private int queueIndex = -1;

    private PlaybackMediaSession mediaSession;
    private AudioManager audioManager;
    private AudioFocusRequest audioFocusRequest;
    private boolean hasAudioFocus;

    private final AudioManager.OnAudioFocusChangeListener audioFocusChangeListener =
            focusChange -> {
                if (focusChange == AudioManager.AUDIOFOCUS_LOSS
                        || focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                    pause();
                    notifyMediaSessionPlayPause();
                } else if (focusChange == AudioManager.AUDIOFOCUS_GAIN) {
                    play();
                    notifyMediaSessionPlayPause();
                }
            };

    static synchronized MusicPlayer get() {
        if (instance == null) {
            instance = new MusicPlayer();
        }
        return instance;
    }

    private MusicPlayer() {
    }

    void setListener(Listener listener) {
        this.listener = listener;
    }

    PlayableTrack getCurrentTrack() {
        return currentTrack;
    }

    /** Snapshot of the current play order (respects shuffle). */
    List<PlayableTrack> getQueueSnapshot() {
        return new ArrayList<>(playQueue);
    }

    int getQueueIndex() {
        return queueIndex;
    }

    int getQueueSize() {
        return playQueue.size();
    }

    /** Jump to a track in the current queue and start playback. */
    void playAtQueueIndex(int index) {
        if (index < 0 || index >= playQueue.size()) {
            return;
        }
        queueIndex = index;
        playCurrent();
    }

    /** Sort the play queue by filename; keeps the current track as current. */
    void sortQueueByName(boolean ascending) {
        if (playQueue.isEmpty()) {
            return;
        }
        PlayableTrack keep = currentTrack;
        final int dir = ascending ? 1 : -1;
        Collections.sort(playQueue, (a, b) -> dir * compareText(a.displayName, b.displayName));
        queueIndex = keep != null ? indexOfTrack(playQueue, keep) : 0;
        if (queueIndex < 0) {
            queueIndex = 0;
        }
    }

    /**
     * Sort the play queue by artist; if artist is missing, use album.
     * Keeps the current track as current.
     */
    void sortQueueByAuthor(boolean ascending) {
        if (playQueue.isEmpty()) {
            return;
        }
        PlayableTrack keep = currentTrack;
        final int dir = ascending ? 1 : -1;
        Collections.sort(playQueue, (a, b) -> {
            int byKey = dir * compareText(artistOrAlbumKey(a), artistOrAlbumKey(b));
            if (byKey != 0) {
                return byKey;
            }
            return dir * compareText(a.displayName, b.displayName);
        });
        queueIndex = keep != null ? indexOfTrack(playQueue, keep) : 0;
        if (queueIndex < 0) {
            queueIndex = 0;
        }
    }

    /**
     * Sort by release year/TYER, else file date. {@code newestFirst} true = Newest.
     * Keeps the current track as current.
     */
    void sortQueueByReleaseDate(boolean newestFirst) {
        if (playQueue.isEmpty()) {
            return;
        }
        PlayableTrack keep = currentTrack;
        final int dir = newestFirst ? -1 : 1;
        Collections.sort(playQueue, (a, b) -> {
            long keyA = a != null ? a.releaseSortKey() : 0L;
            long keyB = b != null ? b.releaseSortKey() : 0L;
            int byDate = Long.compare(keyA, keyB);
            if (byDate != 0) {
                return dir * byDate;
            }
            return compareText(
                    a != null ? a.displayName : "",
                    b != null ? b.displayName : "");
        });
        queueIndex = keep != null ? indexOfTrack(playQueue, keep) : 0;
        if (queueIndex < 0) {
            queueIndex = 0;
        }
    }

    private static String artistOrAlbumKey(PlayableTrack track) {
        if (track == null) {
            return "";
        }
        String artist = usableMeta(track.artist);
        if (!artist.isEmpty()) {
            return artist;
        }
        return usableMeta(track.album);
    }

    private static String usableMeta(String value) {
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

    private static int compareText(String left, String right) {
        String a = left != null ? left.trim() : "";
        String b = right != null ? right.trim() : "";
        if (a.isEmpty() && b.isEmpty()) {
            return 0;
        }
        if (a.isEmpty()) {
            return 1;
        }
        if (b.isEmpty()) {
            return -1;
        }
        return a.compareToIgnoreCase(b);
    }

    PlaybackMediaSession mediaSessionOrNull() {
        return mediaSession;
    }

    void notifyMediaSessionPlayPause() {
        if (mediaSession != null) {
            mediaSession.onPlayPauseChanged();
        }
    }

    private void ensureMediaSession() {
        if (appContext == null) {
            return;
        }
        if (mediaSession == null) {
            mediaSession = new PlaybackMediaSession(appContext);
        }
        if (audioManager == null) {
            audioManager = (AudioManager) appContext.getSystemService(Context.AUDIO_SERVICE);
        }
    }

    private boolean requestAudioFocus() {
        ensureMediaSession();
        if (audioManager == null) {
            return true;
        }
        int result;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (audioFocusRequest == null) {
                AudioAttributes attrs = new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build();
                audioFocusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                        .setAudioAttributes(attrs)
                        .setOnAudioFocusChangeListener(audioFocusChangeListener)
                        .build();
            }
            result = audioManager.requestAudioFocus(audioFocusRequest);
        } else {
            result = audioManager.requestAudioFocus(audioFocusChangeListener,
                    AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
        }
        hasAudioFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
        return hasAudioFocus;
    }

    private void abandonAudioFocus() {
        if (audioManager == null || !hasAudioFocus) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && audioFocusRequest != null) {
            audioManager.abandonAudioFocusRequest(audioFocusRequest);
        } else {
            audioManager.abandonAudioFocus(audioFocusChangeListener);
        }
        hasAudioFocus = false;
    }

    boolean isPlaying() {
        if (!prepared) {
            return false;
        }
        if (usingVlc) {
            return vlcPlayer != null && vlcPlayer.isPlaying();
        }
        return mediaPlayer != null && mediaPlayer.isPlaying();
    }

    boolean isPrepared() {
        if (usingVlc) {
            return vlcPlayer != null && prepared;
        }
        return mediaPlayer != null && prepared;
    }

    int getDurationMs() {
        if (!prepared) {
            return 0;
        }
        try {
            if (usingVlc) {
                if (vlcPlayer == null) {
                    return 0;
                }
                long length = vlcPlayer.getLength();
                return length > 0 ? (int) length : 0;
            }
            return mediaPlayer != null ? mediaPlayer.getDuration() : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    int getPositionMs() {
        if (!prepared) {
            return 0;
        }
        try {
            if (usingVlc) {
                if (vlcPlayer == null) {
                    return 0;
                }
                long time = vlcPlayer.getTime();
                return time > 0 ? (int) time : 0;
            }
            return mediaPlayer != null ? mediaPlayer.getCurrentPosition() : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Play {@code selected}, then the rest of tracks in the same folder key.
     */
    void playTrackInFolder(Context context, PlayableTrack selected, List<PlayableTrack> folderTracks) {
        if (selected == null || selected.uri == null) {
            notifyError("File not found");
            return;
        }
        appContext = context.getApplicationContext();
        libraryTracks.clear();
        if (folderTracks == null || folderTracks.isEmpty()) {
            libraryTracks.add(selected);
        } else {
            libraryTracks.addAll(folderTracks);
        }
        rebuildQueueFrom(selected);
        playCurrent();
    }

    /**
     * Play a prepared list, optionally starting at {@code startTrack}.
     */
    void playTracks(Context context, List<PlayableTrack> tracks, PlayableTrack startTrack) {
        if (tracks == null || tracks.isEmpty()) {
            notifyError(context.getString(R.string.folder_no_music));
            return;
        }
        appContext = context.getApplicationContext();
        libraryTracks.clear();
        libraryTracks.addAll(tracks);
        if (startTrack == null || indexOfTrack(libraryTracks, startTrack) < 0) {
            boolean shuffle = PlaybackPrefs.isShuffleEnabled(appContext);
            playQueue.clear();
            if (shuffle) {
                playQueue.addAll(libraryTracks);
                Collections.shuffle(playQueue);
            } else {
                playQueue.addAll(libraryTracks);
            }
            queueIndex = 0;
        } else {
            rebuildQueueFrom(startTrack);
        }
        playCurrent();
    }

    void onShuffleModeChanged() {
        if (currentTrack == null || libraryTracks.isEmpty()) {
            return;
        }
        rebuildQueueFrom(currentTrack);
    }

    private void rebuildQueueFrom(PlayableTrack selected) {
        playQueue.clear();
        boolean shuffle = appContext != null && PlaybackPrefs.isShuffleEnabled(appContext);
        PlayableTrack selectedTrack = selected;
        int existing = indexOfTrack(libraryTracks, selected);
        if (existing >= 0) {
            selectedTrack = libraryTracks.get(existing);
        }

        if (shuffle) {
            List<PlayableTrack> shuffled = new ArrayList<>(libraryTracks);
            if (indexOfTrack(shuffled, selectedTrack) < 0) {
                shuffled.add(selectedTrack);
            }
            Collections.shuffle(shuffled);
            playQueue.addAll(shuffled);
            int selectedIndex = indexOfTrack(playQueue, selectedTrack);
            queueIndex = selectedIndex >= 0 ? selectedIndex : 0;
        } else {
            playQueue.addAll(libraryTracks);
            if (indexOfTrack(playQueue, selectedTrack) < 0) {
                playQueue.add(selectedTrack);
            }
            if (playQueue.isEmpty()) {
                playQueue.add(selectedTrack);
            }
            int selectedIndex = indexOfTrack(playQueue, selectedTrack);
            queueIndex = selectedIndex >= 0 ? selectedIndex : 0;
        }
    }

    private static int indexOfTrack(List<PlayableTrack> tracks, PlayableTrack target) {
        for (int i = 0; i < tracks.size(); i++) {
            if (tracks.get(i).equals(target)) {
                return i;
            }
        }
        return -1;
    }

    private void rebuildQueueForRepeatCycle() {
        playQueue.clear();
        boolean shuffle = appContext != null && PlaybackPrefs.isShuffleEnabled(appContext);
        if (shuffle) {
            playQueue.addAll(libraryTracks);
            Collections.shuffle(playQueue);
        } else {
            playQueue.addAll(libraryTracks);
        }
        queueIndex = 0;
    }

    private void playCurrent() {
        if (queueIndex < 0 || queueIndex >= playQueue.size()) {
            notifyPlaylistFinished();
            return;
        }
        playTrackInternal(playQueue.get(queueIndex));
    }

    boolean hasPrevious() {
        return queueIndex > 0;
    }

    boolean hasNext() {
        return queueIndex >= 0 && queueIndex + 1 < playQueue.size();
    }

    void playPrevious() {
        if (!hasPrevious()) {
            return;
        }
        queueIndex--;
        playCurrent();
    }

    void playNext() {
        if (!hasNext()) {
            return;
        }
        queueIndex++;
        playCurrent();
    }

    private void playNextOrFinish() {
        if (queueIndex + 1 < playQueue.size()) {
            queueIndex++;
            playCurrent();
            return;
        }
        boolean repeat = appContext != null && PlaybackPrefs.isRepeatEnabled(appContext);
        if (repeat && !libraryTracks.isEmpty()) {
            rebuildQueueForRepeatCycle();
            playCurrent();
            return;
        }
        notifyPlaylistFinished();
    }

    private void playTrackInternal(PlayableTrack track) {
        if (track == null || track.uri == null) {
            notifyError("File not found");
            return;
        }
        ensureMediaSession();
        requestAudioFocus();
        releasePlayerOnly();
        currentTrack = track;
        prepared = false;
        if (mediaSession != null) {
            mediaSession.onTrackChanged(track);
        }
        if (listener != null) {
            listener.onTrackChanged(track);
        }
        if (track.needsVlc()) {
            playWithVlc(track);
        } else {
            playWithMediaPlayer(track);
        }
    }

    private void playWithMediaPlayer(PlayableTrack track) {
        usingVlc = false;
        try {
            mediaPlayer = new android.media.MediaPlayer();
            mediaPlayer.setDataSource(appContext, track.uri);
            mediaPlayer.setOnPreparedListener(mp -> {
                prepared = true;
                mp.start();
                if (mediaSession != null) {
                    mediaSession.onPrepared();
                }
                if (listener != null) {
                    listener.onPrepared();
                }
            });
            mediaPlayer.setOnCompletionListener(mp -> playNextOrFinish());
            mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                prepared = false;
                if (!track.needsVlc()) {
                    releasePlayerOnly();
                    playWithVlc(track);
                    return true;
                }
                playNextOrFinish();
                return true;
            });
            mediaPlayer.prepareAsync();
        } catch (IOException | IllegalArgumentException | SecurityException e) {
            releasePlayerOnly();
            playWithVlc(track);
        }
    }

    private void ensureLibVlc() {
        if (libVlc != null) {
            return;
        }
        ArrayList<String> options = new ArrayList<>();
        options.add("--aout=opensles");
        options.add("--audio-time-stretch");
        options.add("--network-caching=1000");
        libVlc = new LibVLC(appContext, options);
    }

    private void playWithVlc(PlayableTrack track) {
        usingVlc = true;
        try {
            ensureLibVlc();
            vlcPlayer = new org.videolan.libvlc.MediaPlayer(libVlc);
            Media media = openVlcMedia(track.uri);
            if (media == null) {
                prepared = false;
                notifyError("Could not play this file");
                playNextOrFinish();
                return;
            }
            media.addOption(":no-video");
            vlcPlayer.setMedia(media);
            media.release();
            vlcPlayer.setEventListener(event -> {
                if (event.type == org.videolan.libvlc.MediaPlayer.Event.Playing) {
                    if (!prepared) {
                        prepared = true;
                        if (mediaSession != null) {
                            mediaSession.onPrepared();
                        }
                        if (listener != null) {
                            listener.onPrepared();
                        }
                    } else if (mediaSession != null) {
                        mediaSession.onPlayPauseChanged();
                    }
                } else if (event.type == org.videolan.libvlc.MediaPlayer.Event.EndReached) {
                    playNextOrFinish();
                } else if (event.type == org.videolan.libvlc.MediaPlayer.Event.EncounteredError) {
                    prepared = false;
                    notifyError("Could not play this file");
                    playNextOrFinish();
                }
            });
            vlcPlayer.play();
        } catch (Exception e) {
            prepared = false;
            notifyError("Could not play this file");
            playNextOrFinish();
        }
    }

    /**
     * LibVLC often fails on bare content:// URIs. Prefer AssetFileDescriptor / FD,
     * then fall back to a temp file copy (needed for some SAF providers).
     */
    private Media openVlcMedia(Uri uri) throws IOException {
        releaseVlcSources();
        if (uri == null) {
            return null;
        }
        String scheme = uri.getScheme();
        if (scheme != null && scheme.equalsIgnoreCase("file")) {
            String path = uri.getPath();
            if (path != null && new File(path).exists()) {
                return new Media(libVlc, path);
            }
            return new Media(libVlc, uri);
        }

        try {
            vlcAfd = appContext.getContentResolver().openAssetFileDescriptor(uri, "r");
            if (vlcAfd != null) {
                return new Media(libVlc, vlcAfd);
            }
        } catch (Exception ignored) {
            closeQuietly(vlcAfd);
            vlcAfd = null;
        }

        try {
            vlcPfd = appContext.getContentResolver().openFileDescriptor(uri, "r");
            if (vlcPfd != null) {
                return new Media(libVlc, vlcPfd.getFileDescriptor());
            }
        } catch (Exception ignored) {
            closeQuietly(vlcPfd);
            vlcPfd = null;
        }

        vlcTempFile = copyUriToCache(uri);
        if (vlcTempFile != null) {
            return new Media(libVlc, vlcTempFile.getAbsolutePath());
        }
        return null;
    }

    private File copyUriToCache(Uri uri) {
        InputStream in = null;
        FileOutputStream out = null;
        try {
            in = appContext.getContentResolver().openInputStream(uri);
            if (in == null) {
                return null;
            }
            String name = "vlc_play_tmp";
            String path = uri.getLastPathSegment();
            if (path != null) {
                int slash = path.lastIndexOf('/');
                String leaf = slash >= 0 ? path.substring(slash + 1) : path;
                if (leaf.toLowerCase(Locale.US).endsWith(".wma")
                        || leaf.toLowerCase(Locale.US).endsWith(".asf")) {
                    name = leaf.replace(':', '_');
                } else {
                    name = "vlc_play_tmp.wma";
                }
            }
            File file = new File(appContext.getCacheDir(), name);
            out = new FileOutputStream(file);
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) >= 0) {
                out.write(buf, 0, n);
            }
            out.flush();
            return file;
        } catch (Exception e) {
            return null;
        } finally {
            closeQuietly(in);
            closeQuietly(out);
        }
    }

    private void releaseVlcSources() {
        closeQuietly(vlcAfd);
        vlcAfd = null;
        closeQuietly(vlcPfd);
        vlcPfd = null;
        if (vlcTempFile != null) {
            //noinspection ResultOfMethodCallIgnored
            vlcTempFile.delete();
            vlcTempFile = null;
        }
    }

    private static void closeQuietly(AutoCloseable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (Exception ignored) {
        }
    }

    void play() {
        if (!prepared) {
            return;
        }
        requestAudioFocus();
        if (usingVlc) {
            if (vlcPlayer != null && !vlcPlayer.isPlaying()) {
                vlcPlayer.play();
            }
        } else if (mediaPlayer != null && !mediaPlayer.isPlaying()) {
            mediaPlayer.start();
        }
        notifyMediaSessionPlayPause();
    }

    void pause() {
        if (!prepared) {
            return;
        }
        if (usingVlc) {
            if (vlcPlayer != null && vlcPlayer.isPlaying()) {
                vlcPlayer.pause();
            }
        } else if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
        }
        notifyMediaSessionPlayPause();
    }

    void seekTo(int positionMs) {
        if (!prepared) {
            return;
        }
        int safe = Math.max(0, positionMs);
        if (usingVlc) {
            if (vlcPlayer != null) {
                vlcPlayer.setTime(safe);
            }
        } else if (mediaPlayer != null) {
            mediaPlayer.seekTo(safe);
        }
        notifyMediaSessionPlayPause();
    }

    void releasePlayerOnly() {
        prepared = false;
        if (mediaPlayer != null) {
            try {
                mediaPlayer.reset();
                mediaPlayer.release();
            } catch (Exception ignored) {
            }
            mediaPlayer = null;
        }
        if (vlcPlayer != null) {
            try {
                vlcPlayer.stop();
                vlcPlayer.detachViews();
                vlcPlayer.release();
            } catch (Exception ignored) {
            }
            vlcPlayer = null;
        }
        releaseVlcSources();
        usingVlc = false;
    }

    void releaseAll() {
        releasePlayerOnly();
        abandonAudioFocus();
        if (mediaSession != null) {
            mediaSession.release();
            mediaSession = null;
        }
        if (libVlc != null) {
            try {
                libVlc.release();
            } catch (Exception ignored) {
            }
            libVlc = null;
        }
        playQueue.clear();
        libraryTracks.clear();
        queueIndex = -1;
        currentTrack = null;
    }

    private void notifyError(String message) {
        if (listener != null) {
            listener.onError(message);
        }
    }

    private void notifyPlaylistFinished() {
        abandonAudioFocus();
        if (mediaSession != null) {
            mediaSession.onPlaylistFinished();
        }
        if (listener != null) {
            listener.onPlaylistFinished();
        }
    }

    static List<String> supportedExtensions() {
        List<String> list = new ArrayList<>();
        list.add("mp3");
        list.add("m4a");
        list.add("aac");
        list.add("flac");
        list.add("ogg");
        list.add("oga");
        list.add("opus");
        list.add("wav");
        list.add("aiff");
        list.add("aif");
        list.add("wma");
        list.add("asf");
        list.add("amr");
        list.add("3gp");
        list.add("3gpp");
        list.add("mp4");
        list.add("mkv");
        list.add("webm");
        list.add("mid");
        list.add("midi");
        list.add("xmf");
        list.add("rtttl");
        list.add("imy");
        list.add("mxmf");
        return list;
    }
}
