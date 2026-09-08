# exiftool Quick Reference

Purpose: read (and write) metadata across a very wide range of file
formats — the default first tool for any image/document/media artifact.

```bash
# Purpose: full metadata dump, all groups, including unknown/duplicate tags — the CTF-recommended default flags
exiftool -a -u -g1 ./artifact

# Purpose: same as above but also shows raw unknown binary tags
exiftool -a -u -unknown -G1 ./artifact

# Purpose: extract GPS coordinates specifically, formatted for direct use
exiftool -gpslatitude -gpslongitude -c "%.6f" ./photo.jpg

# Purpose: strip all metadata (for comparison against the original — reveals what was actually present)
exiftool -all= ./artifact -o stripped_copy

# Purpose: batch-process a directory, one summary line per file
exiftool -T -filename -comment -gpsposition ./photos/*.jpg
```

## Indicators & Decisions

- Default (no-flag) `exiftool` output can hide unknown/duplicate tags —
  always use `-a -u -g1` for CTF work, the interesting tag is often the
  one that's hidden by default.
- Non-generic `Comment`/`Description`/`Artist`/XMP field content → likely
  the hiding spot; decode/investigate that value directly.
- Fully stock/empty metadata → flag is probably elsewhere (pixel data,
  appended bytes) — see `../steganography/images.md`.

## Alternatives

- `identify -verbose` (ImageMagick) — image-specific, sometimes surfaces
  slightly different fields.
- Manual PNG chunk walking (Python `struct`) when exiftool doesn't surface
  a custom/nonstandard chunk — see `../digital-forensics/metadata.md`.

## Common Mistakes

- Running bare `exiftool file` and missing tags hidden without `-a -u`.
- Not checking non-image formats (PDF, Office docs, audio, video) for
  metadata — exiftool supports all of these equally well.
