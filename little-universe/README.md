# Little Universe — Refined Depth Edition v0.4.0

Native, completely offline Android baby memory app for the family.

## What changed
- Deleted the two decorative arcs drawn across *every tile* in the original `GlassDrawable`, including buttons, memory cards, the home feature tile and the bottom dock.
- Removed artificial orbit/moon outlines and wireframe geometry from the animated hero background; it now has subtle blue/purple lighting and restrained twinkling stars.
- Rebuilt the common card material with soft, offset shadows, polished bevel edges, a diffused top-left reflection, a subtle inner sheen, and lower-edge depth.
- Simplified the top app header and small action labels. Retained tactile press animations, delicate card entrances, and pulsating Create control.
- Existing offline photo/video albums, memory journal, milestones, letters, voice messages, offline dictation when Android supports it, and complete ZIP backups remain intact.

## Safe installation
This release uses a separate application ID `com.littleuniverse.memories.depth`. Because earlier prototypes used temporary GitHub CI signing certificates, Android cannot reliably upgrade those in place. Install v0.4 alongside earlier versions. If you have records in an older version, first export its Vault ZIP, then restore that ZIP in v0.4. Make sure the memories are present, back up v0.4 externally, and only then consider uninstalling prior versions.

## Privacy
No cloud, ads, paid providers or Internet permission. Media and voice recordings live in private storage on the phone, so make regular full ZIP exports to a computer or external device. Do not upload family media to this public repository.

## Build
GitHub Actions workflow `.github/workflows/build-little-universe.yml`; Java 17, Gradle 8.10.2, Android API 35. Release tag `little-universe-v0.4.0`.
