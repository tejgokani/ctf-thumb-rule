# jadx Quick Reference

Purpose: decompile Android APK/DEX bytecode to readable Java-like source —
the primary readability tool for APK logic analysis (see
`../reverse-engineering/apk.md` for the full workflow this fits into).

```bash
# Purpose: decompile an APK to a source tree on disk
jadx -d jadx_out ./app.apk

# Purpose: GUI mode — better for interactive browsing/searching large codebases
jadx-gui ./app.apk

# Purpose: decompile a raw .dex file directly (when you only have classes.dex, not the full APK)
jadx -d jadx_out ./classes.dex

# Purpose: broad grep sweep across the decompiled output — same pattern as reverse-engineering/apk.md
grep -RniE 'flag|secret|key|password|token' jadx_out/
```

## Indicators & Decisions

- Decompilation succeeds cleanly → read the output directly, prioritize
  manifest-flagged suspicious components (see `apk.md`).
- Decompilation fails/produces garbage for specific methods (heavily
  obfuscated bytecode) → fall back to smali-level reading for just those
  methods, or Ghidra's Java decompiler as a second opinion.

## Alternatives

- `apktool` — doesn't decompile to Java; instead gives you resources,
  manifest, and smali (lower-level bytecode-as-text) — use *alongside*
  jadx, not instead of it (see `apk.md`'s reasoning for running both).
- Ghidra (with the Ghidra-DEX/APK extension) — an alternative
  decompilation engine, occasionally succeeds where jadx produces broken
  output on heavily obfuscated code.

## Common Mistakes

- Using jadx alone and never checking `apktool`'s raw manifest/resource
  output — some data (raw resource files, exact manifest structure)
  isn't fully represented in jadx's view.
- Giving up when a specific method's decompilation is broken instead of
  falling back to smali or an alternate decompiler for just that method.
