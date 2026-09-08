# [TEMPLATE / EXAMPLE — NOT A REAL COMPETITION SOLVE] Crypto Warmup

> **This file is an illustrative template**, showing the required write-up
> format from `README.md`. It does not describe a real "What The Flag"
> challenge, and the "flag" below is a placeholder value, not a genuine
> competition flag. Use this as a format reference when writing up real
> solves.

**Category**: Cryptography
**Difficulty**: Easy (illustrative warmup-tier example)

## Challenge Description

*(Illustrative prompt text)* "We intercepted this message but our analyst
was in a rush and just XORed it with something simple. Can you recover
it?" — provided file: `message.txt` containing a single line of hex:

```
1e0a1f4b1e4b17421f5a5c1f421e0c1e421e5c481e0f1e421e0c1e
```

## Initial Assessment

`file message.txt` reported plain ASCII text (a hex string). Length is
even, character set is `[0-9a-f]` only → confirmed hex-encoded per
`cryptography/crypto-triage.md`'s Tree 1 branch.

## Initial Approach

Per `cryptography/crypto-triage.md`: hex-charset data with no digest-
length match → hex-decode first and re-triage the resulting bytes.
Challenge flavor text ("XORed it with something simple") strongly implies
single-byte XOR, so the plan was: hex-decode → single-byte XOR brute force
per `cryptography/xor.md`.

## Investigation

```bash
# Purpose: hex-decode the provided string to raw bytes
echo -n "1e0a1f4b1e4b17421f5a5c1f421e0c1e421e5c481e0f1e421e0c1e" | xxd -r -p > message.bin

# Purpose: confirm it's now non-printable binary, ruling out "already plaintext"
file message.bin
xxd message.bin

# Purpose: brute-force all 256 single-byte XOR keys, filter for printable output
python3 -c "
data = open('message.bin','rb').read()
for key in range(256):
    out = bytes(b ^ key for b in data)
    if all(32 <= c < 127 for c in out):
        print(key, out)
"
```

Output showed exactly one key (`0x7e`) producing fully printable ASCII:
`example{xor_is_never_secure_alone}`.

## Solution

The message was hex-encoded, then single-byte XOR'd with key `0x7e`.
Brute-forcing all 256 possible single-byte keys and filtering for
printable output isolated the correct key immediately — this is the
standard fast path documented in `cryptography/xor.md`.

## Flag Identification

- Format match: `example{...}` matches this illustrative competition's
  stated example flag format.
- Provenance: directly produced by the recorded command chain from the
  original `message.txt`.
- Reproducibility: re-running the exact command sequence against a clean
  copy of `message.txt` reproduces the identical output.
- Only one of the 256 candidate keys produced fully printable output,
  which is strong confirming evidence this is the correct key/plaintext,
  not a coincidental partial match.

## Reproduction

```bash
# Purpose: full reproduction from the original artifact to the flag, in order
echo -n "1e0a1f4b1e4b17421f5a5c1f421e0c1e421e5c481e0f1e421e0c1e" | xxd -r -p > message.bin
python3 -c "
data = open('message.bin','rb').read()
print(bytes(b ^ 0x7e for b in data))
"
```

## Key Insight

Character-set and length checks (per `cryptography/crypto-triage.md`)
identify the encoding layer almost for free — always decode before
assuming you're looking at ciphertext, and single-byte XOR brute force is
cheap enough to always try before any statistical analysis.

## Tools Used

- `file` (magic-byte identification)
- `xxd` (hex decode)
- Python 3 standard library (single-byte XOR brute force)

## Lessons Learned

*(Generalizable technique — already reflected in `cryptography/xor.md`
and `cryptography/crypto-triage.md`; no challenge-specific secret is
copied into those docs, only the reusable method.)* Hex-charset data
should always be decoded first, and single-byte XOR brute force with a
printable-output filter is a near-zero-cost first attempt whenever a
challenge hints at "simple" encryption.

## Difficulty/Efficiency Notes

Matched its "Easy/warmup" tier — total solve time under two minutes once
the standard hex-decode-then-XOR-bruteforce fast path was applied. No
faster path exists for this specific case; the bottleneck was recognizing
the hex charset, which the standard triage checklist already covers.

## Flag

`example{xor_is_never_secure_alone}` *(illustrative placeholder — not a
real competition flag)*
