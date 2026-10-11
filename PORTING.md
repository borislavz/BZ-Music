# BZ Music — Port to another PC (Android Studio)

Package: `com.borislav007.muzika` · Display name: **BZ Music** · Version: **1.0.8 (8)** · targetSdk **36**

Read this first on the new machine. Then run `scripts\setup-new-machine.ps1`.

---

## Critical: do not lose the signing key

Play updates **must** be signed with the same upload key:

| File | Purpose |
|------|---------|
| `muzika-release.jks` | Release / Play upload keystore |
| `keystore.properties` | Passwords + alias (**gitignored** — not in git) |

If these are lost and you are **not** enrolled in Play App Signing with a recoverable upload key, you **cannot** update the same Play listing.

Keep a second offline backup (USB / encrypted drive).

---

## What to copy

### Option A — packed zip (recommended)

On this PC:

```powershell
cd C:\develop\Muzika
.\scripts\pack-for-port.ps1
# optional offline Maven cache (larger zip):
.\scripts\pack-for-port.ps1 -IncludeMavenCache
```

Creates `C:\develop\BZ-Music-port.zip` (source, Play assets, keystore, Gradle wrapper zip; Maven cache optional).

Copy that zip to the other PC and extract (e.g. `C:\develop\Muzika`).

### Option B — copy the folder

Copy the whole `Muzika` tree **except** you may omit:

- `.gradle\`
- `build\`
- `app\build\`
- `.idea\` (Android Studio regenerates it)
- Old root `*.apk` / duplicate AAB copies (optional)

**Must include:**

- `app\` (source + resources)
- `gradle\` (wrapper + `gradle-8.13-bin.zip` if present)
- `gradlew` / `gradlew.bat`
- `*.gradle.kts`, `gradle.properties`
- `local-maven-repo\` (recommended if network/SSL is flaky)
- `muzika-release.jks` + `keystore.properties`
- `gradle-truststore.jks` (optional SSL workaround)
- `play-store\` (listing graphics, copy, privacy, upload guide)
- `scripts\` (`setup-new-machine.ps1`, `pack-for-port.ps1`)
- `PRIVACY.md`, `PLAY_STORE.md`, `PORTING.md`, `README.md`
- `.cursor\rules\` (optional — Cursor shortcuts: `go`, Version5 restore)
- `test-audio\` (optional sample media)
- Latest AAB/APK under `release-artifacts\` if you packed with the script

**Do not rely on git alone for the keystore** — `.jks` and `keystore.properties` are gitignored.

---

## New PC prerequisites

1. **Android Studio** (recent stable) — installs SDK + JBR (Java 17+)
2. SDK: **Android API 36** + matching build-tools
3. Open the project root (folder with `settings.gradle.kts`) via **File → Open**
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
- Optionally wire `gradle-truststore.jks` if you pass `-EnableTruststore`
- Verify keystore files exist

Then in Android Studio: **Sync Project with Gradle Files**, or:

```powershell
.\gradlew.bat assembleRelease bundleRelease
```

Outputs:

- `app\build\outputs\apk\release\app-release.apk`
- `app\build\outputs\bundle\release\app-release.aab`

If dependency download fails with SSL errors:

```powershell
.\scripts\setup-new-machine.ps1 -EnableTruststore
```

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

If the developer account requires **closed testing** (12 testers / 14 days), that status lives in Play Console — not in this repo. Continue from the same Play app entry.

---

## Git note

Local git exists (e.g. tag `Version5`). Secrets stay gitignored (`.jks`, `keystore.properties`, `local.properties`). For a private backup remote, commit source + `play-store` docs/assets, and keep the keystore **only** in the port zip / offline backup — not on a public remote.

---

## Quick verify checklist

- [ ] `muzika-release.jks` and `keystore.properties` present  
- [ ] `.\scripts\setup-new-machine.ps1` succeeded  
- [ ] Android Studio opens the project and Gradle sync works  
- [ ] `.\gradlew.bat assembleRelease bundleRelease` succeeds  
- [ ] About screen shows version **1.0.8**  
- [ ] Play Console still accepts uploads signed with this keystore  
- [ ] Privacy URL still opens publicly  
