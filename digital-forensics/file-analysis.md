# File Analysis — Triage, Magic Bytes, Signatures, Mismatches, Embedded Data

## Fastest First-Pass Checklist (Unknown File)

```bash
# Purpose: identify true type from magic bytes, ignore the extension entirely
file ./artifact

# Purpose: hash for evidence log / dedup before anything else touches the file
sha256sum ./artifact

# Purpose: printable strings, longer minimum length cuts noise
strings -n 8 ./artifact | less

# Purpose: header + trailer bytes — spot magic numbers, truncation, appended data
xxd ./artifact | head -n 20
xxd ./artifact | tail -n 20

# Purpose: scan for embedded files/signatures anywhere in the blob
binwalk ./artifact

# Purpose: full metadata dump (works on far more than just images)
exiftool ./artifact
```
Decision after these six: known container format → its own doc
(`common-file-formats.md`, `reverse-engineering/elf.md`/`pe.md`,
`pcaps.md`); unrecognized/raw data → escalate per Tree 1 in
`../methodology/triage.md`.

## Technique: Magic-Byte / Signature Identification

**When to use it**: always, first, on every unknown artifact.

**Fast path**:
```bash
# Purpose: file(1) reads magic-byte databases (/usr/share/file/magic) to ID format
file ./artifact
# Purpose: MIME-only output, useful for scripting comparisons
file --mime-type -b ./artifact
```

**Tools**: `file`, manual `xxd`/hex-editor inspection, `binwalk`.

**Expected Results**: a type string ("PNG image data, 800 x 600...", "Zip
archive data", "ELF 64-bit LSB executable", "PDF document, version 1.6").

**Indicators**: type string is confident and specific → trust it and route
to that format's doc. Type string is generic ("data") → likely raw/encoded/
encrypted/compressed content with no standard header, or a header that's
been stripped/corrupted deliberately.

**Failure Conditions**: `file` says "data" with no further info.

**Next Step**: manual magic-byte lookup at the front of the file with `xxd`
against a signature table (see below), or `binwalk` for embedded/offset
signatures.

**Common Mistakes**: trusting the file *extension* instead of running
`file`. CTF authors routinely rename a ZIP to `.png` or a PNG to `.txt` —
this is one of the most common and cheapest tricks to catch.

## Technique: Extension-vs-Actual-Type Mismatch

**When to use it**: whenever `file`'s output disagrees with the given
extension, or immediately as a standing check on every artifact.

**Fast path**:
```bash
# Purpose: compare stated extension against file(1)'s real determination
for f in *; do echo -n "$f: "; file -b "$f"; done
```

**Indicators**: `artifact.jpg` reported as "Zip archive data" (common:
JPEG/PNG containing an appended ZIP), `notes.txt` reported as "PNG image
data" (data hidden as an image, given a text extension to mislead).

**Next Step**: rename/copy with the correct extension and re-run standard
tooling for the true type; if a container-in-container (ZIP appended to
JPEG), extract per `common-file-formats.md`.

**Common Mistakes**: renaming the *original* file in place instead of a
copy — breaks evidence-handling and can confuse later hash comparisons.

## Common Magic Bytes Reference

| Bytes (hex) | Format |
|---|---|
| `89 50 4E 47 0D 0A 1A 0A` | PNG |
| `FF D8 FF` | JPEG |
| `47 49 46 38` | GIF |
| `25 50 44 46` | PDF (`%PDF`) |
| `50 4B 03 04` | ZIP (also DOCX/XLSX/APK/JAR — all ZIP-based) |
| `52 61 72 21 1A 07` | RAR |
| `7F 45 4C 46` | ELF |
| `4D 5A` | PE/EXE (`MZ`) |
| `1F 8B` | GZIP |
| `42 5A 68` | BZIP2 |
| `52 49 46 46` | RIFF (WAV/AVI, check bytes 8-11 for subtype) |
| `4F 67 67 53` | OGG |
| `49 44 33` | MP3 (ID3 tag) |
| `D0 CF 11 E0` | Legacy MS Office (DOC/XLS/PPT) |
| `4B 44 4D` / `53 51 4C 69 74 65` | SQLite (`SQLite format 3\0`) |

## Technique: Embedded Files & Appended Data

**When to use it**: `file` identifies a container format but the file size
is larger than the container's own declared content size, or `binwalk`
shows more than one signature.

**Fast path**:
```bash
# Purpose: scan entire file for known signatures at any offset
binwalk ./artifact

# Purpose: auto-extract everything binwalk recognizes into a subfolder
binwalk -e ./artifact

# Purpose: recursive extraction, useful for nested containers (zip-in-png-in-zip)
binwalk -Me ./artifact
```

**Expected Results**: a list of offsets + descriptions; a second signature
partway through the file is the strongest possible indicator of appended
data.

**Indicators**: second signature at a non-zero offset with a plausible file
type following it (another ZIP, another PNG, a filesystem).

**Failure Conditions**: `binwalk -e` extracts nothing usable, or only
extracts garbage/partial data (common with corrupted-on-purpose length
fields).

**Next Step**: manually carve using `dd` with the offset binwalk reported:
```bash
# Purpose: manually cut out an embedded file starting at a known byte offset
dd if=artifact.bin of=carved.bin bs=1 skip=<offset> 2>/dev/null
```
Then re-run `file`/`strings`/format-specific tooling on `carved.bin`.

**Common Mistakes**: running `binwalk -e` and then never actually looking
inside the extraction folder recursively — always `file`/`strings` every
extracted item, they often nest further.

## Technique: Entropy & Compression Detection

**When to use it**: `file` says "data", size is large, and you need to know
whether it's compressed/encrypted (high entropy, ~8 bits/byte) vs raw
structured data (lower, uneven entropy).

**Fast path**:
```bash
# Purpose: binwalk's entropy scan renders a plot showing entropy across the file
binwalk -E ./artifact

# Purpose: quick numeric approximation via ent, if installed
ent ./artifact
```

**Indicators**: flat high entropy across the whole file → likely
compressed or encrypted (see `cryptography/crypto-triage.md` next).
Entropy spikes in a sub-region only → that region specifically holds
compressed/encrypted/random data (e.g. an appended encrypted blob, an image
with an LSB-stego payload region).

**Next Step**: if flat-high across the whole artifact, treat as
crypto/compression triage. If localized, treat the surrounding structure as
a carrier (stego candidate) — see `../steganography/images.md`.

## Alternate Data Streams (ADS) — Windows/NTFS Artifacts

**When to use it**: disk/filesystem images from Windows, or files
originating from an NTFS volume, where hidden data may ride alongside a
normal file in a named stream.

**Fast path** (from a mounted NTFS volume or extracted image, using
`sleuthkit`):
```bash
# Purpose: list all streams (including ADS) attached to a file on an NTFS image
istat -f ntfs image.dd <inode_number>

# Purpose: The Sleuth Kit's fls can reveal named streams as filename:streamname
fls -f ntfs -r image.dd | grep ':'
```
**Indicators**: a filename with a `:streamname` suffix in `fls` output.

**Next Step**: extract the specific stream with `icat` and treat it as its
own artifact — full workflow in `disk-forensics.md`.

## Common Mistakes (File Analysis, general)

- Trusting extensions over `file` output.
- Modifying the original artifact instead of a copy.
- Stopping at the first `binwalk` hit instead of scanning the whole report
  for a second/third signature.
- Ignoring the file's *size* relative to its declared content dimensions —
  a 4x4 PNG that's 2MB is not innocent.
