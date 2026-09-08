# Metadata Analysis

## Fastest First-Pass Checklist

```bash
# Purpose: dump every metadata field the tool recognizes, including maker notes
exiftool -a -u -g1 ./artifact

# Purpose: also print duplicate/unknown tags exiftool otherwise hides
exiftool -a -u -unknown -G1 ./artifact

# Purpose: for PDFs specifically, dump the document info dictionary + XMP
exiftool -a -u ./document.pdf
```

## Technique: Standard Metadata Dump

**When to use it**: on every image, document, audio, or video artifact,
immediately after `file`/`strings` triage.

**Tools**: `exiftool` (primary — widest format support), `identify -verbose`
(ImageMagick, image-specific).

**Expected Results**: camera make/model, GPS coordinates, software used to
create/edit the file, author/creator fields, timestamps, comment fields.

**Indicators**: a `Comment`, `Description`, `Artist`, `Copyright`, or custom
XMP field containing a non-generic string (base64-looking, flag-shaped, a
URL, a name). GPS EXIF tags present when the challenge is OSINT/geolocation
flavored. Software field showing an unusual editor (e.g. a hex editor or
script) suggesting the metadata was hand-crafted for the challenge.

**Failure Conditions**: metadata is stock/empty (freshly stripped or
generated file) — this itself is an indicator that the flag is elsewhere
(pixel data, appended data, filename).

**Next Step**: if metadata is empty/stock, move to `steganography/images.md`
or `file-analysis.md`'s embedded-data techniques.

**Common Mistakes**: only running `exiftool` with no flags — default output
suppresses some tags. Always use `-a -u -g1` (all tags, unknown tags,
grouped) for CTF work since the interesting tag is often the one exiftool
hides by default.

## Technique: GPS / Geolocation Extraction

**Fast path**:
```bash
# Purpose: pull GPS tags specifically and format as a Google-Maps-ready link
exiftool -gpslatitude -gpslongitude -c "%.6f" ./photo.jpg
```
**Next Step**: feed coordinates into `osint/geolocation.md`.

## Technique: PNG Chunk Inspection (beyond standard EXIF)

PNG doesn't natively carry EXIF the way JPEG does — data typically lives in
`tEXt`/`zTXt`/`iTXt` ancillary chunks, which `exiftool` also reads, but a
manual chunk walk catches malformed/custom chunks that parsers skip.

**Fast path**:
```bash
# Purpose: exiftool decodes standard PNG text chunks
exiftool -a -u ./image.png

# Purpose: manual chunk walk — lists every chunk type/length in file order
python3 - <<'EOF'
# Purpose: walk PNG chunks manually to catch nonstandard/custom chunk names exiftool may not surface
import struct
data = open("image.png","rb").read()
i = 8  # skip PNG signature
while i < len(data):
    length = struct.unpack(">I", data[i:i+4])[0]
    ctype = data[i+4:i+8].decode("latin1")
    print(ctype, length)
    i += 8 + length + 4  # length + type + data + CRC
EOF
```
**Indicators**: an unrecognized chunk type (not one of `IHDR IDAT IEND PLTE
tEXt zTXt iTXt gAMA cHRM sRGB pHYs tIME`) is a strong sign of a
custom-embedded payload.

**Next Step**: extract that chunk's raw bytes and treat as its own
artifact (often base64 or directly the flag).

## Technique: Document Metadata (Office/PDF)

**Fast path**:
```bash
# Purpose: PDF document info + XMP metadata
exiftool document.pdf

# Purpose: DOCX/XLSX/PPTX are ZIPs — inspect docProps/core.xml and app.xml directly
unzip -p document.docx docProps/core.xml
unzip -p document.docx docProps/app.xml
```
**Indicators**: `Creator`, `LastModifiedBy`, `Company`, `Comments` fields
often carry challenge-author fingerprints or direct hints/flags. Revision
history in `.docx` (via `word/document.xml` tracked changes) can reveal
deleted content.

**Next Step**: for PDFs with embedded objects/JS, see
`common-file-formats.md`'s PDF section.

## Common Mistakes

- Running `exiftool` without `-a -u` and missing duplicate/unknown tags.
- Ignoring metadata timestamps that don't make sense (a "2003" creation
  date on a file clearly made for a 2026 competition) — this is often
  itself a clue, not noise.
- Forgetting metadata exists on non-image formats (PDF, Office docs, audio,
  video all carry rich metadata).
