# Our Little Universe · Memory Studio v0.5.2

This is a focused update: photo, video and audio memory viewers now have **Save to Phone** and **Choose Folder**. Android 10+ saves original-quality images to Pictures/Our Little Universe, videos to Movies/Our Little Universe, audio to Music/Our Little Universe, with no recompression. Choose Folder uses Android's document picker on all supported versions; Android 8/9 also use this picker for Save to Phone. Nothing in the approved blue/purple design or family archive logic is changed. **The existing v3 home interface is not redesigned**: the v3.1 UI (v3 with only the unwanted two card arcs removed) remains, with a new Memory Studio entry and richer memory metadata.

## Features included
- Original native Android journal, original-quality local photos/videos and voice-note recorder, offline speech recognition when device supports it, letters, firsts, and private SQLite storage
- Family contributor names and credits on memories; settings let Dad, Mariah, Grandma, Papa, etc. take turns on one device
- Family ZIP imports merged by stable memory IDs without duplicates, photos/voice attachment union
- Family ZIP exports include author and supplemental records; distinct **full backup** and **family-only archive**: memories marked private are not included in family-only ZIPs
- Comments and reactions with contributor credit, favorites, album names and searchable tags, manual locations and captions
- Native memory search and filters (favorites, letters, voice), photo/video gallery and real local photo slideshow
- Calendar date browsing, On This Day, month/year recap navigation
- 35 milestone suggestions from ultrasound to graduation
- Pregnancy countdown estimated from due date, pregnancy diary and bump-photo entry shortcuts; due date estimates are not medical guidance
- Height/weight history and custom native chart (no medical percentile comparisons)
- Birthday interview questions, prompts, quotes, gratitude and family traditions, recipes, family stories, name ideas, wishlists and packing lists
- Time capsule letters with future opening dates (REMINDER LABELS ONLY; no encryption or lock)
- Local reminders with Android notifications when permission granted (Android controls timing and battery behavior)
- Printable, locally rendered PDF baby book
- All content stays on-device until intentionally exported

## Retrieving one of your memories
Open a saved photo or video in a memory, tap **Save to Phone**, and check Photos/Gallery or Files → Pictures/Our Little Universe (video: Movies/Our Little Universe). Voice recordings have the same option in the playback dialog. Or tap **Choose Folder** to write the original directly to a selected Downloads/other folder. The app keeps its private original. None of these actions requires a server or subscription.

## Installation and migration
This version deliberately uses `com.littleuniverse.memories.cosmic.studio.our.media` to avoid replacing v0.3.2 and losing data due to ephemeral CI signing keys. To migrate: export a FULL ZIP in the old app, import in v0.5.2, check entries and media, then export a fresh full backup from v0.5.2. Keep the previous app until everything has been verified.

## Limits
- This is manual ZIP exchange; not live sharing and not an iOS app.
- Same-entry edits on different devices preserve local wording; deletions do not propagate.
- ZIPs and exported PDF books are not encrypted; sharing them through a messaging service is outside the app and may create copies.
- The PDF export is a compact printable journal, not an automated professional bound photo-book order.
- The growing-up slideshow plays existing photos inside the app; it does not generate a new encoded MP4.
- Offline speech-to-text needs a compatible Android on-device recognition service/model.
- Reminders are not guaranteed at exact times: Android battery restrictions and notification permissions apply.
- Unlimited free cloud storage, AI-generated photos/videos, iPhone native installs, face transformations, photo-face timelapse alignment, and automatic real-time sync are **not** included because they would violate platform, offline, cost, or feasibility constraints.
- No Play Store, external account, paid subscription, external server, or INTERNET permission.

## Development
Android Java 17, API 35, Gradle 8.10.2. The project remains isolated from the main Galactic-8ball branch. No personal family media should ever be committed to GitHub.
