# Our Little Universe — Web + Windows Companion (v0.1.0)

This is a **new companion app**, not an Android replacement. It shares the family archive format little-universe-android-v1 with the native Android app. It runs in Safari/Chrome and packages into a Windows portable executable with Electron.

## Included
- Private browser/desktop local storage (IndexedDB), usable offline after PWA installation.
- Timeline, journals, milestones, letters, audio recording (where supported), photos and videos.
- Gallery and playback, original media download.
- Author credit, favorites, tags, album labels, profile settings.
- Deletion/restore, family-only versus full ZIP, ZIP integrity verification.
- **Bidirectional Android ZIP exchange:** preserves stable record UUIDs, merges only missing entries and attachments, and keeps existing text unchanged. Reads/writes the original Android archive.json and media/ paths.
- A responsive blue/purple theme inspired by the approved Android v3 design without decorative arches.
- No remote database or app store required.

## iPhone
A website must be published under HTTPS before the app can be installed on iOS Home Screen: open in Safari → Share → Add to Home Screen. It then caches for offline use. Never upload real family ZIPs to GitHub; import them into the locally running application. GitHub Pages requires Pages to be enabled in Settings → Pages (GitHub Actions). This project's CI attempts publishing.

## Windows EXE
GitHub Actions builds an unsigned portable Windows x64 EXE using Electron. Download it from the Windows artifact, unzip and launch it. Windows SmartScreen may warn about unsigned software.

## Safety and differences
No live sync. Each phone/PC stores its own local data until a ZIP is exchanged. Browser website data deletion can remove private memories; keep full backups. ZIP files are **not encrypted**. Large ZIPs consume browser memory. GitHub Pages hosts app code, not private photos.

This companion has core timeline, media, voice, family ZIP compatibility. It does NOT yet have the full advanced Android suite (growth charts, 35 milestone picker, photo Keepsake Maker, printable book). Voice recording depends on browser microphone permission.

## Build
Run npm install; npm run build; npm run dev; on Windows npm run windows. npm test runs ZIP format smoke tests. The app uses zero-cost tools.
