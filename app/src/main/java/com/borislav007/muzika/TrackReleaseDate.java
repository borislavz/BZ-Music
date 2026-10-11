package com.borislav007.muzika;

import android.content.Context;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Release display rules:
 * <ul>
 *   <li>YEAR tag is required for a tagged release date</li>
 *   <li>If month+day are also available (DATE/TDRC), show {@code YYYY-MMM-DD}</li>
 *   <li>If only YEAR, show {@code YYYY}</li>
 *   <li>Otherwise fall back to Date Modified ({@code YYYY-MMM-DD})</li>
 * </ul>
 */
final class TrackReleaseDate {
    private static final Pattern YEAR_PATTERN = Pattern.compile("(19|20)\\d{2}");
    private static final long ONE_DAY_MS = 24L * 60L * 60L * 1000L;

    static final class TagRelease {
        static final TagRelease NONE = new TagRelease(0L, false);

        final long millis;
        /** True when only YEAR is known (no reliable month/day). */
        final boolean yearOnly;

        TagRelease(long millis, boolean yearOnly) {
            this.millis = millis;
            this.yearOnly = yearOnly;
        }

        boolean isPresent() {
            return millis > 0L;
        }
    }

    private TrackReleaseDate() {
    }

    static int parseYear(String raw) {
        if (raw == null) {
            return 0;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return 0;
        }
        try {
            int year = Integer.parseInt(trimmed);
            if (year >= 1000 && year <= 2100) {
                return year;
            }
        } catch (NumberFormatException ignored) {
        }
        Matcher matcher = YEAR_PATTERN.matcher(trimmed);
        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group());
            } catch (NumberFormatException ignored) {
            }
        }
        return 0;
    }

    static TagRelease fromYear(int year) {
        if (year < 1000 || year > 2100) {
            return TagRelease.NONE;
        }
        return new TagRelease(millisForYear(year), true);
    }

    static TagRelease readFromUri(Context context, Uri uri, long dateModifiedMillis) {
        if (context == null || uri == null) {
            return TagRelease.NONE;
        }
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(context, uri);
            return readFromRetriever(retriever, dateModifiedMillis);
        } catch (RuntimeException ignored) {
            return TagRelease.NONE;
        } finally {
            try {
                retriever.release();
            } catch (IOException ignored) {
            }
        }
    }

    /**
     * YEAR is required. Month/day from DATE are used only to flesh out that year.
     * DATE alone (without YEAR) is not treated as a release tag.
     */
    static TagRelease readFromRetriever(MediaMetadataRetriever retriever, long dateModifiedMillis) {
        if (retriever == null) {
            return TagRelease.NONE;
        }
        int year = parseYear(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR));
        if (year <= 0) {
            return TagRelease.NONE;
        }

        TagRelease fromDate = parseTagDate(
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE));
        if (fromDate.isPresent() && !fromDate.yearOnly
                && looksLikeFileTimestamp(fromDate.millis, dateModifiedMillis)) {
            fromDate = TagRelease.NONE;
        }

        if (fromDate.isPresent() && !fromDate.yearOnly) {
            Calendar calendar = Calendar.getInstance(TimeZone.getDefault(), Locale.US);
            calendar.setTimeInMillis(fromDate.millis);
            int month = calendar.get(Calendar.MONTH);
            int day = calendar.get(Calendar.DAY_OF_MONTH);
            return new TagRelease(millisForYmd(year, month, day), false);
        }
        return fromYear(year);
    }

    /** Display value for Song Info / list (YYYY or YYYY-MMM-DD), empty if none. */
    static String displayValue(long taggedMillis, boolean yearOnly, long dateModifiedMillis) {
        if (taggedMillis > 0L) {
            if (yearOnly) {
                return String.valueOf(yearOf(taggedMillis));
            }
            return formatYyyyMmmDd(taggedMillis);
        }
        if (dateModifiedMillis > 0L) {
            return formatYyyyMmmDd(dateModifiedMillis);
        }
        return "";
    }

    static String formatYyyyMmmDd(long millis) {
        if (millis <= 0L) {
            return "";
        }
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MMM-dd", Locale.US);
        format.setTimeZone(TimeZone.getDefault());
        return format.format(new Date(millis));
    }

    static TagRelease parseTagDate(String raw) {
        if (raw == null) {
            return TagRelease.NONE;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return TagRelease.NONE;
        }

        if (trimmed.matches("(?i)^(19|20)\\d{2}$")) {
            return fromYear(Integer.parseInt(trimmed));
        }

        String datePart = trimmed;
        int tIndex = Math.max(datePart.indexOf('T'), datePart.indexOf('t'));
        if (tIndex > 0) {
            datePart = datePart.substring(0, tIndex);
        }
        int space = datePart.indexOf(' ');
        if (space > 0) {
            datePart = datePart.substring(0, space);
        }
        datePart = datePart.trim();

        String[] patterns = {
                "yyyy-MM-dd",
                "yyyy/MM/dd",
                "yyyy.MM.dd",
                "dd-MM-yyyy",
                "dd/MM/yyyy",
                "yyyyMMdd"
        };
        for (String pattern : patterns) {
            Long millis = tryParse(datePart, pattern);
            if (millis != null) {
                return new TagRelease(millis, false);
            }
        }

        String[] dateTimePatterns = {
                "yyyy-MM-dd'T'HH:mm:ss",
                "yyyy-MM-dd'T'HH:mm:ssZ",
                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                "yyyyMMdd'T'HHmmss"
        };
        for (String pattern : dateTimePatterns) {
            Long millis = tryParse(trimmed, pattern);
            if (millis != null) {
                return new TagRelease(millis, false);
            }
        }

        int year = parseYear(trimmed);
        return year > 0 ? fromYear(year) : TagRelease.NONE;
    }

    private static Long tryParse(String value, String pattern) {
        try {
            SimpleDateFormat format = new SimpleDateFormat(pattern, Locale.US);
            format.setLenient(false);
            format.setTimeZone(TimeZone.getDefault());
            Date parsed = format.parse(value);
            return parsed != null ? parsed.getTime() : null;
        } catch (ParseException ignored) {
            return null;
        }
    }

    private static boolean looksLikeFileTimestamp(long tagMillis, long dateModifiedMillis) {
        if (tagMillis <= 0L || dateModifiedMillis <= 0L) {
            return false;
        }
        return Math.abs(tagMillis - dateModifiedMillis) <= ONE_DAY_MS;
    }

    static long millisForYear(int year) {
        return millisForYmd(year, Calendar.JANUARY, 1);
    }

    private static long millisForYmd(int year, int month, int day) {
        Calendar calendar = Calendar.getInstance(TimeZone.getDefault(), Locale.US);
        calendar.clear();
        calendar.set(year, month, day, 0, 0, 0);
        return calendar.getTimeInMillis();
    }

    private static int yearOf(long millis) {
        Calendar calendar = Calendar.getInstance(TimeZone.getDefault(), Locale.US);
        calendar.setTimeInMillis(millis);
        return calendar.get(Calendar.YEAR);
    }
}
