# Our Little Universe v0.6.0 — Keepsake Maker

## What's new
**Keepsake Maker** is a real local image editor inside Memory Studio. It lets your family turn original photos already saved to memories into finished collectible keepsake images, like a pregnancy announcement or a milestone collage. Nothing goes to a remote server.

- Select any **1–4 original photos** from the family timeline; page through larger galleries.
- Write a **title** and a personal **dedication** for the keepsake.
- Select one of three restrained color themes: **Blue & Purple Galaxy**, **Purple Velvet**, or **Midnight Storybook**. No ugly decorative arcs or wireframe shapes.
- Choose three sizes: **1080×1350** keepsake portrait, **1080×1080** square collage, or **1080×1920** phone-story layout.
- See a real image preview before saving.
- **Save a full-resolution PNG to the phone's Gallery**, **Share through Android's share chooser**, or **Add a copy as a new memory in Our Little Universe**.
- Original photo files are never edited. New timeline keepsakes are eligible for normal full backups and family ZIP exchange.
- Uses Android Bitmap/Canvas and the existing MediaSaver with no external providers or internet access.

**Where:** Home → The Memory Studio → Keepsake Maker, or Memory Studio → Photo & video gallery → Create a photo keepsake.

## Preserved
The approved blue-and-purple **v3 appearance** remains the same (with the requested card arches removed), and everything from v0.5.3 remains: voice recordings, timeline, letters, family ZIP merge, media exports, Recently Deleted, backup integrity checks, and journal/growth features.

## Migration
Because GitHub Actions prototype APKs use ephemeral signing keys, v0.6.0 installs separately (`com.littleuniverse.memories.cosmic.studio.our.cards`). Keep v0.5.3 installed, export its **full ZIP** and import it into v0.6.0, confirm you can see every memory and original picture, and keep a separate backup before uninstalling anything.

## Limitations and privacy
These are offline static image cards, **not** auto-generated videos or an AI child prediction. User-selected photos and captions are personal; sharing through an external messaging app is the user's choice, and those providers can store the resulting image. Exported ZIP files remain unencrypted. The new composer works with ordinary photos supported by the Android device, not video clips. This is not a live cloud service.

## Tests
Eight Android instrumentation tests cover family ZIP merging, collisions, recovery, backup verification, original image export, one/four-photo cards in multiple sizes, and byte-for-byte identity of exported PNGs. Build and emulator checks are handled through the repo's GitHub Actions workflows.
