# Crypto Triage — The Decision Tree

This is the master routing document for any "here's some weird data,
figure out what it is" crypto challenge. Follow it top to bottom; each
branch names the next doc to jump to once identified.

## Fastest First-Pass Checklist

```bash
# Purpose: check charset — hex-only?
echo -n "$DATA" | grep -qE '^[0-9a-fA-F]+$' && echo "-> hex"

# Purpose: check charset — base64-compatible (incl. padding)?
echo -n "$DATA" | grep -qE '^[A-Za-z0-9+/]+={0,2}$' && echo "-> base64 candidate"

# Purpose: length — common hash digest lengths
echo -n "$DATA" | wc -c   # 32=MD5(hex) 40=SHA1(hex) 64=SHA256(hex) 128=SHA512(hex)

# Purpose: try the identify-then-decode tool if available (handles many encodings/ciphers heuristically)
echo "$DATA" | python3 -c "import sys; print(sys.stdin.read())" | ciphey  # or `cyberchef` magic wand offline equivalent
```

## The Decision Tree

```
Unknown data string
│
├─ Charset is [0-9a-fA-F] only, even length
│     └─> Hex-encoded.
│         ├─ Length matches a known hash digest (32/40/64/128 hex chars)?
│         │     YES → likely a HASH → hashes.md (lookup/crack, not decrypt)
│         │     NO  → hex-decode: `echo $DATA | xxd -r -p` → re-triage the decoded bytes
│         └─ (always try hex-decode regardless — costs nothing)
│
├─ Charset is [A-Za-z0-9+/=] (or URL-safe [A-Za-z0-9_-=])
│     └─> Base64/Base64URL candidate.
│         └─> base64 -d and inspect result:
│               ├─ Decodes to more text/another encoding → repeat triage (multi-layer encoding is common)
│               ├─ Decodes to binary with a recognizable magic byte → route to file-analysis.md
│               ├─ Decodes to high-entropy garbage → likely genuinely encrypted/compressed binary
│               └─ Fails to decode cleanly → check for Base32/Base58/Base85/Ascii85 instead
│
├─ Repeated short-period byte pattern visible in raw bytes (XOR indicator)
│     └─> xor.md — recover key via known-plaintext or frequency analysis
│
├─ Small RSA parameters given (small n, small e, or multiple moduli/messages)
│     └─> rsa.md — factor n, common-modulus/low-exponent/Wiener's attacks
│
├─ Ciphertext resembles readable-language structure after a 1:1 character
│    mapping (letter frequency looks natural but shifted/substituted)
│     └─> classical-ciphers.md — Caesar/ROT/Atbash/Vigenère/substitution,
│          use frequency analysis
│
├─ Fixed-length string, high entropy, no charset pattern suggesting text
│     └─> Likely a hash or a symmetric-cipher output.
│         ├─ Digest-length match → hashes.md
│         └─> No digest-length match → aes.md / general symmetric crypto
│              triage (check for ECB block-repetition patterns first — cheap
│              and often decisive)
│
└─ Structure suggests a known cipher family from context (challenge says
     "RSA", provides n/e/c; says "AES", provides IV+ciphertext; etc.)
       └─> Skip charset analysis, go directly to that cipher's doc — the
            challenge has already told you the family; identify the *attack*,
            not the *cipher*.
```

## Common CTF Crypto Mistakes (cross-cutting)

- Attacking data as ciphertext before confirming it isn't just encoded —
  always attempt a decode pass first (see `encoding-vs-encryption.md`).
- Assuming "AES" means unbreakable — CTF AES challenges are almost always
  about a mode/implementation flaw (ECB, IV reuse, padding oracle, key
  reuse), not brute-forcing the algorithm itself.
- Not checking for multi-layer encoding (base64-of-hex-of-base64) — when a
  decode produces more printable text that still looks encoded, decode
  again.
- Ignoring the challenge's own naming/hints ("this is definitely XOR") in
  favor of running generic multi-tool identification — if the challenge
  tells you the cipher family, trust it and go straight to the attack.
- Treating a classical-cipher-looking string as "too easy to be right" and
  overthinking it — frequency analysis first, always, before assuming a
  more exotic cipher.
