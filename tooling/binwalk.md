# binwalk Quick Reference

Purpose: scan a binary blob for known file-format signatures at any
offset, and extract/carve them out — the primary tool for "what's hiding
inside this file" triage.

```bash
# Purpose: scan and list every recognized signature with its offset
binwalk ./artifact

# Purpose: auto-extract everything recognized into a subdirectory
binwalk -e ./artifact

# Purpose: recursive extraction — unpacks nested containers automatically (archive-in-archive-in-image)
binwalk -Me ./artifact

# Purpose: entropy analysis — visualize compressed/encrypted regions even without a signature match
binwalk -E ./artifact

# Purpose: restrict scan to a specific signature type, cuts noise on large files
binwalk --signature ./artifact
```

## Indicators & Decisions

- A second signature at a non-zero offset → appended/embedded data, extract
  and re-triage per `../digital-forensics/file-analysis.md`.
- Entropy plot shows a flat high region with no matching signature →
  likely encrypted/compressed with no recognizable header — route to
  `../cryptography/crypto-triage.md`.
- `-e` extracts nothing despite a signature match → the signature may be a
  false positive (common with short/generic magic bytes) or extraction
  needs manual offset carving via `dd` (see `file-analysis.md`).

## Alternatives

- Manual magic-byte scanning with `xxd`/a hex editor when binwalk's
  signature database doesn't recognize a custom/unusual format.
- `foremost`/`photorec` for pure file-carving from unstructured/disk-image
  data without needing binwalk's broader signature database.

## Common Mistakes

- Running `binwalk` without `-e` and manually trying to carve offsets by
  hand when extraction would have done it automatically.
- Not scanning recursively (`-M`) and missing a container nested more than
  one level deep.
- Ignoring the entropy scan (`-E`) as a parallel signal when the signature
  scan alone comes back empty.
