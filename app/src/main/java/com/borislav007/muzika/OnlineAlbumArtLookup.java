package com.borislav007.muzika;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.text.TextUtils;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Aggressively looks up album artwork online from song metadata and file names.
 * Uses free APIs (iTunes + Deezer) with no API key.
 */
final class OnlineAlbumArtLookup {
    private static final Pattern TRACK_PREFIX =
            Pattern.compile("^\\s*\\d{1,3}\\s*[.\\-_)]+\\s*");
    private static final Pattern ARTIST_TITLE_SPLIT =
            Pattern.compile("\\s+[\\-–—]\\s+|\\s+_\\s+|\\s{2,}");

    private OnlineAlbumArtLookup() {
    }

    static Bitmap findCover(String artist, String album, String title) {
        return findCover(artist, album, title, null);
    }

    static Bitmap findCover(String artist, String album, String title, String fileName) {
        List<String> queries = buildQueries(artist, album, title, fileName);
        for (String query : queries) {
            String artUrl = searchItunesArtwork(query, "song");
            if (TextUtils.isEmpty(artUrl)) {
                artUrl = searchItunesArtwork(query, "album");
            }
            if (TextUtils.isEmpty(artUrl)) {
                artUrl = searchDeezerArtwork(query);
            }
            if (!TextUtils.isEmpty(artUrl)) {
                Bitmap bitmap = downloadBitmap(artUrl);
                if (bitmap != null) {
                    return bitmap;
                }
            }
        }
        return null;
    }

    private static List<String> buildQueries(String artist, String album, String title,
                                             String fileName) {
        String cleanArtist = cleanMeta(artist);
        String cleanAlbum = cleanMeta(album);
        String cleanTitle = cleanMeta(title);
        String fromFile = cleanFileName(fileName);

        // Prefer a human title over a raw file name when both look like the same leaf name.
        if (isProbablyFileLeaf(cleanTitle) && !TextUtils.isEmpty(fromFile)) {
            cleanTitle = fromFile;
        } else if (TextUtils.isEmpty(cleanTitle) && !TextUtils.isEmpty(fromFile)) {
            cleanTitle = fromFile;
        }

        String parsedArtist = null;
        String parsedTitle = null;
        String[] parsed = splitArtistTitle(cleanTitle);
        if (parsed != null) {
            parsedArtist = parsed[0];
            parsedTitle = parsed[1];
        }
        if (parsedArtist == null && !TextUtils.isEmpty(fromFile)) {
            String[] fromFileSplit = splitArtistTitle(fromFile);
            if (fromFileSplit != null) {
                parsedArtist = fromFileSplit[0];
                parsedTitle = fromFileSplit[1];
            }
        }

        Set<String> queries = new LinkedHashSet<>();
        addQuery(queries, cleanArtist, cleanAlbum, null);
        addQuery(queries, cleanArtist, cleanTitle, null);
        addQuery(queries, cleanArtist, cleanAlbum, cleanTitle);
        addQuery(queries, cleanAlbum, cleanTitle, null);
        addQuery(queries, parsedArtist, parsedTitle, null);
        addQuery(queries, cleanArtist, parsedTitle, null);
        addQuery(queries, parsedArtist, cleanAlbum, null);
        addQuery(queries, cleanArtist, null, null);
        addQuery(queries, cleanAlbum, null, null);
        addQuery(queries, cleanTitle, null, null);
        addQuery(queries, fromFile, null, null);
        addQuery(queries, parsedTitle, null, null);

        // Last-resort: strip parentheses / brackets / featuring tags.
        addQuery(queries, cleanArtist, stripExtras(cleanTitle), null);
        addQuery(queries, stripExtras(cleanTitle), null, null);
        addQuery(queries, stripExtras(fromFile), null, null);

        return new ArrayList<>(queries);
    }

    private static void addQuery(Set<String> out, String a, String b, String c) {
        StringBuilder query = new StringBuilder();
        appendTerm(query, a);
        appendTerm(query, b);
        appendTerm(query, c);
        String value = query.toString().trim();
        if (value.length() >= 2) {
            out.add(value);
        }
    }

    private static void appendTerm(StringBuilder query, String term) {
        if (TextUtils.isEmpty(term)) {
            return;
        }
        if (query.length() > 0) {
            query.append(' ');
        }
        query.append(term.trim());
    }

    private static String cleanMeta(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        String lower = trimmed.toLowerCase(Locale.US);
        if (lower.equals("<unknown>")
                || lower.equals("unknown")
                || lower.equals("unknown artist")
                || lower.equals("unknown album")
                || lower.equals("various artists")
                || lower.equals("n/a")
                || lower.equals("null")
                || lower.equals("track")
                || lower.equals("audio")) {
            return null;
        }
        return trimmed;
    }

    private static String cleanFileName(String fileName) {
        if (fileName == null) {
            return null;
        }
        String name = fileName.trim();
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0 && slash < name.length() - 1) {
            name = name.substring(slash + 1);
        }
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            name = name.substring(0, dot);
        }
        name = name.replace('_', ' ').replace('.', ' ').trim();
        name = TRACK_PREFIX.matcher(name).replaceFirst("").trim();
        name = name.replaceAll("\\s+", " ").trim();
        return cleanMeta(name);
    }

    private static boolean isProbablyFileLeaf(String title) {
        if (title == null) {
            return false;
        }
        String lower = title.toLowerCase(Locale.US);
        return lower.endsWith(".mp3")
                || lower.endsWith(".wma")
                || lower.endsWith(".flac")
                || lower.endsWith(".m4a")
                || lower.endsWith(".wav")
                || lower.endsWith(".ogg");
    }

    private static String[] splitArtistTitle(String value) {
        if (TextUtils.isEmpty(value)) {
            return null;
        }
        Matcher matcher = ARTIST_TITLE_SPLIT.matcher(value);
        if (!matcher.find()) {
            return null;
        }
        String left = value.substring(0, matcher.start()).trim();
        String right = value.substring(matcher.end()).trim();
        if (left.length() < 2 || right.length() < 2) {
            return null;
        }
        // Avoid splitting album-style "disc 1 - track"
        if (left.toLowerCase(Locale.US).matches("disc\\s*\\d+")
                || left.toLowerCase(Locale.US).matches("cd\\s*\\d+")) {
            return null;
        }
        return new String[]{left, right};
    }

    private static String stripExtras(String value) {
        if (TextUtils.isEmpty(value)) {
            return null;
        }
        String cleaned = value
                .replaceAll("(?i)\\s*[\\(\\[][^\\)\\]]*(remaster|remix|live|feat\\.?|ft\\.?|bonus|explicit)[^\\)\\]]*[\\)\\]]", "")
                .replaceAll("\\s*[\\(\\[].*?[\\)\\]]", "")
                .replaceAll("(?i)\\s+(feat\\.?|ft\\.?)\\s+.+$", "")
                .replaceAll("\\s+", " ")
                .trim();
        return cleanMeta(cleaned);
    }

    private static String searchItunesArtwork(String query, String entity) {
        HttpURLConnection connection = null;
        try {
            Uri uri = Uri.parse("https://itunes.apple.com/search").buildUpon()
                    .appendQueryParameter("term", query)
                    .appendQueryParameter("media", "music")
                    .appendQueryParameter("entity", entity)
                    .appendQueryParameter("limit", "8")
                    .build();
            connection = openGet(uri.toString());
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                return null;
            }
            JSONObject root = new JSONObject(readFully(connection.getInputStream()));
            JSONArray results = root.optJSONArray("results");
            if (results == null) {
                return null;
            }
            String queryLower = query.toLowerCase(Locale.US);
            String bestAny = null;
            String bestMatch = null;
            for (int i = 0; i < results.length(); i++) {
                JSONObject item = results.optJSONObject(i);
                if (item == null) {
                    continue;
                }
                String art = firstNonEmpty(
                        item.optString("artworkUrl100", null),
                        item.optString("artworkUrl60", null));
                if (TextUtils.isEmpty(art)) {
                    continue;
                }
                if (bestAny == null) {
                    bestAny = art;
                }
                String track = item.optString("trackName", "");
                String collection = item.optString("collectionName", "");
                String artistName = item.optString("artistName", "");
                String hay = (track + " " + collection + " " + artistName).toLowerCase(Locale.US);
                if (tokensOverlap(queryLower, hay)) {
                    bestMatch = art;
                    break;
                }
            }
            String chosen = bestMatch != null ? bestMatch : bestAny;
            return TextUtils.isEmpty(chosen) ? null : upgradeArtworkUrl(chosen);
        } catch (Exception ignored) {
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static String searchDeezerArtwork(String query) {
        HttpURLConnection connection = null;
        try {
            Uri uri = Uri.parse("https://api.deezer.com/search").buildUpon()
                    .appendQueryParameter("q", query)
                    .appendQueryParameter("limit", "8")
                    .build();
            connection = openGet(uri.toString());
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                return null;
            }
            JSONObject root = new JSONObject(readFully(connection.getInputStream()));
            JSONArray results = root.optJSONArray("data");
            if (results == null) {
                return null;
            }
            String queryLower = query.toLowerCase(Locale.US);
            String bestAny = null;
            String bestMatch = null;
            for (int i = 0; i < results.length(); i++) {
                JSONObject item = results.optJSONObject(i);
                if (item == null) {
                    continue;
                }
                JSONObject albumObj = item.optJSONObject("album");
                JSONObject artistObj = item.optJSONObject("artist");
                String art = null;
                if (albumObj != null) {
                    art = firstNonEmpty(
                            albumObj.optString("cover_xl", null),
                            albumObj.optString("cover_big", null),
                            albumObj.optString("cover_medium", null),
                            albumObj.optString("cover", null));
                }
                if (TextUtils.isEmpty(art)) {
                    continue;
                }
                if (bestAny == null) {
                    bestAny = art;
                }
                String track = item.optString("title", "");
                String albumName = albumObj != null ? albumObj.optString("title", "") : "";
                String artistName = artistObj != null ? artistObj.optString("name", "") : "";
                String hay = (track + " " + albumName + " " + artistName).toLowerCase(Locale.US);
                if (tokensOverlap(queryLower, hay)) {
                    bestMatch = art;
                    break;
                }
            }
            String chosen = bestMatch != null ? bestMatch : bestAny;
            if (TextUtils.isEmpty(chosen)) {
                return null;
            }
            if (chosen.startsWith("http://")) {
                chosen = "https://" + chosen.substring("http://".length());
            }
            return chosen;
        } catch (Exception ignored) {
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static boolean tokensOverlap(String queryLower, String hayLower) {
        if (TextUtils.isEmpty(queryLower) || TextUtils.isEmpty(hayLower)) {
            return false;
        }
        String[] tokens = queryLower.split("[^a-z0-9]+");
        int hits = 0;
        int meaningful = 0;
        for (String token : tokens) {
            if (token.length() < 3) {
                continue;
            }
            meaningful++;
            if (hayLower.contains(token)) {
                hits++;
            }
        }
        if (meaningful == 0) {
            return hayLower.contains(queryLower);
        }
        return hits >= Math.min(2, meaningful) || hits == meaningful;
    }

    private static String firstNonEmpty(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.trim().isEmpty() && !"null".equalsIgnoreCase(value)) {
                return value;
            }
        }
        return null;
    }

    private static String upgradeArtworkUrl(String url) {
        String upgraded = url.replace("100x100bb", "600x600bb")
                .replace("100x100", "600x600")
                .replace("60x60bb", "600x600bb")
                .replace("60x60", "600x600");
        if (upgraded.startsWith("http://")) {
            upgraded = "https://" + upgraded.substring("http://".length());
        }
        return upgraded;
    }

    private static HttpURLConnection openGet(String url) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(9000);
        connection.setReadTimeout(9000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("User-Agent", "BZ-Music/1.0 (Android album art lookup)");
        connection.connect();
        return connection;
    }

    private static Bitmap downloadBitmap(String imageUrl) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(imageUrl).openConnection();
            connection.setConnectTimeout(9000);
            connection.setReadTimeout(9000);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("User-Agent", "BZ-Music/1.0 (Android album art lookup)");
            connection.connect();
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                return null;
            }
            InputStream input = connection.getInputStream();
            return BitmapFactory.decodeStream(input);
        } catch (Exception ignored) {
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static String readFully(InputStream input) throws Exception {
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }
        }
        return builder.toString().trim();
    }
}
