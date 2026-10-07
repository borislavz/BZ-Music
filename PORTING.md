# BZ Music — Port to another PC (develop + publish)

Package: `com.borislav007.muzika` · Display name: **BZ Music** · Version: **1.0.2 (3)** · targetSdk **36**

Read this first on the new machine. Then run `scripts\setup-new-machine.ps1`.

---

## Critical: do not lose the signing key

Play updates **must** be signed with the same upload key:

| File | Purpose |
|------|---------|
| `muzika-release.jks` | Release / Play upload keystore |
| `keystore.properties` | Passwords + alias (gitignored) |

If these are lost and you are **not** enrolled in Play App Signing with a recoverable upload key, you **cannot** update the same Play listing.

Keep a second offline backup (USB / encrypted drive).

---

## What to copy

### Option A — packed zip (recommended)

On this PC:

```powershell
cd C:\develop\Muzika
.\scripts\pack-for-port.ps1
# optional offline Maven cache (~550MB extra):
.\scripts\pack-for-port.ps1 -IncludeMavenCache
```

Creates `C:\develop\BZ-Music-port.zip` (source, Play assets, keystore, Gradle zip; Maven cache optional).

Copy that zip to the other PC and extract (e.g. `C:\develop\Muzika`).

### Option B — copy the folder

Copy the whole `Muzika` tree **except** you may omit:

- `.gradle\`
- `build\`
- `app\build\`
- `.idea\` (Android Studio regenerates it)
- Old root `*.apk` / duplicate AAB copies (optional)

**Must include:**

- `app\` (source)
- `gradle\` (wrapper + `gradle-8.13-bin.zip` offline fallback)
- `gradlew` / `gradlew.bat`
- `*.gradle.kts`, `gradle.properties`
- `local-maven-repo\` (recommended — offline deps / past SSL issues)
- `muzika-release.jks` + `keystore.properties`
- `gradle-truststore.jks` (optional SSL workaround)
- `play-store\` (listing graphics, copy, privacy, upload guide)
- `PRIVACY.md`, `PLAY_STORE.md`, `PORTING.md`, `README.md`
- `test-audio\` (optional sample media)
- Current AAB: `app\build\outputs\bundle\release\app-release.aab` or root `BZ Music.aab`

---

## New PC prerequisites

1. **Android Studio** (Ladybug / recent) — installs SDK + JBR (Java 17+)
2. SDK platforms: **Android 36** (API 36) + build-tools
3. **Cursor** (optional) — open the project folder as workspace
4. Google account with **Play Console** access for BZ Music

---

## Setup on the new PC

```powershell
cd C:\develop\Muzika   # or wherever you extracted
.\scripts\setup-new-machine.ps1
```

This will:

- Write `local.properties` (`sdk.dir=...`)
- Point Gradle at local `gradle-8.13-bin.zip` if present (offline-friendly)
- Optionally wire `gradle-truststore.jks` if SSL downloads fail
- Verify keystore files exist

Then:

```powershell
.\gradlew.bat bundleRelease
```

Output: `app\build\outputs\apk\release\` and `app\build\outputs\bundle\release\app-release.aab`

Open in Android Studio: **File → Open** → project root (folder with `settings.gradle.kts`).

---

## Publishing continuity

| Item | Location / note |
|------|-----------------|
| Play package id | `com.borislav007.muzika` (never change) |
| Upload key | `muzika-release.jks` + `keystore.properties` |
| Store listing pack | `play-store\` — see `UPLOAD_GUIDE.md` |
| Privacy policy | Hosted Google Site: `https://sites.google.com/view/bz-music-privacy` (or your URL) |
| Listing copy / Data safety | `play-store\STORE_LISTING.md` |
| Contact email | `bobby.zahariev@gmail.com` |
| Console | https://play.google.com/console |

If the new personal developer account requires **closed testing** (12 testers / 14 days), that status lives in Play Console — not in this repo. Continue from the same Play app entry.

---

## Git note

This project may have **no commits** yet. Secrets stay gitignored (`.jks`, `keystore.properties`, `local.properties`). For a private backup remote, commit source + `play-store` docs/assets, and keep the keystore **only** in the port zip / offline backup — not on a public remote.

---

## Quick verify checklist

- [ ] `muzika-release.jks` and `keystore.properties` present  
- [ ] `.\scripts\setup-new-machine.ps1` succeeded  
- [ ] `.\gradlew.bat bundleRelease` succeeds  
- [ ] About screen still shows version **1.0.2 (3)**  
- [ ] Play Console still accepts uploads signed with this keystore  
- [ ] Privacy URL still opens publicly  
