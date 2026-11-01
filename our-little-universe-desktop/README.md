# Our Little Universe v0.7.0 — Private Family Hub

## What changed
You can now make your Windows PC the **master family archive**. Family phones keep their own offline copies and synchronize original media and memory records directly with your PC. New memories from Grandma appear on every phone that reconnects, no daily manual ZIPs. This is a first operational sync build, not a hosted cloud product.

### Architecture
- The original Windows EXE now embeds a small **self-written** Node.js HTTP server that starts when the desktop app starts. The server listens ONLY on \`127.0.0.1:43877\` and stores data in the current Windows user's private app-data directory.
- Family members reach your PC privately via **Tailscale Serve HTTPS**, which forwards connections into that local listener. Do not use Funnel, public router port forwarding, or expose the hub HTTP endpoint openly.
- Each family member gets a single-use, seven-day **Family Hub pairing code** from the Windows app. The hub issues a per-device bearer token, supports contributor/viewer roles, and allows the PC owner to revoke access.
- The hub stores memory records, structured keepsakes and original media on your PC. Sync sends only missing media and new/changed records, never remote backups to commercial cloud storage.
- On Android, go to Vault → Private Family Sync; pair once with the private HTTPS address and invitation, then use Sync Now or leave the app open for ~2.5-minute checks.
- On the iPhone Safari PWA, go to Family Vault → Connect to private family hub. Pair once, then it checks about every two minutes while active.
- On the Windows app, open Family Vault → Invite a family member / Manage paired devices. The PC must stay on and the Windows app must remain running.
- Existing ZIP exports and imports are preserved as offline recovery options. A real full backup on separate storage is still required to survive PC drive failure.

### One-time private remote connection (no cloud memory storage)
1. Install the free personal [Tailscale](https://tailscale.com/download) client on your Windows PC, your Android device, and each family member's phone. Sign in; invite family accounts to the private network, or share just the PC to each outside account.
2. On your Windows PC, run \`tailscale serve --bg 43877\` in Windows Terminal. Tailscale will provide a private \`https://your-computer.your-tailnet.ts.net\` address, and may ask you to enable its free HTTPS certificates. Check the result using \`tailscale serve status\`.
3. On the Windows app, create an invitation for each relative. Share the private HTTPS address and invitation code **privately**. On their phone, connect Tailscale, then paste the address and code into the Our Little Universe Family Vault and tap Pair.
4. Create a sample memory on the phone, tap Sync Now, and confirm it arrives on the Windows app and another paired phone. Only after confirming should you import original family ZIP backups into the Windows app to seed the hub.

### Important limitations
- This is PRIVATE REMOTE NETWORKING: relatives far away still use their own Wi-Fi/mobile internet, but do not need direct ZIP messages or a paid cloud photo database.
- **Tailscale is the transport and identity layer**, not storage for your family photos. It is third-party networking software; relay/coordinator metadata may pass through Tailscale infrastructure, and traffic is encrypted. If your requirement is to use *no third party at all*, a VPN server you self-host and manually configure is an alternative.
- iOS web apps cannot guarantee background synchronization while closed. Android currently synchronizes when the app is open; a future native Android background worker can extend it.
- The PC must be powered on and the Family Hub EXE running to accept sync.
- The first sync can take substantial time if many video originals exist. Media files are limited to 1 GB each in this version.
- Deletion is NOT remotely propagated in v0.7.0: deliberately so one phone cannot permanently delete everybody's history. Conflicting edits to existing shared text preserve the original PC wording while merging new attachments and metadata. New memories with unique IDs are synchronized.
- Owner tokens/invite codes are stored locally and should be treated as secrets. ZIP backups are unencrypted. The master hub's files are stored unencrypted in local Windows user storage; protect that PC with Windows account login and full-disk encryption.
- Personal Tailscale is free for up to six users at time of building. Different networking configurations and firewall policies may require adjustments. This release is not yet verified on family members' real networks.
- The v0.7.0 APK installs separately because earlier prototypes used inconsistent debug signing keys. From previous Android app export a full ZIP, import into this version and verify it before removing anything.

## Source layout
- \`hub-server.cjs\`: local PC master archive, pairing, auth, media streaming and incremental changes.
- \`preload.cjs\` + \`electron.cjs\`: securely expose owner-only controls to the desktop UI.
- \`src/hub-sync.js\`: iPhone/browser and Windows sync logic.
- \`little-universe/app/.../FamilyHubSync.java\`: native Android sync, pairing and original-file verification.
- \`tests/hub.test.mjs\`: automated tests for unauthorized access, device pairing, original byte exchange, duplicate prevention, persistence and revocation.

No subscription, external photo database, backend VPS, or phone App Store is required.
