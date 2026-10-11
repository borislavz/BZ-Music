package com.borislav007.muzika;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

final class AppNavigation {
    private static final long ABOUT_FIRST_TEXT_MS = 10_000L;
    private static final long ABOUT_SPLASH_INTERVAL_MS = 3_000L;
    private static final long ABOUT_AUTHOR_SPLASH_MS = 4_000L;

    private AppNavigation() {
    }

    static final String EXTRA_OPEN_INITIAL = "open_initial_screen";

    static void goToMainMenu(AppCompatActivity activity) {
        android.content.Intent intent =
                new android.content.Intent(activity, MainActivity.class);
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
                | android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.putExtra(EXTRA_OPEN_INITIAL, true);
        activity.startActivity(intent);
        if (!(activity instanceof MainActivity)) {
            activity.finish();
        }
    }

    static void confirmExit(AppCompatActivity activity) {
        new AlertDialog.Builder(activity)
                .setTitle(R.string.exit_confirm_title)
                .setMessage(R.string.exit_confirm_message)
                .setPositiveButton(R.string.exit_confirm_yes, (dialog, which) -> {
                    MusicPlayer.get().releaseAll();
                    activity.finishAffinity();
                })
                .setNegativeButton(R.string.exit_confirm_no, null)
                .show();
    }

    static void showAbout(AppCompatActivity activity) {
        View content = LayoutInflater.from(activity).inflate(R.layout.dialog_about, null, false);
        TextView authorHeader = content.findViewById(R.id.aboutHeaderAuthor);
        ScrollView textScroll = content.findViewById(R.id.aboutTextScroll);
        TextView aboutText = content.findViewById(R.id.aboutText);
        ImageView splashImage = content.findViewById(R.id.aboutSplashImage);
        MaterialButton okButton = content.findViewById(R.id.aboutOk);

        // Title + author stay in the header; body/version swap with splash images.
        aboutText.setText(versionLine(activity)
                + "\n\n"
                + activity.getString(R.string.about_body));

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setView(content)
                .setCancelable(true)
                .create();

        Handler handler = new Handler(Looper.getMainLooper());
        Random random = new Random();
        final List<Integer> splashOrder = new ArrayList<>();
        final int[] orderIndex = {0};
        final long startedAt = SystemClock.uptimeMillis();
        final boolean[] splashCycleStarted = {false};

        final Runnable[] showSplash = new Runnable[1];
        final Runnable[] startSplashCycle = new Runnable[1];
        final Runnable[] restoreAfterAuthor = new Runnable[1];

        showSplash[0] = () -> {
            if (!dialog.isShowing()) {
                return;
            }
            splashCycleStarted[0] = true;
            if (splashOrder.isEmpty() || orderIndex[0] >= splashOrder.size()) {
                splashOrder.clear();
                for (int i = 0; i < SplashImages.IMAGES.length; i++) {
                    splashOrder.add(i);
                }
                Collections.shuffle(splashOrder, random);
                orderIndex[0] = 0;
            }
            int index = splashOrder.get(orderIndex[0]);
            orderIndex[0]++;
            textScroll.setVisibility(View.GONE);
            splashImage.setImageResource(SplashImages.IMAGES[index]);
            splashImage.setContentDescription(
                    activity.getString(SplashImages.DESCRIPTIONS[index]));
            splashImage.setVisibility(View.VISIBLE);
            handler.postDelayed(showSplash[0], ABOUT_SPLASH_INTERVAL_MS);
        };

        startSplashCycle[0] = () -> showSplash[0].run();

        restoreAfterAuthor[0] = () -> {
            if (!dialog.isShowing()) {
                return;
            }
            long elapsed = SystemClock.uptimeMillis() - startedAt;
            if (!splashCycleStarted[0] && elapsed < ABOUT_FIRST_TEXT_MS) {
                splashImage.setVisibility(View.GONE);
                textScroll.setVisibility(View.VISIBLE);
                handler.postDelayed(startSplashCycle[0], ABOUT_FIRST_TEXT_MS - elapsed);
            } else {
                showSplash[0].run();
            }
        };

        authorHeader.setClickable(true);
        authorHeader.setOnClickListener(v -> {
            handler.removeCallbacks(startSplashCycle[0]);
            handler.removeCallbacks(showSplash[0]);
            handler.removeCallbacks(restoreAfterAuthor[0]);
            textScroll.setVisibility(View.GONE);
            splashImage.setImageResource(SplashImages.AUTHOR_IMAGE);
            splashImage.setContentDescription(
                    activity.getString(SplashImages.AUTHOR_DESCRIPTION));
            splashImage.setVisibility(View.VISIBLE);
            handler.postDelayed(restoreAfterAuthor[0], ABOUT_AUTHOR_SPLASH_MS);
        });

        okButton.setOnClickListener(v -> dialog.dismiss());
        dialog.setOnDismissListener(d -> {
            handler.removeCallbacks(showSplash[0]);
            handler.removeCallbacks(startSplashCycle[0]);
            handler.removeCallbacks(restoreAfterAuthor[0]);
        });
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            DisplayMetrics metrics = activity.getResources().getDisplayMetrics();
            window.setLayout(
                    Math.round(metrics.widthPixels * 0.96f),
                    Math.round(metrics.heightPixels * 0.78f));
        }
        // Strip AlertDialog's default bottom padding under the custom view / OK button.
        View parent = (View) content.getParent();
        if (parent instanceof ViewGroup) {
            parent.setPadding(
                    parent.getPaddingLeft(),
                    parent.getPaddingTop(),
                    parent.getPaddingRight(),
                    Math.round(2 * activity.getResources().getDisplayMetrics().density));
        }
        // Text for 10s, then walk a shuffled splash list (reshuffle when finished).
        handler.postDelayed(startSplashCycle[0], ABOUT_FIRST_TEXT_MS);
    }

    private static String versionLine(AppCompatActivity activity) {
        try {
            android.content.pm.PackageInfo info = activity.getPackageManager()
                    .getPackageInfo(activity.getPackageName(), 0);
            String name = info.versionName != null ? info.versionName : "";
            return activity.getString(R.string.about_version, name);
        } catch (Exception e) {
            return activity.getString(R.string.about_version, "");
        }
    }

    /**
     * Wires the shared app bar title plus optional Start / About / Exit buttons.
     *
     * @param showBack when true, shows a back arrow that navigates up
     * @param showAbout when true, shows the About button
     * @param showStartHome when true, shows Start (player bar; title is hidden)
     */
    static void bindAppBar(AppCompatActivity activity, @StringRes int titleRes,
                           boolean showBack, boolean showAbout, boolean showStartHome) {
        TextView titleView = activity.findViewById(R.id.textToolbarTitle);
        if (titleView != null) {
            titleView.setText(titleRes);
            // Player uses equal Start/About/List/Exit buttons; other screens keep the title.
            titleView.setVisibility(showStartHome ? View.GONE : View.VISIBLE);
        }

        ImageButton back = activity.findViewById(R.id.buttonToolbarBack);
        if (back != null) {
            if (showBack) {
                back.setVisibility(View.VISIBLE);
                back.setOnClickListener(v ->
                        activity.getOnBackPressedDispatcher().onBackPressed());
            } else {
                back.setVisibility(View.GONE);
            }
        }

        MaterialButton about = activity.findViewById(R.id.buttonAbout);
        if (about != null) {
            if (showAbout) {
                about.setVisibility(View.VISIBLE);
                about.setOnClickListener(v -> showAbout(activity));
            } else {
                about.setVisibility(View.GONE);
                about.setOnClickListener(null);
            }
        }

        MaterialButton mainMenu = activity.findViewById(R.id.buttonMainMenu);
        if (mainMenu != null) {
            if (showStartHome) {
                mainMenu.setVisibility(View.VISIBLE);
                mainMenu.setText(R.string.action_start);
                mainMenu.setOnClickListener(v -> goToMainMenu(activity));
            } else {
                mainMenu.setVisibility(View.GONE);
                mainMenu.setOnClickListener(null);
            }
        }

        View spacerLeft = activity.findViewById(R.id.barSpacerLeft);
        View spacerRight = activity.findViewById(R.id.barSpacerRight);
        MaterialButton togglePlaylist = activity.findViewById(R.id.buttonTogglePlaylist);
        if (showStartHome) {
            // Player: Start | spacer | List/Song info | spacer | About | Exit
            if (spacerLeft != null) {
                spacerLeft.setVisibility(View.VISIBLE);
            }
            if (spacerRight != null) {
                spacerRight.setVisibility(View.VISIBLE);
            }
        } else {
            if (spacerLeft != null) {
                spacerLeft.setVisibility(View.GONE);
            }
            if (spacerRight != null) {
                spacerRight.setVisibility(View.GONE);
            }
            if (togglePlaylist != null) {
                togglePlaylist.setVisibility(View.GONE);
                togglePlaylist.setOnClickListener(null);
            }
        }

        MaterialButton exit = activity.findViewById(R.id.buttonExit);
        if (exit != null) {
            exit.setOnClickListener(v -> confirmExit(activity));
        }
    }

    static void bindAppBar(AppCompatActivity activity, @StringRes int titleRes,
                           boolean showBack, boolean showAbout) {
        bindAppBar(activity, titleRes, showBack, showAbout, false);
    }

    static void bindAppBar(AppCompatActivity activity, @StringRes int titleRes, boolean showBack) {
        bindAppBar(activity, titleRes, showBack, false, false);
    }

    static void setAppBarTitle(AppCompatActivity activity, @Nullable CharSequence title) {
        TextView titleView = activity.findViewById(R.id.textToolbarTitle);
        if (titleView != null && title != null) {
            titleView.setText(title);
        }
    }
}
