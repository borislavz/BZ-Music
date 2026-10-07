# BZ Music

Local Android music player by Borislav Zahariev (BZ).

| | |
|--|--|
| **Package** | `com.borislav007.muzika` |
| **Store name** | BZ Music |
| **Min SDK** | 24 |
| **Target / Compile SDK** | 36 |
| **Version** | 1.0.3 (versionCode 4) |

## Build

```powershell
cd C:\develop\Muzika
.\scripts\setup-new-machine.ps1   # first time on a machine
.\gradlew.bat assembleRelease
.\gradlew.bat bundleRelease
```

- APK: `app\build\outputs\apk\release\app-release.apk`
- Play AAB: `app\build\outputs\bundle\release\app-release.aab`

Signing uses `muzika-release.jks` + `keystore.properties` (not committed).

## Move to another PC

See **[PORTING.md](PORTING.md)**. Pack with:

```powershell
.\scripts\pack-for-port.ps1
```

## Play Store

Assets and copy: `play-store\` (see `UPLOAD_GUIDE.md`, `STORE_LISTING.md`).  
Privacy (hosted): `https://sites.google.com/view/bz-music-privacy`
