# Little Universe — Blue & Purple Cosmic Edition v0.3.0

Native offline Android keepsake app. This is the **real implemented UI**, not a rendered mockup.

## Changes from v0.2
- Parents' shared blue and purple color palette throughout. No gender-based assumptions.
- Animated native galaxy, twinkling stars and glowing moon on the home screen and memory editor.
- Native custom-drawn multi-layer 3D glass cards with glossy highlights, colored rim lighting and subtle bottom shadow.
- Raised vivid blue and violet story / voice cards with tactile press and release animations.
- Pulsing 3D glowing central Create orb, star orb in the header, and an illuminated recording orb.
- Richer vault, memory feed, firsts, buttons and menu styling.
- Gentle animated card entrances. All effects drawn on-device by Android Canvas, no remote images or cloud dependencies.
- Existing offline voice recording, on-device dictation (when supported), journal, photos/videos, milestones, letters, and original-size ZIP backup/restore all preserved.

## Installing without losing earlier records
This version deliberately has its **own Android application ID** (`com.littleuniverse.memories.cosmic`). The CI debug signature is not persistent, so an APK with the old application ID might not install as an update. Therefore v0.3 installs alongside v0.1/v0.2. If you recorded earlier memories, export a ZIP in the older app's Vault, then use the new app's Vault → Restore. Confirm your records and save a fresh external ZIP before uninstalling the older version.

## Offline privacy and cost
No INTERNET permission, tracking, subscriptions, paid assets or cloud APIs. Database and original media stay locally on the device. Export full backups regularly to computer/external media. The code lives in the `little-universe-v1` branch of `Jewwstein/Galactic-8ball`; the Galactic 8-Ball main branch is untouched. Never put your family media/backup ZIP in this public repo.

## Build
GitHub Actions workflow `.github/workflows/build-little-universe.yml`, Java 17, Gradle 8.10.2, Android 35. Install the release APK from `little-universe-v0.3.0`.
