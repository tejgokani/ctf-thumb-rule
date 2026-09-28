# STRATA — Setup Guide

This is the one part of building STRATA that has to be done by hand: STRATA never ships a
Google API key or client secret, because an Android OAuth client is identified by your app's
**package name + signing certificate fingerprint**, not by an embedded credential. That's why
this file exists.

---

## ⚠️ Read this first — the keystore is a one-way door

`strata.keystore` (committed at the repo root) is not a throwaway debug key. **Every account you
connect trusts this exact keystore.** If it's lost, or you sign a future build with a different
one, that new build's SHA-1 no longer matches the OAuth client below — the app almost certainly
loses access to every chunk it already stored.

**Before you go further:**
```bash
cp strata.keystore ~/somewhere-that-is-not-this-repo/strata.keystore.backup
```
Put a copy somewhere durable and *off this device* — a password manager's file storage, a USB
drive, printed paper in a drawer. Do this now; it only takes a second and there is no recovery
path if you skip it and later lose this checkout.

The store/key password for local development is `strata-dev-only` (see `app/build.gradle.kts`).
For anything beyond your own device, override it with the `STRATA_KEYSTORE_PASSWORD` /
`STRATA_KEY_PASSWORD` environment variables instead of leaving the default in place.

---

## 1. Get the SHA-1 fingerprint

Already generated for this checkout's keystore:

```
SHA-1: DA:97:00:2F:76:60:2A:A4:10:3B:66:3F:7F:3C:66:A4:A8:A5:01:29
```

If you ever need to regenerate it:
```bash
keytool -list -v -keystore strata.keystore -alias strata -storepass strata-dev-only | grep SHA1
```

## 2. Create a Google Cloud project and enable the Drive API

1. Go to [console.cloud.google.com](https://console.cloud.google.com/) → create a new project (or pick an existing one you're happy dedicating to this).
2. **APIs & Services → Library** → search **Google Drive API** → **Enable**.

## 3. Configure the OAuth consent screen

1. **APIs & Services → OAuth consent screen**.
2. User type: **External** (this is a personal project, not a Workspace org).
3. Add scopes: `.../auth/drive.file` and `.../auth/drive.appdata`. Both are **non-sensitive**
   (R1 in the architecture notes), so Google does not require a security review for them.
4. Add yourself (and anyone else who'll use the app) as a test user, OR — better — skip straight
   to publishing:
5. **Publish the app to Production.** This is not optional: an app left in "Testing" status has
   its OAuth grants expire every **7 days** (R7), which will make background transfers silently
   stall about once a week. Because the scopes are non-sensitive, publishing does not trigger
   Google's paid app-verification review — it's free and immediate.

## 4. Create the Android OAuth client

1. **APIs & Services → Credentials → Create Credentials → OAuth client ID**.
2. Application type: **Android**.
3. Package name: `com.tejgokani.strata`
4. SHA-1 certificate fingerprint: `DA:97:00:2F:76:60:2A:A4:10:3B:66:3F:7F:3C:66:A4:A8:A5:01:29`
5. Create.

That's it — no client ID or secret needs to go anywhere in the app. Android resolves the OAuth
client at runtime purely from the combination of package name and signing certificate.

## 5. Install and run

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```
(Or open the project in Android Studio and hit Run — same keystore either way, since debug and
release share one signing config for exactly the reason explained above.)

On first launch:
1. Set a passphrase and **save the recovery key it shows you** — this is the only other way
   back into your data if you forget the passphrase. STRATA cannot reset it for you.
2. Add your first node: this opens the standard Android account picker, then Google's normal
   consent screen for the two scopes above.
3. Repeat for as many Google accounts as you want to pool.
4. Upload something from the FILES tab and watch it stripe across your nodes on the SHARD MAP
   screen.

## First-run checks worth doing once

Two things the architecture notes could not verify without a live account (see `ARCHITECTURE.md`
§12/§13) — try these early, since their answers matter more than anything else in the app:

- **Uninstall, then reinstall the app**, and confirm it can still see the chunks it created
  before. (Expected: yes — `drive.file` access is tied to the OAuth client, not the install.)
- **Revoke STRATA's access** at
  [myaccount.google.com/permissions](https://myaccount.google.com/permissions), then re-add that
  node in the app. Check whether the app can read the chunks that account already held.
  (Expected, per the architecture notes: **no** — treat this as a real risk, not a hypothetical
  one, and avoid revoking access as a way to "reset" a node.)

## Limits worth knowing

- 15 GB free per Google account, shared with Gmail and Photos.
- 750 GB/day upload ceiling per account (Drive API limit) — irrelevant until you're moving
  serious volume.
- Files you didn't create yourself in that Drive account are never touched or visible to
  STRATA — the `drive.file` scope only ever sees what STRATA itself uploaded.
