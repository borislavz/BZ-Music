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
    private static final int[] SPLASH_IMAGES = {
            R.drawable.splash_instruments,
            R.drawable.splash_hard_rock,
            R.drawable.splash_classical,
            R.drawable.splash_metal_stage,
            R.drawable.splash_flying_v,
            R.drawable.splash_metal_crowd
    };
    private static final int[] SPLASH_DESCRIPTIONS = {
            R.string.splash_content_description,
            R.string.splash_hard_rock_description,
            R.string.splash_classical_description,
            R.string.splash_metal_stage_description,
            R.string.splash_flying_v_description,
            R.string.splash_metal_crowd_description
    };

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable openMain = new Runnable() {
        @Override
        public void run() {
            startActivity(new Intent(SplashActivity.this, MainActivity.class));
            finish();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        int index = new Random().nextInt(SPLASH_IMAGES.length);
        ImageView splashImage = findViewById(R.id.splashImage);
        splashImage.setImageResource(SPLASH_IMAGES[index]);
        splashImage.setContentDescription(getString(SPLASH_DESCRIPTIONS[index]));

        handler.postDelayed(openMain, SPLASH_DELAY_MS);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(openMain);
        super.onDestroy();
    }
}
