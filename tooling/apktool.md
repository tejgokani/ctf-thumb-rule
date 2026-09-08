# apktool Quick Reference

Purpose: decode an APK's resources and manifest to their original
(non-binary) form, and disassemble bytecode to smali — the ground-truth
companion to jadx's readable-but-sometimes-lossy decompilation (see
`../reverse-engineering/apk.md` for the full workflow this fits into).

```bash
# Purpose: full decode — manifest, resources, and smali all extracted
apktool d ./app.apk -o apk_out

# Purpose: decode without disassembling to smali (resources/manifest only, faster)
apktool d -s ./app.apk -o apk_out

# Purpose: rebuild an APK after modifying decoded output (e.g. for dynamic-analysis patching)
apktool b apk_out -o modified.apk
```

## What to Inspect After Decoding

```bash
# Purpose: the ground-truth manifest — activities, services, receivers, providers, permissions, exports
cat apk_out/AndroidManifest.xml

# Purpose: string resources — a very common place for hardcoded flags/keys/URLs
cat apk_out/res/values/strings.xml

# Purpose: raw resources — arbitrary files bundled as-is
ls -la apk_out/res/raw/

# Purpose: assets — arbitrary files, no resource-ID processing, common flag hiding spot
ls -la apk_out/assets/

# Purpose: smali — disassembled bytecode, needed when jadx's decompilation of a specific method is broken
find apk_out/smali* -iname '*.smali' | xargs grep -l 'flag'
```

## Indicators & Decisions

See `../reverse-engineering/apk.md`'s full ordered workflow — apktool's
manifest is the step-3 anchor for identifying suspicious
components/permissions before diving into code.

## Alternatives

- `jadx` — Java-like decompilation (more readable, sometimes lossy) — use
  alongside apktool, not instead of it.
- Direct `unzip` + `aapt dump badging`/`aapt dump xmltree` for a quicker
  manifest-only look when full resource decoding isn't needed.

## Common Mistakes

- Skipping apktool because jadx "already shows the manifest" — jadx's
  manifest view can be incomplete; apktool's decoded XML is the reliable
  ground truth.
- Not checking `res/raw/` and `assets/` — non-code carriers that are easy
  to forget when focused on Java/smali logic.
