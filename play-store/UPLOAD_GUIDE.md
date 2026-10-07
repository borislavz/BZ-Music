# Upload guide — BZ Music to Google Play

Do these in order. Keep File Explorer open on the two folders already launched.

**AAB:** `C:\develop\Muzika\app\build\outputs\bundle\release\app-release.aab`  
**Graphics / copy:** `C:\develop\Muzika\play-store\`

---

## 1. Sign in

1. Open https://play.google.com/console in your normal browser (Chrome recommended).
2. Sign in with the Google account that paid for the Play developer account.
3. Open the app **BZ Music** (`com.borislav007.muzika`).  
   - If the app does not exist yet: **Create app** → App name **BZ Music** → App or game: **App** → Free → accept declarations → Create.

---

## 2. Upload the AAB (Internal testing first)

1. Left menu → **Testing** → **Internal testing**
2. **Create new release** (or **Edit release**)
3. If asked about **Play App Signing**: choose **Continue** / opt in (recommended).
4. **Upload** → pick  
   `C:\develop\Muzika\app\build\outputs\bundle\release\app-release.aab`
5. Wait until processing finishes. Confirm version **1.0.2 (3)**.
6. Add a short release name, e.g. `1.0.2`
7. **Next** → **Save** → **Review release** → **Start rollout to Internal testing**
8. On Internal testing, add yourself as a tester (email) and open the opt-in link on a phone to verify install.

---

## 3. Store listing graphics & text

1. Left menu → **Grow** → **Store presence** → **Main store listing**
2. Upload from `play-store\`:
   - App icon → `icon-512.png`
   - Feature graphic → `feature-graphic.png`
   - Phone screenshots → `screenshot-01-main.png`, `screenshot-02-about.png`
3. Paste from `STORE_LISTING.md`:
   - Short description  
   - Full description  
   - App name: **BZ Music**
4. Category: **Music & Audio**
5. Contact email: `bobby.zahariev@gmail.com`
6. **Save**

---

## 4. Privacy policy URL (required)

Play will not let you finish without a public **https://** privacy URL.

Fastest free option:

1. Create a GitHub repo (e.g. `bz-music-privacy`)
2. Upload `play-store\privacy.html`
3. Settings → Pages → deploy from `main`
4. Copy the URL, e.g. `https://YOURUSER.github.io/bz-music-privacy/privacy.html`
5. Paste that URL into:
   - Main store listing → Privacy policy  
   - App content → Data safety (when asked)

Or publish the same text on Google Sites.

---

## 5. App content forms

Left menu → **Policy** / **App content** — complete every incomplete item. Answers are in `STORE_LISTING.md`:

- Ads → **No**
- Data safety → Audio files on-device; no sharing; HTTPS for album art
- Content rating → music player questionnaire
- Target audience → not directed at children
- News / COVID / etc. → No

---

## 6. Production release

1. Left menu → **Production** → **Create new release**
2. Either upload the same AAB again, or **Promote release** from Internal testing
3. Countries: choose as you prefer (or all)
4. **Review release** → **Start rollout to Production**
5. Confirm **Send for review**

Dashboard checkmarks must all be green before Production can submit.

---

## If something blocks you

| Problem | Fix |
|---------|-----|
| “Privacy policy required” | Host `privacy.html` and paste https URL |
| “Store listing incomplete” | Icon + feature graphic + ≥2 screenshots + descriptions |
| “Data safety not completed” | Fill form using `STORE_LISTING.md` |
| Signing / key mismatch | Use the same upload keystore; opt into Play App Signing |
| Target API error | This AAB is already targetSdk 36 — rebuild only if Console complains |

When you are signed into Console and stuck on a specific screen, tell me which page and the exact error text — I can give the next click.
