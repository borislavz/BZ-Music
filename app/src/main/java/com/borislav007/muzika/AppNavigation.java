package com.borislav007.muzika;

import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;

final class AppNavigation {
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
        new AlertDialog.Builder(activity)
                .setTitle(R.string.about_title)
                .setMessage(activity.getString(R.string.about_author)
                        + "\n"
                        + versionLine(activity)
                        + "\n\n"
                        + activity.getString(R.string.about_body))
                .setPositiveButton(R.string.about_ok, null)
                .show();
    }

    private static String versionLine(AppCompatActivity activity) {
        try {
            android.content.pm.PackageInfo info = activity.getPackageManager()
                    .getPackageInfo(activity.getPackageName(), 0);
            String name = info.versionName != null ? info.versionName : "";
            long code = android.os.Build.VERSION.SDK_INT >= 28
                    ? info.getLongVersionCode()
                    : info.versionCode;
            return activity.getString(R.string.about_version, name, (int) code);
        } catch (Exception e) {
            return activity.getString(R.string.about_version, "", 0);
        }
    }

    /**
     * Wires the shared app bar title plus optional Start / Exit buttons.
     *
     * @param showBack when true, shows a back arrow that navigates up
     * @param showAbout when true, shows a centered About button (main screen only)
     * @param showStartHome when true, shows Start and returns to the initial screen
     */
    static void bindAppBar(AppCompatActivity activity, @StringRes int titleRes,
                           boolean showBack, boolean showAbout, boolean showStartHome) {
        TextView titleView = activity.findViewById(R.id.textToolbarTitle);
        if (titleView != null) {
            titleView.setText(titleRes);
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
