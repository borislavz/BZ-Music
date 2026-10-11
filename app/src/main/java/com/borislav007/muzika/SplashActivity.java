package com.borislav007.muzika;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.ImageView;
import androidx.appcompat.app.AppCompatActivity;
import java.util.Random;

@SuppressLint("CustomSplashScreen")
public class SplashActivity extends AppCompatActivity {
    private static final long SPLASH_DELAY_MS = 2000L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable openMain = new Runnable() {
        @Override
        public void run() {
            // Session may have appeared while splash was showing (unlikely but cheap to re-check).
            if (openPlayerIfSessionActive()) {
                return;
            }
            Intent intent = new Intent(SplashActivity.this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Icon / launcher re-entry while a session is already running: restore the player UI.
        if (openPlayerIfSessionActive()) {
            return;
        }

        // Duplicate splash stacked on an existing task (no active session): drop it so the
        // previous screen (folder browser, etc.) stays in front.
        if (!isTaskRoot()) {
            finish();
            return;
        }

        setContentView(R.layout.activity_splash);

        int index = new Random().nextInt(SplashImages.IMAGES.length);
        ImageView splashImage = findViewById(R.id.splashImage);
        splashImage.setImageResource(SplashImages.IMAGES[index]);
        splashImage.setContentDescription(getString(SplashImages.DESCRIPTIONS[index]));

        handler.postDelayed(openMain, SPLASH_DELAY_MS);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (openPlayerIfSessionActive()) {
            return;
        }
        if (!isTaskRoot()) {
            finish();
        }
    }

    /**
     * @return true if this activity has started the player and is finishing
     */
    private boolean openPlayerIfSessionActive() {
        MusicPlayer player = MusicPlayer.get();
        if (!player.hasRestorableSession()) {
            return false;
        }
        PlayableTrack current = player.getCurrentTrack();
        Intent intent = new Intent(this, PlayerActivity.class);
        intent.putExtra(PlayerActivity.EXTRA_TRACK_URI, current.uri.toString());
        intent.putExtra(PlayerActivity.EXTRA_ALREADY_QUEUED, true);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_SINGLE_TOP
                | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        startActivity(intent);
        finish();
        return true;
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(openMain);
        super.onDestroy();
    }
}
