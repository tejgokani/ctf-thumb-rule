# STRATA

> One vault. Many drives.

An Android app that federates multiple Google accounts into a single logical storage pool.
Files are chunked, encrypted on-device, and striped across whichever accounts you connect —
10 accounts at 15 GB each becomes one ~150 GB encrypted, auditable vault. No backend server:
authentication, storage, and recovery all happen directly between your phone and Google Drive.

- **[SETUP.md](SETUP.md)** — the Google Cloud Console walkthrough (10 minutes, one-time).
- **[ARCHITECTURE.md](ARCHITECTURE.md)** — how it works and why.

## Quick start

```bash
./gradlew testDebugUnitTest   # 58 tests, no accounts or device needed
./gradlew assembleDebug       # → app/build/outputs/apk/debug/app-debug.apk
```

Then follow [SETUP.md](SETUP.md) to register the Android OAuth client and install the APK.

## What it looks like

A brutalist data-vault: full-black instrument panel, monospace data, hairline borders, no soft
shadows. The signature screen is the **Shard Map** — a literal chunk-index × account matrix
showing exactly where every byte of a file physically lives.

## What makes this safe to actually use

- **Encrypted before it ever leaves the device.** AES-256-GCM, a passphrase-derived key STRATA
  never stores. Google sees opaque ciphertext and opaque metadata — never your files, never even
  their names.
- **`drive.file` scope** — the app can only ever see files it created itself, never your
  existing Drive contents.
- **Self-describing chunks.** Every chunk carries its own encrypted manifest entry, so losing the
  local app database entirely is recoverable straight from Drive — provable with the in-app
  "Simulate Disaster Recovery" action.
- **Never deletes your local originals.** STRATA is an uploader, not a mover.

See [ARCHITECTURE.md](ARCHITECTURE.md) for the honest limitations list — what this build has and
hasn't been able to verify without a physical device.
