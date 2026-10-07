# Google Play packaging notes — BZ Music

## What changed for Play compatibility

- Removed `MANAGE_EXTERNAL_STORAGE` (“All files access”), which Google Play rejects for typical music players.
- Browse/play through **MediaStore** + `READ_MEDIA_AUDIO` / `READ_EXTERNAL_STORAGE` (maxSdk 32).
- Playback uses **content URIs** (Android `MediaPlayer` + LibVLC for WMA).
- **Storage Access Framework** (“Choose folder”) lets the user grant one specific folder tree — Play-approved alternative when MediaStore is not enough.
- MediaStore listing includes audio even when `IS_MUSIC` is unset (common for copied demos).
- Release packaging targets an **Android App Bundle (`.aab`)**.
- Signing passwords live in local `keystore.properties` (not committed).

## Build the Play upload bundle

From `C:\develop\Muzika`:

```powershell
.\gradlew.bat bundleRelease
```

Output:

`app\build\outputs\bundle\release\app-release.aab`

Also available for sideload testing:

```powershell
.\gradlew.bat assembleRelease
```

## Signing

1. Keep `muzika-release.jks` and `keystore.properties` private (listed in `.gitignore`).
2. Back up the keystore and passwords offline — you need the same key for all future updates.
3. In Play Console, prefer **Play App Signing** (upload key = this keystore).

## Play Console checklist

**Ready-to-upload pack:** `play-store\` (graphics, screenshots, `STORE_LISTING.md`, `privacy.html`).

1. Create the app with package id `com.borislav007.muzika` and store title **BZ Music**.
2. Upload `app-release.aab` (version **1.0.2 / 3**, targetSdk **36**).
3. Host `play-store\privacy.html` publicly (GitHub Pages / Google Sites) and paste that URL into:
   - Store listing → Privacy policy
   - App content → Data safety
4. Paste store copy and form answers from `play-store\STORE_LISTING.md`.
5. Upload `icon-512.png`, `feature-graphic.png`, and at least two phone screenshots from `play-store\`.
6. Finish Data safety, ads (No), content rating, target audience.
7. Promote Internal testing → Production and submit for review.

## Privacy summary (short)

See string `privacy_summary`, `PRIVACY.md`, and hostable `play-store\privacy.html`.
