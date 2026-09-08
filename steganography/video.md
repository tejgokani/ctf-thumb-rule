# Video Steganography

## Fastest First-Pass Checklist (Stego Video)

```bash
# Purpose: metadata first
exiftool video.mp4

# Purpose: appended-data/embedded-file check
binwalk video.mp4

# Purpose: extract the audio track — often analyzed separately per audio.md
ffmpeg -i video.mp4 -vn -acodec copy extracted_audio.wav

# Purpose: extract frames for per-frame image analysis
ffmpeg -i video.mp4 -vf fps=1 frames/frame_%04d.png
```

## Technique: Metadata

**Fast path**: run the standard `../digital-forensics/metadata.md`
workflow — video containers (MP4/MKV/AVI) carry rich metadata including
creation tool, GPS (for phone-recorded video), and custom tags.

## Technique: Audio Track Extraction

**When to use it**: always — video files carry an audio stream that's
easy to overlook if focus stays purely visual.

**Fast path**:
```bash
# Purpose: pull the audio stream out as its own file for the full audio.md workflow (spectrogram etc.)
ffmpeg -i video.mp4 -vn -acodec copy extracted_audio.wav
```
**Next Step**: treat `extracted_audio.wav` as its own artifact — run
`audio.md`'s spectrogram/LSB/frequency techniques on it directly.

## Technique: Frame Extraction & Per-Frame Analysis

**When to use it**: a payload/QR code/flag flashes briefly across one or
few frames, too fast to notice on normal playback, or a payload is hidden
via image-stego techniques within a specific frame.

**Fast path**:
```bash
# Purpose: extract every single frame for exhaustive review (use for short clips)
ffmpeg -i video.mp4 frames/frame_%05d.png

# Purpose: extract at low fps for a quick skim of long videos before going frame-by-frame
ffmpeg -i video.mp4 -vf fps=1 frames_sparse/frame_%04d.png

# Purpose: batch-scan every extracted frame for QR codes automatically
for f in frames/*.png; do zbarimg -q "$f" && echo "  -> $f"; done

# Purpose: batch-run exiftool/strings across every frame in case metadata/stego is per-frame
for f in frames/*.png; do exiftool "$f" | grep -i comment; done
```
**Indicators**: `zbarimg` decodes a QR code from one specific frame; a
particular frame's file size or `zsteg` output stands out from its
neighbors (only one frame in the sequence was stego-modified).

## Technique: Frame-Diffing (Motion/Anomaly Detection)

**When to use it**: payload is hidden as a subtle change between
consecutive frames (not visible in any single frame alone).

**Fast path**:
```python
# Purpose: diff consecutive frames — a payload embedded as per-frame deltas becomes visible this way
from PIL import Image, ImageChops
import glob

frames = sorted(glob.glob('frames/*.png'))
for i in range(len(frames) - 1):
    a = Image.open(frames[i])
    b = Image.open(frames[i+1])
    diff = ImageChops.difference(a, b)
    if diff.getbbox():  # non-empty diff = actual change occurred
        diff.save(f'diff_{i}.png')
```
**Indicators**: most frame-diffs are near-blank (static background) except
for a handful showing a deliberate pattern/text — those are the frames of
interest.

## Common Mistakes

- Forgetting to extract and separately analyze the audio track — easy to
  focus entirely on visual frames and miss an audio-stego payload.
- Extracting every frame of a long video at full resolution and
  eyeballing all of them manually instead of scripting a QR/anomaly scan
  first to narrow down candidates.
- Not checking playback speed/frame timing — a message that requires
  frame-by-frame stepping (not visible at normal playback speed) is a
  common and easy-to-miss pattern.
