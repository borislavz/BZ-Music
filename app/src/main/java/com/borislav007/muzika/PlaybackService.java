package com.borislav007.muzika;

import android.app.Notification;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import androidx.annotation.Nullable;

/**
 * Foreground media service so Bluetooth / system controls stay connected while playing.
 */
public class PlaybackService extends Service {
    static final String ACTION_START = "com.borislav007.muzika.action.START";
    static final String ACTION_REFRESH = "com.borislav007.muzika.action.REFRESH";
    static final String ACTION_STOP = "com.borislav007.muzika.action.STOP";
    static final String ACTION_PLAY = "com.borislav007.muzika.action.PLAY";
    static final String ACTION_PAUSE = "com.borislav007.muzika.action.PAUSE";
    static final String ACTION_NEXT = "com.borislav007.muzika.action.NEXT";
    static final String ACTION_PREV = "com.borislav007.muzika.action.PREV";

    static void start(Context context) {
        Intent intent = new Intent(context, PlaybackService.class).setAction(ACTION_START);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    static void refreshNotification(Context context) {
        Context app = context.getApplicationContext();
        Intent intent = new Intent(app, PlaybackService.class).setAction(ACTION_REFRESH);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                app.startForegroundService(intent);
            } else {
                app.startService(intent);
            }
        } catch (Exception ignored) {
            // Service may already be stopped.
        }
    }

    static void stop(Context context) {
        context.stopService(new Intent(context, PlaybackService.class).setAction(ACTION_STOP));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : ACTION_START;
        if (ACTION_STOP.equals(action)) {
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }
        if (ACTION_PLAY.equals(action)) {
            MusicPlayer.get().play();
            MusicPlayer.get().notifyMediaSessionPlayPause();
        } else if (ACTION_PAUSE.equals(action)) {
            MusicPlayer.get().pause();
            MusicPlayer.get().notifyMediaSessionPlayPause();
        } else if (ACTION_NEXT.equals(action)) {
            MusicPlayer.get().playNext();
        } else if (ACTION_PREV.equals(action)) {
            MusicPlayer player = MusicPlayer.get();
            if (player.getPositionMs() > 3000) {
                player.seekTo(0);
                player.notifyMediaSessionPlayPause();
            } else {
                player.playPrevious();
            }
        }

        PlaybackMediaSession session = MusicPlayer.get().mediaSessionOrNull();
        if (session == null) {
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }
        Notification notification = session.buildNotification();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(PlaybackMediaSession.notificationId(), notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else {
            startForeground(PlaybackMediaSession.notificationId(), notification);
        }
        return START_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
