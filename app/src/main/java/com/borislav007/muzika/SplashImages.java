package com.borislav007.muzika;

/** Shared splash drawables used at launch and in the About dialog. */
final class SplashImages {
    /** Launch / About carousel pool — excludes the author easter-egg image. */
    static final int[] IMAGES = {
            R.drawable.splash_instruments,
            R.drawable.splash_hard_rock,
            R.drawable.splash_classical,
            R.drawable.splash_metal_stage,
            R.drawable.splash_flying_v,
            R.drawable.splash_stadium_aerial_1,
            R.drawable.splash_stadium_aerial_2,
            R.drawable.splash_acoustic
    };

    static final int[] DESCRIPTIONS = {
            R.string.splash_content_description,
            R.string.splash_hard_rock_description,
            R.string.splash_classical_description,
            R.string.splash_metal_stage_description,
            R.string.splash_flying_v_description,
            R.string.splash_stadium_aerial_1_description,
            R.string.splash_stadium_aerial_2_description,
            R.string.splash_acoustic_description
    };

    /** Shown only when the About author line is tapped. */
    static final int AUTHOR_IMAGE = R.drawable.splash_metal_crowd;
    static final int AUTHOR_DESCRIPTION = R.string.splash_metal_crowd_description;

    private SplashImages() {
    }
}
