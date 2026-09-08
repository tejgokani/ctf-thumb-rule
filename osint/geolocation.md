# Geolocation

## Fastest First-Pass Checklist

```bash
# Purpose: GPS EXIF is the fastest possible geolocation win — always check first
exiftool -gpslatitude -gpslongitude -c "%.6f" image.jpg

# Purpose: if coordinates found, format directly as a maps link
python3 -c "print('https://www.google.com/maps?q=<lat>,<lon>')"
```

## Technique: Direct GPS Metadata

**When to use it**: first move, always — full detail in
`../digital-forensics/metadata.md`'s GPS section. If present, this solves
geolocation instantly with no visual analysis needed.

## Technique: Visual Geolocation (No Metadata)

**When to use it**: metadata is stripped (very common — most social
platforms strip GPS EXIF on upload) and location must be inferred from
image content.

**Systematic checklist of visual clues**, roughly in order of
narrowing power:
1. **Language/alphabet on signage** — narrows to a country/region
   immediately.
2. **Vehicle characteristics** — license plate format/color, which side of
   the road traffic drives on, vehicle makes common to a region.
3. **Road markings and infrastructure** — lane marking style, road sign
   design standards (these differ systematically by country/region and
   are well-documented).
4. **Architecture style** — roofing material, building shape conventions,
   window style.
5. **Vegetation/terrain** — climate zone indicators.
6. **Utility infrastructure** — power line pole style/voltage
   configuration, differs by region.
7. **Shadows** — sun angle/shadow direction + time-of-day estimate can
   narrow latitude/longitude band if the photo timestamp is known
   (metadata or challenge-provided).

## Technique: Satellite Imagery Cross-Referencing

**When to use it**: a region/city has been narrowed via visual clues, and
you need to pin down the exact location.

**Fast path**: use Google Maps/Earth or an equivalent satellite view,
search the narrowed region for structures matching the image's distinctive
features (unusual building shape, specific intersection layout,
recognizable landmark silhouette). Cross-reference multiple distinctive
features simultaneously (a building shape AND a nearby road curve AND
matching vegetation) rather than relying on one alone.

**Indicators**: an exact structural match (building outline, road
intersection angle) between the target image and satellite/street view
imagery — this is the confirming evidence, not just "looks similar."

## Technique: Shadow / Sun-Position Analysis

**When to use it**: precise time-of-day is known/derivable (from
metadata timestamp or challenge context) and coarse geolocation is needed
without visual landmarks.

**Fast path**: use a sun-position calculator (SunCalc or equivalent) with
the known/estimated capture time; the shadow angle/length in the image
constrains the possible latitude band.

## Common Mistakes

- Skipping the metadata check and going straight to visual analysis —
  costs significant time when GPS EXIF was present all along.
- Relying on a single visual clue (e.g. "these look like European
  buildings") without cross-referencing multiple independent clues before
  committing to a specific location.
- Not accounting for GPS EXIF being present but describing where the
  *photo was uploaded from* or a *default device location* rather than
  where the photo was actually *taken* — sanity-check coordinates against
  the image content itself.
