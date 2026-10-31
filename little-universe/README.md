# Little Universe — offline Android baby memory journal
This is **a completely separate Android project** on the `little-universe-v1` branch of Galactic-8ball. It does not modify Galactic 8-Ball on `main`.

## Version 0.1.0
- Native Android APK, no web wrapper, no network permission
- Pregnancy announcement shortcut dated October 31, 2026
- Chronological memory timeline, journal, milestones, and letters
- Original photos and videos copied into private on-device files
- Native SQLite database
- Baby's name, parents, and due date customization
- Complete backup and restore to a user-chosen ZIP document
- No Play Store, no cloud, no subscriptions, no tracking

## Building
The GitHub Actions workflow builds `little-universe/app/build/outputs/apk/debug/app-debug.apk` and publishes it as a GitHub Actions artifact and release. Run locally with Android SDK API 35 and Gradle 8.10.2:
`gradle -p little-universe :app:assembleDebug`

## Important
The first prototype is signed with a CI-generated debug certificate. A later update may be signed differently, so it may **require exporting a backup, uninstalling v0.1.0, installing the new APK, then restoring your ZIP**. Do not uninstall until you have a verified backup. We should configure a stable private app signing key before regular updates.

App data lives in Android private storage and is removed on uninstall. Keep backup ZIPs **outside the phone**, ideally on a computer and an external drive. Photos/videos added to the app are copied, not deleted from the gallery. Family/network sharing is intentionally not implemented yet.

Do not put private photos, videos, or ZIP backups in this public GitHub repository. These contain personal family information.
