# BZ Music — Play Console copy & form answers

Paste these into Google Play Console. Package: `com.borislav007.muzika` · Version: **1.0.2 (3)**

---

## Main store listing

| Field | Value |
|-------|--------|
| **App name** | BZ Music |
| **Short description** (max 80) | Local music player with shuffle, repeat, and WMA playback. |
| **Full description** | See below |
| **App icon** | `icon-512.png` (512×512) |
| **Feature graphic** | `feature-graphic.png` (1024×500) |
| **Phone screenshots** | `screenshot-01-main.png`, `screenshot-02-about.png` |
| **Category** | Music & Audio |
| **Contact email** | bobby.zahariev@gmail.com |
| **Privacy policy** | Host `privacy.html` publicly, then paste the https URL |

### Short description (copy)

```
Local music player with shuffle, repeat, and WMA playback.
```

### Full description (copy)

```
BZ Music is a simple local music player for Android.

• Browse your device music library or pick a folder
• Play common audio formats with shuffle and repeat
• Play WMA files (built-in LibVLC) — something most stock players cannot do
• Optional internet use only to look up album art when a track has no cover

No account required. Your music stays on your device.
Made by Borislav Zahariev (BZ).
```

---

## App content / declarations

### Ads
- **Contains ads?** No

### Privacy policy
- URL: *(your hosted `privacy.html`)*

### Data safety (recommended answers for this app)

| Question | Answer |
|----------|--------|
| Does your app collect or share any of the required user data types? | **Yes** (audio files accessed on device for playback) |
| **Audio files** | Collected: Yes · Shared: **No** · Processed ephemerally / on-device for playback |
| Is this data collected, shared, or both? | Collected (on device) |
| Is this data processed ephemerally? | Yes (used for playback; not uploaded by the app) |
| Is this data required or can users choose? | Required for core functionality (playing music) |
| Why collected? | App functionality |
| Location / Personal info / Financial / Photos / Contacts / etc. | **No** |
| Data encrypted in transit? | **Yes** (HTTPS for optional album-art lookups) |
| Users can request data deletion? | **No** / N/A — no account or server-side user data |

Notes for reviewers: The app reads MediaStore audio with `READ_MEDIA_AUDIO` (or storage on older Android). It does not use All files access. Optional network is only for album artwork.

### Permissions declaration
- Photos and videos / Music and audio: used to list and play local audio.

### Target audience
- Target age group: **18 and over** (or “all ages” / general if Console offers it — not directed at children)
- Appeal to children: **No**

### News app
- **No**

### COVID-19 contact tracing / status apps
- **No**

### Data safety “government apps”
- **No**

### Content rating (IARC questionnaire)
Answer as a **music player**:
- No user-generated public content / social features
- No violence, sexual content, drugs, gambling
- No sharing location
- Unrestricted / Everyone-style rating expected

### Financial features
- **No**

### Health
- **No**

### Government
- **No**

---

## Release checklist

1. Upload AAB: `C:\develop\Muzika\app\build\outputs\bundle\release\app-release.aab`
2. Complete store listing graphics + descriptions above
3. Host privacy policy → paste URL
4. Finish Data safety + content rating + audience
5. Internal testing → install on a device
6. Promote to **Production** → Submit for review

Google usually reviews in about **a few hours to a few days**. You can release after they approve.
