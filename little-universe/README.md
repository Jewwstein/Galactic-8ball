# Little Universe v0.3.2 — family ZIP exchange

This is the original **v0.3.1 blue-and-purple design** (original v3 with the two card arches removed). Only the family archive merge behavior and wording on the existing Vault page have changed.

## The whole family can participate without hosting
1. Each Android family member installs this APK.
2. Dad exports `Vault → Export / share full family ZIP` and sends the ZIP to Mom or Grandma via any private file-sharing method.
3. They choose `Vault → Merge a family ZIP (no duplicates)`, import Dad's archive, and add their own memories, photos, letters and voice recordings locally.
4. They export a **new full ZIP** and send it back.
5. Dad imports the ZIP. New memory UUIDs are added, existing ones are skipped, and new media attached to existing memories is merged.
6. Exchanges can happen as many times as you want in either direction.

The ZIP is **not password protected**. Only send it through trusted, private methods. Anyone with the file can access the saved photos, videos and recordings. File transfer services may store or relay the ZIP while it is being sent, even though Little Universe itself has no cloud integration.

## Merge guarantees
- Stable memory IDs maintained through all imports/exports; repeated import is idempotent.
- New memories are inserted, never overwritten.
- Existing local dates, title, journal text and letter text are preserved when other people edit the same event independently. Added audio/image/video media are unioned by filename.
- New media names are checked, and a mismatched existing filename causes a safe abort rather than losing the original.
- No duplicate attachments from repeat imports.
- Missing original media causes a failure instead of a broken memory.
- Existing local profile settings are only filled if they are blank.
- The import confirmation tells how many new events, duplicates skipped, attachments added and original files copied.
- **Not a full conflict-resolution system yet:** edits to the same existing memory's title/body on different devices do not synchronize, and deleting a memory on one device doesn't delete it elsewhere. Save edits as new memories if everyone needs to see them.
- This is manual offline exchange, not live synchronization; everyone needs the most recently shared ZIP to receive updates.

## Restore / safety
The app deliberately installs under separate Android package `com.littleuniverse.memories.cosmic.family`, because previous GitHub Actions APKs used temporary debug signing keys and cannot reliably update in place. Export your existing data from v0.3.1, then import the ZIP on v0.3.2 and confirm before deleting anything. Keep a second backup on another device.

## Tests
Android instrumentation tests verify a two-person merge including title preservation, new media merge, repeat imports without duplicates, and rejecting media collisions. The GitHub Actions smoke workflow compiles and runs the tests on Android emulator.

## Privacy
Completely offline within the app, no paid server, no INTERNET permission, no subscriptions, no tracking. Sharing through ZIP files works between Android installations. iPhone has no native APK support, so iPhone sharing remains a separate future task.
