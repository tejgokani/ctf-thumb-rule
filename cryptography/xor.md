# XOR Analysis

## Fastest First-Pass Checklist

```bash
# Purpose: try every single-byte key (0-255), keep outputs containing printable/flag-like text
python3 -c "
data = bytes.fromhex('$CIPHERTEXT_HEX')
for key in range(256):
    out = bytes(b ^ key for b in data)
    if all(32 <= c < 127 or c in (9,10,13) for c in out):
        print(key, out)
"
```

## Technique: Single-Byte XOR

**When to use it**: ciphertext is suspected XOR-encoded with a 1-byte key
— common as an easy warmup, or as one layer of a multi-step challenge.

**Fast path**: brute force all 256 keys (script above), filter for
printable output or a flag-format match. Essentially free — always try
this before anything more sophisticated.

## Technique: Repeating-Key XOR (Vigenère-style)

**When to use it**: key is longer than 1 byte and repeats across the
ciphertext — indicated by a periodic pattern in byte-level structure.

**Fast path**:
```python
# Purpose: estimate key length via Hamming-distance normalization (classic Cryptopals-style approach)
def hamming(a, b):
    return sum(bin(x ^ y).count('1') for x, y in zip(a, b))

data = open('ciphertext.bin', 'rb').read()
best = []
for keysize in range(2, 40):
    chunks = [data[i:i+keysize] for i in range(0, len(data), keysize)][:4]
    if len(chunks) < 4: continue
    dist = sum(hamming(chunks[i], chunks[i+1]) for i in range(3)) / 3 / keysize
    best.append((dist, keysize))
best.sort()
print(best[:5])   # smallest normalized distance = most likely key length
```
Once key length is estimated, split ciphertext into that many
interleaved single-byte-XOR streams and solve each independently via
frequency analysis (each stream is now a single-byte-XOR problem, solvable
with the checklist above).

## Technique: Known-Plaintext / Crib Dragging

**When to use it**: you know or can guess part of the plaintext (a
expected flag prefix like `flag{`, a common header, a repeated word).

**Fast path**:
```python
# Purpose: XOR a guessed crib against the ciphertext at every position, reveals key fragment where crib is correct
ciphertext = bytes.fromhex('$CIPHERTEXT_HEX')
crib = b"flag{"
for i in range(len(ciphertext) - len(crib)):
    key_fragment = bytes(c ^ p for c, p in zip(ciphertext[i:i+len(crib)], crib))
    print(i, key_fragment)
```
**Indicators**: at the correct offset, the recovered key fragment looks
like a plausible readable key (repeats a short word) rather than random
bytes — confirms both key and alignment.

## Technique: Two-Time Pad (Same Key/Keystream, Multiple Messages)

**When to use it**: two or more ciphertexts were XORed with the *same*
key/keystream (classic mistake in both raw XOR and, e.g., CTR-mode nonce
reuse — see `aes.md`).

**Fast path**:
```python
# Purpose: XOR two ciphertexts together — cancels the shared key, leaves XOR of the two plaintexts
c1 = bytes.fromhex('...')
c2 = bytes.fromhex('...')
xored = bytes(a ^ b for a, b in zip(c1, c2))
# xored == plaintext1 XOR plaintext2 — crib-drag known words (spaces XOR letters produce case-flips, a useful tell)
```
**Indicators**: XOR-ing two ciphertexts and crib-dragging a common word
(like `" the "`) against the result quickly starts revealing readable
fragments of both original plaintexts at once.

## Common Mistakes

- Assuming single-byte XOR failed just because the printable-filter check
  was too strict — loosen it (allow a few non-printable bytes) if a
  reasonable-looking-but-not-perfect result appears near a promising key.
- Not trying known-plaintext/crib-dragging when the flag format is known —
  `flag{` or the competition's actual prefix is often the single fastest
  way to break repeating-key XOR without any statistical analysis at all.
- Forgetting that a "custom encryption" challenge might just be XOR with
  extra steps (e.g. XOR then base64) — decode the outer encoding first
  (`encoding-vs-encryption.md`), then apply XOR techniques to the result.
