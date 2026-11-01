# Our Little Universe — Family Relay v0.4.0

**Your Windows PC holds the family archive. Every other device gets the APK or the iPhone PWA website.**

This release replaces the failed router-dependent approach with an **outbound relay connection**. It does not install or use any third-party VPN software, change router settings, intercept Spark traffic, or require any additional app on a family member's phone.

## One-time owner setup in the Windows EXE

Go to **Family Vault → Set up automatic family sync**. Our Little Universe can deploy its own source code (\`relay/worker.mjs\`) to an internet-accessible free account. An outside hosting company runs only our relay program; it does not store the baby's photo archive.

Only the owner needs a free Workers hosting account (Cloudflare provides a free limited-use plan). The owner enters their 32-character account ID and a scoped Workers Scripts Edit API token on the Windows setup screen. The app deploys the code, configures a secret and the public HTTPS address, and connects the Windows hub automatically. **The API token is not saved.** No one else creates an account or uses the provider's dashboard.

The owner then uses **Invite a family member**. The invitation is a single URL to the existing iPhone PWA. A family member opens it and taps Join my family. On Android, the native app can handle the same link (or users can join via the web app); the family member never configures networking.

Windows auto-reconnects to its own relay over outbound WSS. Every API call is sent from family phones over HTTPS and forwarded over the long-lived connection to the owner's local Windows server. The hub handles pairing, permission checks, device revocation and permanent archive storage. The relay stores no family records, queued photos or videos. It transmits limited-size chunks.

## Implementation
- \`relay/worker.mjs\`: first-party HTTP/WebSocket forwarding, Durable Object room (in-memory only; no persistent family content), CORS controls and owner authentication
- \`relay-setup.cjs\`: Windows-only one-time API deployment of our relay source; no extra software installed
- \`relay-agent.cjs\`: native Node/Electron outbound WebSocket connection with automatic reconnection and request forwarding to localhost
- \`hub-server.cjs\`: local archive and deduplicating permission-checked media API, now with partial originals and paginated change feeds
- \`src/hub-sync.js\`: iPhone/browser incremental upload/download of large originals in 192 KiB chunks
- Android \`FamilyHubSync.java\`: same native protocol on Android, no VPN service
- \`tests/relay.test.mjs\`: on-disk original checksum tests and owner/phone relay routing tests

## Constraints
- This is a first implementation, not a confirmed live family sync. An actual hosted deployment and cross-network test still require the owner's one-time free hosting account credentials.
- Your PC must be on, running Our Little Universe, and connected to the internet to receive new updates. Offline memories remain on the device until its next sync.
- The free hosting plan has request and usage limits, particularly with many large videos. With 192 KiB media pieces, a 1 GB video uses thousands of requests.
- The relay's HTTPS terminates at the hosting provider. Files transit in memory there in chunks, but no permanent family archive or database is created. The connection from the relay to Windows uses WSS. Do not claim the relay is end-to-end encrypted or independent of the hosting provider.
- iPhone PWA and current Android code sync while their apps are open. They cannot guarantee background sync after force-close.
- Conflict handling is conservative: previous text is kept on existing memories, extra media and tags merge; remote deletions are not propagated.
- Back up the Windows archive to an external drive; the PC master copy is otherwise a single point of failure.
- Earlier Android prototypes had different package IDs/signing. The v0.8.0 APK installs separately; export a ZIP from the old app and import into this APK before relying on it.
- Do not grant blanket permission to a provider account token or share it in ChatGPT. Use the appropriate narrowly scoped Workers permission, then revoke the token after setup succeeds if desired.
