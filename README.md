<div align="center">
<img width="1200" height="475" alt="GHBanner" src="https://ai.google.dev/static/site-assets/images/share-ais-513315318.png" />
</div>

# Run and deploy your AI Studio app

This contains everything you need to run your app locally.

View your app in AI Studio: https://ai.studio/apps/ac2fac60-5953-464e-9b6b-d9223101899b

## Run Locally

**Prerequisites:**  [Android Studio](https://developer.android.com/studio)

1. Open Android Studio and import the project folder.
2. Create a file named `.env` in the project root and set `GEMINI_API_KEY` in it, or let the helper script create the placeholder file from `.env.example`.
3. Run `run-local.bat` from the project root for a one-command local build. If a device or emulator is connected, it will install the app; otherwise it will assemble the debug APK.
4. The data merge helper writes its output to `data/merged/merged_deduped.json` and the duplicate report to `data/merged/duplicate_groups.txt`.

## Wikipedia 2026 attack totals

Refresh the 2026 UAV strike snapshot, rebuild app events, and regenerate the separate region layer with one command:

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\update-from-wikipedia-uav-strikes.ps1 -Year 2026
```

The aggregate dataset is stored in `data/final/region_attack_totals_2026.json` and bundled as `app/src/main/assets/region_attack_totals_2026.json`. In the app, open the map and select `Attack totals` to display the region gradient and date breakdown.

The narrative event register is stored in `data/final/special_events_2022_2025.json` and bundled as `app/src/main/assets/special_events_2022_2025.json`. It contains the Crimean Bridge, A-50/Il-22M, Russian bridge, and Operation Spiderweb groups without importing article tables.

## Public website

The Firebase Hosting site is in `site/` and includes the public legal and methodology pages:

- `/`
- `/privacy-policy.html`
- `/terms.html`
- `/methodology.html`
- `/app-ads.txt`

Firebase Hosting is configured in `firebase.json` for the `gy-signal-studio` project. Authenticate once, then deploy:

```powershell
firebase.cmd login
firebase.cmd deploy --only hosting
```

`deploy-hosting.bat` runs the deployment command after Firebase CLI is installed and authenticated. The initial Hosting URL is expected to be `https://gy-signal-studio.web.app`. Before enabling an ad network, replace the placeholder in `site/app-ads.txt` with that network's verified seller declaration.
