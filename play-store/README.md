# BZ Music — Google Play store setup pack

Everything you need to finish the listing is in this folder:

`C:\develop\Muzika\play-store\`

| File | Play Console field |
|------|--------------------|
| `icon-512.png` | App icon **512 × 512** |
| `feature-graphic.png` | Feature graphic **1024 × 500** |
| `screenshot-01-main.png` | Phone screenshot |
| `screenshot-02-about.png` | Phone screenshot |
| `STORE_LISTING.md` | App name, descriptions, Data safety, content rating answers |
| `privacy.html` | Host this publicly → paste the URL as Privacy policy |

## What you still must do in Play Console

1. Open [Google Play Console](https://play.google.com/console) → **BZ Music**
2. **Grow → Store presence → Main store listing** — paste text from `STORE_LISTING.md`, upload the graphics
3. Host `privacy.html` (GitHub Pages or Google Sites) and paste the **https://** URL
4. **App content** — Data safety, ads (No), content rating, target audience (answers in `STORE_LISTING.md`)
5. Confirm release AAB is uploaded:  
   `C:\develop\Muzika\app\build\outputs\bundle\release\app-release.aab` (version **1.0.2 / 3**)
6. **Production → Create release** (or promote from Internal testing) → **Submit for review**

Contact email for the listing: `bobby.zahariev@gmail.com`
