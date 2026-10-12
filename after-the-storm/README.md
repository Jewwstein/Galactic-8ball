# After the Storm: Heavy Rescue 3D v0.4 — CAT 545D Skidder integration

Android portrait prototype, built with offline Three.js and Java WebView.

- Added actual user-supplied CAT 545D Skidder model (mobile-optimized glTF/GLB, embedded JPEG textures), loaded through a local Android asset bridge.
- New *SKIDDER* machinery selection button, dedicated HUD with grapple open/close, log grab/release, boom lift/lower, boom extension/retraction, blade up/down, engine and parking brake.
- Grip up to three nearby fallen trees/logs behind the skidder, then haul them to the yellow depot and release to earn cleanup rewards.
- The actual imported model is unrigged. A separate animated grapple/claw assembly and gameplay-grade simplified collisions control the interactions; no full-suspension, hydraulic or deformable-physics simulation yet.
- All other v0.3 machinery remains available.
- Gameplay works offline; textures and models are bundled within APK.

## Model rights

The supplied model archive has no explicit redistribution license. This branch is a test-only integration; verify asset license before publicly redistributing the APK or model, and do not merge to a public release repository until rights are confirmed.

## Build

The GitHub Actions workflow unzips `after-the-storm/app/src/main/assets/skidder.zip` into `skidder.glb` before compiling. That base64 source is an asset for private testing, not a game script.