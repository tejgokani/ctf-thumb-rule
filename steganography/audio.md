# Audio Steganography

## Fastest First-Pass Checklist (Stego Audio)

```bash
# Purpose: metadata first — cheapest possible check
exiftool audio.wav

# Purpose: strings/appended-data check
strings -n 8 audio.wav | tail -50
binwalk audio.wav

# Purpose: visual spectrogram — many CTF audio challenges hide text/images directly in the frequency domain
sox audio.wav -n spectrogram -o spectrogram.png
```

## Technique: Spectrogram Analysis

**When to use it**: always, early — a large fraction of "audio stego" CTF
challenges hide an image, QR code, or text directly as a visual pattern in
the frequency spectrum, visible only via spectrogram, not by listening.

**Fast path**:
```bash
# Purpose: generate a spectrogram image, the single highest-value audio-stego check
sox audio.wav -n spectrogram -o spectrogram.png

# Purpose: Audacity (GUI) equivalent — Analyze > Plot Spectrum, or switch track view to Spectrogram
# for interactive exploration, useful when sox's static image needs different zoom/scale
```
**Indicators**: readable text, a flag string, a QR code, or a recognizable
shape rendered in the spectrogram image.

**Next Step**: if a QR code appears, scan it directly (`zbarimg
spectrogram.png`); if text, read it directly off the image.

## Technique: Frequency Analysis (DTMF, Morse, Tones)

**Fast path**:
```bash
# Purpose: decode DTMF (touch-tone) sequences if present
python3 -m pip install --quiet multimon-ng 2>/dev/null
multimon-ng -t wav -a DTMF audio.wav

# Purpose: decode Morse code from tone on/off patterns (visual inspection of spectrogram/waveform often fastest)
sox audio.wav -n spectrogram -o morse_check.png -X 50   # tighter time resolution helps distinguish dots/dashes
```
**Indicators**: regular short/long tone bursts at a consistent frequency —
Morse code candidate; touch-tone dual-frequency pairs — DTMF candidate.

## Technique: LSB in Audio Samples

**When to use it**: WAV (uncompressed PCM) file, spectrogram is clean, and
appended-data/metadata checks found nothing.

**Fast path**:
```python
# Purpose: extract the least significant bit of every PCM sample — classic audio LSB stego
import wave
w = wave.open('audio.wav', 'rb')
frames = w.readframes(w.getnframes())
bits = [b & 1 for b in frames]
# Group bits into bytes and inspect for printable text
byts = bytes(int(''.join(map(str, bits[i:i+8])), 2) for i in range(0, len(bits) - 8, 8))
print(byts[:200])
```
**Indicators**: extracted byte stream contains printable text or a
recognizable file header.

## Technique: Reversed / Altered Channels

**Fast path**:
```bash
# Purpose: reverse the audio — sometimes a hidden message is only intelligible played backward
sox audio.wav reversed.wav reverse

# Purpose: split stereo channels — data or a message might exist in only one channel, or differ between channels
sox audio.wav left.wav remix 1
sox audio.wav right.wav remix 2

# Purpose: diff the two channels — if normally identical (mono duplicated to stereo), any difference is suspicious
python3 -c "
import wave
l = wave.open('left.wav','rb').readframes(-1)
r = wave.open('right.wav','rb').readframes(-1)
print(l == r)
"
```
**Indicators**: reversed audio contains intelligible speech/morse; left
and right channels differ when they "should" be identical — the diff
itself may encode data (e.g. difference = 1 bit per sample = LSB channel
stego).

## Common Mistakes

- Only listening to the audio and never generating a spectrogram — most
  audio-stego solves are invisible to the ear and only visible spectrally.
- Using a lossy format's raw bytes for LSB extraction — LSB techniques
  require uncompressed PCM (WAV); MP3/OGG compression destroys LSB data,
  so if the challenge provides a lossy format, the payload is more likely
  metadata, appended data, or frequency-domain rather than LSB.
- Not checking both channels independently in stereo files.
