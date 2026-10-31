# Our Little Universe — Memory Safety Edition v0.5.3

This patch focuses on keeping priceless family memories safe. It keeps the approved blue-and-purple original-v3 design and **all v0.5.2 features** unchanged.

## Included in this release
- **Recently Deleted / Recycle Bin:** long-press a memory, move it to Recently Deleted; restore its date, title, journal text, notes, photo/video/audio attachments and contributor details anytime.
- **Permanent deletion requires explicit confirmation:** attached originals are deleted only if no other live or deleted memory references them.
- **Full external ZIP backups now preserve the recycle bin** and its attached media. Family-only ZIPs intentionally do *not* share deleted memories.
- **ZIP exports include per-file SHA-256 checksums**, allowing more dependable verification of original media.
- **Verify an exported backup ZIP** from Vault. Reads each file, checks ZIP CRC and SHA-256 hashes if present, verifies all referenced media are included; works on older ZIPs without hashes (CRC/file checks only). Verification is read-only and does not import or overwrite any memories.
- **Check my memories and media** from Vault. Reads every original saved photo, video, and audio and reports missing/unreadable attachments.
- **A local deletion is not automatically undone by re-importing an older family ZIP.** Restore it explicitly from Recently Deleted if you want it back.
- The approved original-v3 UI, memory studio, gallery, local voice messages, save-to-phone, PDF export, family contributor credits, and manual family ZIP merge are preserved.

## Use
Vault → Recently Deleted, Check my memories and media, or Verify an exported backup ZIP. To delete a memory, long-press its timeline card (as before); it now moves into the recovery area instead of erasing immediately.

## Privacy and important limitations
- Full ZIP backups are **not encrypted**: store them privately. SHA-256 detects modifications/corruption but provides no password protection or authentication.
- The local library check measures file presence/readability; it cannot prove that an original already corrupted before the first backup was created is correct.
- This app is offline and no cloud/server permissions are used. Export a full ZIP and verify it on a *different* device or storage location to protect against phone loss.
- Family sharing deliberately excludes the recycle bin. Deletions still do not propagate to other family members, which is intentional.
- Prototype APKs are temporarily signed by GitHub CI. This v0.5.3 package installs alongside older versions rather than replacing them; use Full backup ZIP from the older app, import into v0.5.3, verify your files, then keep a separate external backup. Do not uninstall the older app before confirming migration.

## Testing
Android instrumentation includes 6 tests: duplicate-free ZIP merge, attachment integrity, collision prevention, gallery export original bytes, recycle restore, no resurrection on family import, ZIP verifier check. CI workflow builds a native APK and runs tests in an Android emulator.
