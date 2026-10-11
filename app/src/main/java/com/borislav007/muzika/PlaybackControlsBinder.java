package com.borislav007.muzika;

import android.app.Activity;
import com.google.android.material.button.MaterialButton;

final class PlaybackControlsBinder {
    private PlaybackControlsBinder() {
    }

    static void bind(Activity activity) {
        MaterialButton buttonShuffle = activity.findViewById(R.id.buttonShuffle);
        MaterialButton buttonRepeat = activity.findViewById(R.id.buttonRepeat);
        if (buttonShuffle == null || buttonRepeat == null) {
            return;
        }

        buttonShuffle.setCheckable(true);
        buttonRepeat.setCheckable(true);
        buttonShuffle.clearOnCheckedChangeListeners();
        buttonRepeat.clearOnCheckedChangeListeners();

        buttonShuffle.setChecked(PlaybackPrefs.isShuffleEnabled(activity));
        buttonRepeat.setChecked(PlaybackPrefs.isRepeatEnabled(activity));
        updateShuffleLabel(buttonShuffle);
        updateRepeatLabel(buttonRepeat);

        buttonShuffle.addOnCheckedChangeListener((button, isChecked) -> {
            PlaybackPrefs.setShuffleEnabled(activity, isChecked);
            updateShuffleLabel(buttonShuffle);
            MusicPlayer.get().onShuffleModeChanged();
            if (activity instanceof PlayerActivity) {
                ((PlayerActivity) activity).onQueueOrderChanged();
            }
        });
        buttonRepeat.addOnCheckedChangeListener((button, isChecked) -> {
            PlaybackPrefs.setRepeatEnabled(activity, isChecked);
            updateRepeatLabel(buttonRepeat);
        });
    }

    /** Turns shuffle off in prefs and refreshes the Shuffle button label/state. */
    static void turnShuffleOff(Activity activity) {
        MaterialButton buttonShuffle = activity.findViewById(R.id.buttonShuffle);
        PlaybackPrefs.setShuffleEnabled(activity, false);
        if (buttonShuffle == null) {
            return;
        }
        buttonShuffle.clearOnCheckedChangeListeners();
        buttonShuffle.setChecked(false);
        updateShuffleLabel(buttonShuffle);
        buttonShuffle.addOnCheckedChangeListener((button, isChecked) -> {
            PlaybackPrefs.setShuffleEnabled(activity, isChecked);
            updateShuffleLabel(buttonShuffle);
            MusicPlayer.get().onShuffleModeChanged();
            if (activity instanceof PlayerActivity) {
                ((PlayerActivity) activity).onQueueOrderChanged();
            }
        });
    }

    private static void updateShuffleLabel(MaterialButton button) {
        button.setText(button.isChecked()
                ? R.string.playback_shuffle_on
                : R.string.playback_shuffle_off);
    }

    private static void updateRepeatLabel(MaterialButton button) {
        button.setText(button.isChecked()
                ? R.string.playback_repeat_on
                : R.string.playback_repeat_off);
    }
}
