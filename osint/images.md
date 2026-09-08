# Image OSINT

## Fastest First-Pass Checklist

```bash
# Purpose: metadata check first — GPS/camera/software info can solve the challenge outright
exiftool -a -u -g1 image.jpg

# Purpose: reverse image search — the core OSINT-image technique (manual, browser-based)
# Upload to: Google Images, Yandex Images (often best for faces/less-common images), TinEye

# Purpose: hash for exact-duplicate checking across sources you find
sha256sum image.jpg
```

## Technique: Metadata Extraction

**Fast path**: full workflow in `../digital-forensics/metadata.md` —
always run first. GPS EXIF tags, camera make/model, and software fields
are the highest-value fields for OSINT specifically.

## Technique: Reverse Image Search

**When to use it**: metadata is stripped/unhelpful, and the goal is to
find where else this image (or a very similar one) appears online.

**Fast path (manual, browser)**:
- **Google Images** — broadest index, good general coverage.
- **Yandex Images** — frequently outperforms Google specifically for
  faces and images originating from Russian/Eastern European sources;
  worth trying even for non-Russian content, it uses a different matching
  algorithm.
- **TinEye** — good for finding the *earliest* indexed instance of an
  exact/near-exact image, useful for establishing provenance/original
  source.

**Indicators**: matches reveal the image's origin (a specific business,
location, article, social media post) — read the surrounding context of
each match, not just the image match itself.

## Technique: Concept vs Reverse Search

If reverse image search returns nothing useful, describe the image's
*content* and search for that instead — a distinctive sign, storefront
name, license plate format, architectural style, or landmark visible in
the image can be searched as text even when the image itself isn't
indexed anywhere.

```
# Example (browser search):
"[distinctive visible text/sign]" location
[architectural style] + [region guessed from other clues]
```

## Technique: Object/Landmark Identification

**Fast path**: look for identifiable elements systematically:
- Signage/text (language, alphabet, business name — searchable directly).
- License plates (format/color scheme narrows country/region).
- Architecture style, road markings, utility pole style, vegetation
  (regional indicators — feed into `geolocation.md`).
- Clothing/uniforms (institutional affiliation).

**Next Step**: once region/location is narrowed, hand off to
`geolocation.md` for the pin-down workflow (satellite imagery
cross-referencing).

## Technique: Manipulation / Authenticity Check

**Fast path**:
```python
# Purpose: Error Level Analysis flags regions edited after the image's last save/compression
from PIL import Image, ImageChops
im = Image.open('image.jpg').convert('RGB')
im.save('resaved.jpg', quality=95)
diff = ImageChops.difference(im, Image.open('resaved.jpg'))
diff.save('ela.png')
```
**Indicators**: a region with a visibly different error level than the
rest of the image was likely edited/composited after the original capture
— relevant when the challenge implies the image was tampered with as part
of the puzzle.

## Common Mistakes

- Only trying Google reverse image search — Yandex frequently finds
  matches Google misses, especially for faces.
- Ignoring metadata because "it's probably stripped" — always check
  first, it's nearly free and sometimes the whole answer.
- Searching only the image itself instead of distinctive *content* within
  it (text, signage, landmarks) when direct reverse search fails.
