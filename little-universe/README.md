# Little Universe — Version 3 (arch-only fix)

This APK **restores the original version 0.3 UI without redesigning anything**. The only rendered UI change relative to the v0.3 source is the removal of two large decorative arc strokes in `CosmicUI.GlassDrawable` that were appearing on every tile/card. The card backgrounds, raised shadows, outlines, gloss, blue/purple palette, starfield, moon, header, controls, animations, layout and app features are precisely the v0.3 implementation. Other orb and moon details are intentionally untouched.

The Android application ID is different (`com.littleuniverse.memories.cosmic.arcfix`) to allow a side-by-side installation under the temporary CI debug signing arrangement. This means memories previously saved in v0.3 are not automatically present: **export a ZIP from its Vault and restore it in the new app before uninstalling anything**.

No cloud, analytics or network permission. Original v0.3 journal, voice, milestones, files, and backup remain. This is a surgical fix, not a redesigned v0.4.
