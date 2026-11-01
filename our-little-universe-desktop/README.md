# Our Little Universe — Built-In Family Direct Sync v0.3.0

## What this update changes
The app no longer requires Tailscale, a VPN application, or any extra software on relatives' phones.

Your Windows PC runs the first-party **Our Little Universe Family Hub**. The PC and every phone keep their local archives, then push new memories and original media to the hub and pull missing items whenever the app is open.

An owner-only button, **Enable built-in remote access**, tries to configure your home router with first-party UPnP code and obtains a public HTTPS certificate with a first-party ACME implementation. The certificate is issued by Let's Encrypt (free public certificate authority), valid for ~6 days and renewed automatically while the hub is running. The Windows app alone runs the local and internet servers. It opens public TCP 80 only for certificate validation and HTTPS TCP 443 for authenticated family sync. There is **no other application to install**.

## How the family joins
1. Owner runs the Windows Family Hub and selects **Family Vault → Enable built-in remote access**.
2. When the screen says *Ready*, owner selects **Invite a family member** and copies the generated single-use join link.
3. Relative opens the invite link in Safari/Chrome, taps **Join my family**. No VPN account, router login, manual IP entry, or command line for the relative. The iPhone can add the website to its Home Screen. The Android APK also registers an optional link handler; the OS may offer to open the invitation in the native app.
4. Memories and original attachments sync both ways when the apps are active. Routine updates are incremental, not whole ZIP imports. Existing full ZIP backups still work.

## Honest requirements and limits
- This is **direct home hosting**. Your PC must be on and the app must be running. You must have a **public reachable IPv4 address** and a router capable of forwarding TCP **80 and 443**. The Windows app first tries automatic UPnP; if the router doesn't support it, the PC owner may need to configure port forwarding manually. The relative never does.
- **Carrier-grade NAT (CGNAT), inbound port filtering, and unconfigurable router firewalls cannot be bypassed by software only** when no external relay/infrastructure is allowed. With CGNAT, ask your home ISP for a public IPv4 address; a later IPv6 mode is possible when the connection supports inbound IPv6.
- Public HTTPS certificate issuance uses Let's Encrypt, but **no family photos, voice clips or journal entries are uploaded to a cloud service**. TLS and public certificates are standards, not a separate VPN app. The ACME client is in our Windows EXE. If certificate validation cannot reach port 80, the app reports the problem; it must never silently skip TLS verification.
- Remote hub endpoints use per-device secret bearer tokens, one-time expiring invitations, authentication for all media downloads, and the hub owner's revocation controls. Keep your computer locked and encrypted and retain backups on another drive.
- The iPhone PWA syncs while it is **open**; iOS background execution for PWA is not guaranteed. Android syncs while active. This release does not guarantee background sync while closed.
- Deletions are not propagated to relatives in this first version, preventing accidental destruction across the family.
- Complex simultaneous changes to the same memory's wording are not merged; established wording is retained and extra media/metadata merge. Future versions should add revision history and conflict-resolution dialogs.
- Free hosting of the PWA *app code* at GitHub Pages is independent of the private family content. ZIP backups are not encrypted.
- First cross-platform implementation; test small fake memories and an old ZIP before trusting irreplaceable family archives. Windows EXE and Android APK builds use development signatures. The new APK installs separately, so use a verified backup ZIP to migrate content.

## Source and automated tests
- \`hub-server.cjs\`: first-party server, media streaming, auth, pairing, synchronization.
- \`home-router.cjs\`: first-party SSDP/UPnP discovery and optional router port mapping.
- \`direct-acme.cjs\`: first-party ACME short-lived public IPv4 TLS certificate implementation (no Certbot).
- \`direct-access.cjs\`: owner-initiated remote server startup and certificate renewal.
- \`electron.cjs\`, \`preload.cjs\`: owner-only local desktop controls.
- \`src/hub-sync.js\`: offline-capable browser and Windows sync client.
- Android \`FamilyHubSync.java\`: native device-to-PC sync over standard HTTPS.
- \`tests/direct-access.test.mjs\`: IP reachability classification, CSR signature/IP SAN, secure-listener off by default.
- \`tests/hub.test.mjs\`: token handling, no duplicate memories, access revocation, original media bytes.

## Clear distinction
**No third-party applications installed.** The public internet, router firmware, free certificate authority, and the pre-existing website hosting are infrastructure and cannot be replaced by purely offline code when relatives are miles apart. If there is no remotely reachable address, a relay or ISP public-address change is necessary. No claim of universal connection is made.
