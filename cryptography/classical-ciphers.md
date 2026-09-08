# Classical Ciphers

## Fastest First-Pass Checklist

```bash
# Purpose: brute-force all 26 Caesar/ROT shifts at once, eyeball for readable output
echo "$CIPHERTEXT" | python3 -c "
import sys
c = sys.stdin.read().strip()
for shift in range(26):
    out = ''.join(chr((ord(ch)-65-shift)%26+65) if ch.isupper() else
                   chr((ord(ch)-97-shift)%26+97) if ch.islower() else ch
                   for ch in c)
    print(shift, out)
"

# Purpose: Atbash is its own inverse — one command tests it fully
echo "$CIPHERTEXT" | tr 'A-Za-z' 'Z-Az-a'
```

## Technique: Caesar Cipher / ROT-N

**When to use it**: short shift-substitution where every letter maps to
another letter a fixed distance away.

**Fast path**: brute force all 26 shifts (script above) — cheap enough to
never bother identifying the shift analytically first.

**Indicators**: letter frequencies match natural English shifted by a
constant; `rot13` in particular is common enough to try as a single
one-off (`echo "$C" | tr 'A-Za-z' 'N-ZA-Mn-za-m'`).

## Technique: Atbash

**When to use it**: cipher reverses the alphabet (A↔Z, B↔Y, ...).

**Fast path**: `tr 'A-Za-z' 'Z-Az-a'` — self-inverse, one command solves
it outright, no brute force needed.

## Technique: Vigenère Cipher

**When to use it**: polyalphabetic substitution with a repeating key —
letter frequency looks flat/non-English until the key period is found.

**Fast path**:
```bash
# Purpose: estimate key length via Index of Coincidence / Kasiski examination
python3 -c "
from itertools import cycle
# Kasiski: find repeated n-grams and their distances, GCD of distances suggests key length
"
# In practice, use a dedicated tool for speed:
# CyberChef 'Vigenère Decode (Bruteforce)' operation, or:
python3 -m pip install --quiet cryptanalysis-lib 2>/dev/null  # or hand-roll IC-based key-length search
```
**If the key is guessable** (common word, hinted in challenge text), just
try decrypting directly with candidate keys rather than doing full
cryptanalysis:
```bash
# Purpose: direct Vigenère decrypt given a guessed/known key
python3 -c "
key = 'GUESSEDKEY'
ct = 'CIPHERTEXT'
from itertools import cycle
pt = ''.join(chr((ord(c)-65-(ord(k)-65))%26+65) if c.isalpha() else c
             for c,k in zip(ct, cycle(key)))
print(pt)
"
```

## Technique: General Substitution Cipher

**When to use it**: 1:1 letter mapping but not a simple shift (no
consistent offset across letters).

**Fast path**: frequency analysis — map ciphertext letter frequencies
against known English letter frequency (E, T, A, O, I, N ... most common)
and iteratively refine using common short words (`THE`, `AND`, single-
letter words `I`/`A`).
```bash
# Purpose: quick frequency count to start the substitution-solving process
echo "$CIPHERTEXT" | tr -cd 'A-Za-z' | tr 'a-z' 'A-Z' | fold -w1 | sort | uniq -c | sort -rn
```
For anything beyond a trivial length, use a solver
(`quipqiup.com`-style automated hill-climbing or a local equivalent) rather
than manual frequency-matching by hand — much faster on longer texts.

## Technique: Transposition Ciphers

**When to use it**: letter frequency matches natural language exactly (no
substitution happened) but the text reads as scrambled — indicates letters
were rearranged, not replaced.

**Fast path**: try common transposition patterns — reverse the string,
read in columns of common small widths (rail fence, columnar transposition
with a small guessed key length):
```bash
# Purpose: brute-force columnar transposition across small column counts
python3 -c "
ct = 'SCRAMBLEDTEXT'
for cols in range(2, 12):
    rows = -(-len(ct)//cols)
    grid = [ct[i:i+cols] for i in range(0, len(ct), cols)]
    out = ''.join(''.join(row[c] for row in grid if c < len(row)) for c in range(cols))
    print(cols, out)
"
```

## Common Mistakes

- Manually trying shifts one at a time instead of brute-forcing all 26 at
  once and eyeballing.
- Assuming a flat-frequency ciphertext must be modern crypto instead of
  checking transposition (frequency-preserving) first.
- Forgetting non-letter characters (spaces, punctuation) can be
  significant clues (word boundaries preserved = simpler cipher; all
  characters concatenated with no spaces = harder, likely needs a
  frequency-based solver rather than manual reading).
