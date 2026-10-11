package com.borislav007.muzika;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadata;
import android.media.MediaMetadataRetriever;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

/**
 * Exposes playback metadata and transport controls to Bluetooth / system media UIs.
 */
final class PlaybackMediaSession {
    private static final String CHANNEL_ID = "bz_music_playback";
    private static final int NOTIFICATION_ID = 42;
    private static final long POSITION_UPDATE_MS = 1000L;

    private final Context appContext;
    private final MediaSession mediaSession;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable positionTicker = new Runnable() {
        @Override
        public void run() {
            if (mediaSession.isActive() && MusicPlayer.get().isPlaying()) {
                publishPlaybackState();
                handler.postDelayed(this, POSITION_UPDATE_MS);
            }
        }
    };

    private Bitmap currentArt;

    PlaybackMediaSession(Context context) {
        appContext = context.getApplicationContext();
        mediaSession = new MediaSession(appContext, "BZMusic");
        mediaSession.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS
                | MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
        mediaSession.setCallback(new MediaSession.Callback() {
            @Override
            public void onPlay() {
                MusicPlayer.get().play();
                publishPlaybackState();
                startPositionUpdates();
                PlaybackService.refreshNotification(appContext);
            }

            @Override
            public void onPause() {
                MusicPlayer.get().pause();
                publishPlaybackState();
                stopPositionUpdates();
                PlaybackService.refreshNotification(appContext);
            }

            @Override
            public void onSkipToNext() {
                MusicPlayer.get().playNext();
            }

            @Override
            public void onSkipToPrevious() {
                MusicPlayer player = MusicPlayer.get();
                if (player.getPositionMs() > 3000) {
                    player.seekTo(0);
                    publishPlaybackState();
                    PlaybackService.refreshNotification(appContext);
                } else {
                    player.playPrevious();
                }
            }

            @Override
            public void onSeekTo(long pos) {
                MusicPlayer.get().seekTo((int) pos);
                publishPlaybackState();
                PlaybackService.refreshNotification(appContext);
            }

            @Override
            public void onStop() {
                MusicPlayer.get().pause();
                publishPlaybackState();
                stopPositionUpdates();
                PlaybackService.stop(appContext);
            }
        });

        mediaSession.setSessionActivity(playerPendingIntent(0));
        ensureChannel();
    }

    private PendingIntent playerPendingIntent(int requestCode) {
        Intent open = new Intent(appContext, PlayerActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_SINGLE_TOP
                | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        PlayableTrack current = MusicPlayer.get().getCurrentTrack();
        if (current != null && current.uri != null) {
            open.putExtra(PlayerActivity.EXTRA_TRACK_URI, current.uri.toString());
        }
        open.putExtra(PlayerActivity.EXTRA_ALREADY_QUEUED, true);
        int piFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            piFlags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return PendingIntent.getActivity(appContext, requestCode, open, piFlags);
    }

    MediaSession.Token getSessionToken() {
        return mediaSession.getSessionToken();
    }

    void onTrackChanged(PlayableTrack track) {
        if (track == null) {
            return;
        }
        recycleArt();
        currentArt = loadArt(track);
        MediaMetadata.Builder meta = new MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, safe(track.title))
                .putString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE, safe(track.title))
                .putString(MediaMetadata.METADATA_KEY_ARTIST,
                        track.artist != null && !track.artist.trim().isEmpty()
                                ? track.artist.trim() : appContext.getString(R.string.app_name))
                .putString(MediaMetadata.METADATA_KEY_ALBUM,
                        track.album != null ? track.album : "")
                .putString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE,
                        track.artist != null ? track.artist : "")
                .putString(MediaMetadata.METADATA_KEY_DISPLAY_DESCRIPTION,
                        track.displayName != null ? track.displayName : "");
        if (currentArt != null) {
            meta.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, currentArt);
            meta.putBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON, currentArt);
        }
        mediaSession.setMetadata(meta.build());
        mediaSession.setActive(true);
        mediaSession.setSessionActivity(playerPendingIntent(0));
        publishPlaybackState();
        PlaybackService.start(appContext);
        startPositionUpdates();
    }

    void onPrepared() {
        PlayableTrack track = MusicPlayer.get().getCurrentTrack();
        if (track != null) {
            long duration = Math.max(0, MusicPlayer.get().getDurationMs());
            MediaMetadata.Builder meta = new MediaMetadata.Builder()
                    .putString(MediaMetadata.METADATA_KEY_TITLE, safe(track.title))
                    .putString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE, safe(track.title))
                    .putString(MediaMetadata.METADATA_KEY_ARTIST,
                            track.artist != null && !track.artist.trim().isEmpty()
                                    ? track.artist.trim() : appContext.getString(R.string.app_name))
                    .putString(MediaMetadata.METADATA_KEY_ALBUM,
                            track.album != null ? track.album : "")
                    .putLong(MediaMetadata.METADATA_KEY_DURATION, duration);
            if (currentArt != null) {
                meta.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, currentArt);
                meta.putBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON, currentArt);
            }
            mediaSession.setMetadata(meta.build());
        }
        mediaSession.setActive(true);
        publishPlaybackState();
        PlaybackService.refreshNotification(appContext);
        startPositionUpdates();
    }

    void onPlayPauseChanged() {
        publishPlaybackState();
        if (MusicPlayer.get().isPlaying()) {
            startPositionUpdates();
        } else {
            stopPositionUpdates();
        }
        PlaybackService.refreshNotification(appContext);
    }

    void onPlaylistFinished() {
        publishPlaybackState(PlaybackState.STATE_STOPPED);
        stopPositionUpdates();
        mediaSession.setActive(false);
        PlaybackService.stop(appContext);
    }

    void release() {
        stopPositionUpdates();
        mediaSession.setActive(false);
        mediaSession.setCallback(null);
        mediaSession.release();
        recycleArt();
        PlaybackService.stop(appContext);
    }

    Notification buildNotification() {
        PlayableTrack track = MusicPlayer.get().getCurrentTrack();
        String title = track != null ? safe(track.title) : appContext.getString(R.string.app_name);
        String artist = track != null && track.artist != null && !track.artist.trim().isEmpty()
                ? track.artist.trim()
                : appContext.getString(R.string.player_title);

        PendingIntent contentIntent = playerPendingIntent(1);

        boolean playing = MusicPlayer.get().isPlaying();
        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(appContext, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(appContext);
        }
        builder.setContentTitle(title)
                .setContentText(artist)
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentIntent(contentIntent)
                .setShowWhen(false)
                .setOngoing(playing)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setStyle(new Notification.MediaStyle()
                        .setMediaSession(mediaSession.getSessionToken())
                        .setShowActionsInCompactView(0, 1, 2))
                .addAction(action(R.drawable.ic_skip_previous, R.string.player_previous,
                        PlaybackService.ACTION_PREV))
                .addAction(playing
                                ? action(R.drawable.ic_pause, R.string.player_pause,
                                PlaybackService.ACTION_PAUSE)
                                : action(R.drawable.ic_play, R.string.player_play,
                                PlaybackService.ACTION_PLAY))
                .addAction(action(R.drawable.ic_skip_next, R.string.player_next,
                        PlaybackService.ACTION_NEXT));
        if (currentArt != null) {
            builder.setLargeIcon(currentArt);
        }
        return builder.build();
    }

    private Notification.Action action(int icon, int titleRes, String action) {
        Intent intent = new Intent(appContext, PlaybackService.class).setAction(action);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent pi = PendingIntent.getService(
                appContext, action.hashCode(), intent, flags);
        return new Notification.Action.Builder(icon, appContext.getString(titleRes), pi).build();
    }

    private void publishPlaybackState() {
        int state = !MusicPlayer.get().isPrepared()
                ? PlaybackState.STATE_NONE
                : (MusicPlayer.get().isPlaying()
                ? PlaybackState.STATE_PLAYING
                : PlaybackState.STATE_PAUSED);
        publishPlaybackState(state);
    }

    private void publishPlaybackState(int state) {
        long actions = PlaybackState.ACTION_PLAY
                | PlaybackState.ACTION_PAUSE
                | PlaybackState.ACTION_PLAY_PAUSE
                | PlaybackState.ACTION_STOP
                | PlaybackState.ACTION_SEEK_TO
                | PlaybackState.ACTION_SKIP_TO_NEXT
                | PlaybackState.ACTION_SKIP_TO_PREVIOUS;
        if (MusicPlayer.get().hasNext()) {
            actions |= PlaybackState.ACTION_SKIP_TO_NEXT;
        }
        if (MusicPlayer.get().hasPrevious() || MusicPlayer.get().getPositionMs() > 0) {
            actions |= PlaybackState.ACTION_SKIP_TO_PREVIOUS;
        }
        PlaybackState playbackState = new PlaybackState.Builder()
                .setActions(actions)
                .setState(state, MusicPlayer.get().getPositionMs(),
                        state == PlaybackState.STATE_PLAYING ? 1.0f : 0f,
                        SystemClock.elapsedRealtime())
                .build();
        mediaSession.setPlaybackState(playbackState);
    }

    private void startPositionUpdates() {
        handler.removeCallbacks(positionTicker);
        if (MusicPlayer.get().isPlaying()) {
            handler.postDelayed(positionTicker, POSITION_UPDATE_MS);
        }
    }

    private void stopPositionUpdates() {
        handler.removeCallbacks(positionTicker);
    }

    private void ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationManager nm = appContext.getSystemService(NotificationManager.class);
        if (nm == null || nm.getNotificationChannel(CHANNEL_ID) != null) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                appContext.getString(R.string.playback_notification_channel),
                NotificationManager.IMPORTANCE_LOW);
        channel.setDescription(appContext.getString(R.string.playback_notification_channel_desc));
        channel.setShowBadge(false);
        nm.createNotificationChannel(channel);
    }

    private Bitmap loadArt(PlayableTrack track) {
        if (track == null || track.uri == null) {
            return null;
        }
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(appContext, track.uri);
            byte[] art = retriever.getEmbeddedPicture();
            if (art != null) {
                Bitmap full = BitmapFactory.decodeByteArray(art, 0, art.length);
                if (full != null) {
                    return scaleArt(full);
                }
            }
        } catch (Exception ignored) {
        } finally {
            try {
                retriever.release();
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private static Bitmap scaleArt(Bitmap source) {
        int max = 512;
        int w = source.getWidth();
        int h = source.getHeight();
        if (w <= max && h <= max) {
            return source;
        }
        float scale = Math.min(max / (float) w, max / (float) h);
        Bitmap scaled = Bitmap.createScaledBitmap(
                source, Math.max(1, Math.round(w * scale)), Math.max(1, Math.round(h * scale)), true);
        if (scaled != source) {
            source.recycle();
        }
        return scaled;
    }

    private void recycleArt() {
        if (currentArt != null && !currentArt.isRecycled()) {
            currentArt.recycle();
        }
        currentArt = null;
    }

    private static String safe(String value) {
        return value != null && !value.trim().isEmpty() ? value.trim() : "Unknown";
    }

    static int notificationId() {
        return NOTIFICATION_ID;
    }
}
