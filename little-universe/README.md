# Little Universe · Keepsake Edition v0.2.0

A personal, completely local Android keepsake app dedicated to your child's lifetime story.

**This lives on the isolated `little-universe-v1` branch** of the original Galactic-8ball GitHub repo. Galactic 8-Ball `main` is not modified.

## What's new
- Cinematic night-sky / moon / starlight home screen and warm keepsake-style visual identity
- Redesigned story feed with photo cards, video playback and voice-note players
- One-tap **Record your voice** — saves full original AAC audio files on the phone, no transcription required
- On-device dictation for titles and stories **only if Android has a local speech recognizer** (no network speech fallback)
- Full-screen memory editor with date picker; customizable baby's name and due date
- Milestone progress, letters vault, original-quality photo/video attachments, ZIP backup/restore
- Local private SQLite database and files. No network permission, accounts, analytics, ads, or cloud.

## Installing
Install the APK from GitHub Releases. Version 0.2.0 has a **separate Android applicationId** (`com.littleuniverse.memories.keepsake`) so it can install alongside the v0.1.0 prototype. To bring earlier entries across, use the v0.1.0 app's Settings → Export ZIP then v0.2.0's Vault → Restore ZIP. Do not uninstall the older APK until you have confirmed the copy and safe backup.

## Speech details
Android 12+ offers an on-device recognizer when an offline speech service/model exists. Only that service is used; if unavailable, audio recording still works on Android 8+. The app never asks for internet permission. Microphone permission is requested when needed.

## Backups / release notes
The app uses private app storage that Android removes on uninstall. Export your ZIP regularly and keep copies outside the device. Debug-signing from CI is not stable across builds; plan an enduring private signing key before in-place upgrades. **Do not upload personal family photos or backup ZIPs to this public source repository.**

## Build
Java 17, Gradle 8.10.2, Android 35. Workflow: `.github/workflows/build-little-universe.yml`.
