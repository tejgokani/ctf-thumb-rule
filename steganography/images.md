# Image Steganography

## Fastest First-Pass Checklist (Stego Image)

```bash
# Purpose: metadata first — cheapest possible check
exiftool -a -u -g1 image.png

# Purpose: strings sweep — sometimes the payload is just plaintext appended
strings -n 8 image.png | tail -50

# Purpose: scan for embedded/appended files or archives
binwalk image.png
binwalk -e image.png

# Purpose: PNG/BMP-specific LSB and channel analysis tool
zsteg image.png

# Purpose: dedicated LSB/steg extraction tool with passphrase support (JPEG primarily)
steghide extract -sf image.jpg
```

## Technique: Metadata & Dimensions

**Fast path**: covered fully in `../digital-forensics/metadata.md` — run
that workflow first. Also sanity-check reported dimensions against actual
pixel data size; a mismatch (declared 100x100 but file size implies far
more data) suggests appended or hidden content.

```bash
# Purpose: compare stated dimensions/color depth against raw expected size
identify -verbose image.png | grep -E 'Geometry|Depth|Colors'
```

## Technique: Strings & Appended Data

**Fast path**: already in checklist — `strings` + `binwalk` catch the
"just concatenated raw data after the image ends" pattern, which is common
and cheap to check before pixel-level analysis.

```bash
# Purpose: manually locate the IEND chunk (PNG end marker) and check for trailing bytes after it
python3 -c "
data = open('image.png','rb').read()
idx = data.find(b'IEND')
print('trailing bytes after IEND:', len(data) - (idx+8))
print(data[idx+8:idx+108])
"
```

## Technique: LSB (Least Significant Bit) Steganography

**When to use it**: metadata/appended-data checks are clean, image file
size looks proportionate to its dimensions (ruling out simple appending),
and the challenge is stego-flavored.

**Fast path**:
```bash
# Purpose: zsteg checks all standard LSB bit-plane/channel-order combinations automatically (PNG/BMP)
zsteg image.png
zsteg -a image.png    # -a = try all combinations, slower but thorough

# Purpose: steghide for JPEG — requires a passphrase (try empty first, then common/hinted ones)
steghide extract -sf image.jpg -p ""
steghide extract -sf image.jpg -p "guessed_password"

# Purpose: view individual bit planes visually for anomalies (PIL/Python)
python3 -c "
from PIL import Image
img = Image.open('image.png').convert('RGB')
px = img.load()
w, h = img.size
out = Image.new('RGB', (w, h))
outpx = out.load()
for y in range(h):
    for x in range(w):
        r, g, b = px[x, y]
        outpx[x, y] = ((r&1)*255, (g&1)*255, (b&1)*255)  # amplify LSB to full brightness
out.save('lsb_visual.png')
"
```
**Indicators**: `zsteg` reports a plausible result (text/flag pattern) in
one of the standard bit-plane combos; the amplified LSB-visualization image
shows a readable pattern/text/QR code instead of pure noise.

**Failure Conditions**: `zsteg -a` finds nothing meaningful across all
combos → likely not LSB, or uses a non-standard/randomized bit order
requiring a specific tool matching the challenge's stated stego method.

## Technique: Color Channel / Alpha Channel Isolation

**Fast path**:
```bash
# Purpose: extract and view each channel independently — data sometimes hidden in a single channel (often alpha)
python3 -c "
from PIL import Image
img = Image.open('image.png')
for i, ch in enumerate(img.split()):
    ch.save(f'channel_{i}.png')
"
```
**Indicators**: one channel visually differs dramatically from the others
(the alpha channel especially, since it's often ignored in casual viewing)
or contains a distinguishable pattern.

## Technique: Compression Anomalies (JPEG-specific)

**Indicators**: `file`/`identify` reports unusual JPEG compression
parameters, or a re-saved/edited region visible via Error Level Analysis
(ELA) — a region edited after the original JPEG compression shows a
different error level than the rest of the image.

**Fast path**:
```bash
# Purpose: ELA highlights regions that were edited/pasted after initial compression
python3 -c "
from PIL import Image, ImageChops
im = Image.open('image.jpg').convert('RGB')
im.save('resaved.jpg', quality=95)
ela = ImageChops.difference(im, Image.open('resaved.jpg'))
ela.save('ela.png')
"
```

## Common Mistakes

- Jumping straight to `zsteg`/pixel analysis before checking metadata and
  appended data — the cheap checks solve a meaningful fraction of "stego"
  challenges outright.
- Not trying an empty passphrase with `steghide` — very commonly the
  actual case in beginner-tier challenges.
- Forgetting the alpha channel exists — many people only think in terms of
  R/G/B and miss data hidden there.
- Giving up after `zsteg` (default combos) without trying `-a` for the
  full combination sweep.
