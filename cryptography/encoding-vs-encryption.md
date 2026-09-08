# Encoding vs Encryption vs Hashing vs Obfuscation vs Compression vs Serialization

Confusing these six categories is the single biggest time-waster in CTF
crypto. Distinguish them before choosing a technique.

## The Six Categories

| Category | Purpose | Reversible? | Needs a key? | Examples |
|---|---|---|---|---|
| **Encoding** | Represent binary data as text-safe characters | Always (trivially) | No | Base64, Base32, Base85, hex, URL-encoding, ASCII85 |
| **Encryption** | Confidentiality — hide meaning | Yes, with the right key | Yes | AES, RSA, XOR-with-secret-key, ChaCha20 |
| **Hashing** | Fixed-size fingerprint / integrity | No (one-way by design) | No | MD5, SHA-1/256/512, bcrypt |
| **Obfuscation** | Make analysis harder, not cryptographically secure | Usually, with effort | Sometimes | Custom substitution, control-flow flattening, string XOR "encryption" that's actually just obfuscation |
| **Compression** | Reduce size | Yes, algorithmically (no key) | No | gzip, zlib/DEFLATE, LZMA |
| **Serialization** | Structure data for storage/transmission | Yes, algorithmically | No | JSON, pickle, Protobuf, Java serialization |

## Why This Distinction Matters

- **Encoding is never "cracked"** — it's decoded. If you find yourself
  trying to "brute force" what turns out to be Base64, you've
  misidentified the category. Always try decoding *before* assuming
  encryption.
- **Hashing has no inverse.** If a challenge presents a fixed-length
  hex/base64 string and asks you to "decrypt" it, it's very likely a hash
  — the actual technique is either a lookup (rainbow table/online hash
  database), a dictionary/brute-force attack (if it's a password hash), or
  finding the pre-image some other way (e.g. it's actually derived from
  something guessable in the challenge, like a known plaintext dictionary
  word).
- **"Encryption" in a CTF is often actually just obfuscation** — a
  hand-rolled "cipher" that looks cryptographic but has an implementation
  flaw (reused XOR key, weak custom algorithm, static IV) making it
  trivially reversible without needing the "real" key. Don't assume
  textbook-crypto attack complexity applies; check for implementation
  weaknesses first.
- **Compression before encryption** is a very common real chain
  (`gzip -> AES`) — after decrypting, if the output is high-entropy
  garbage rather than readable text, try decompressing it before assuming
  decryption failed.

## Fast Identification Checklist

```bash
# Purpose: check character set constraints — narrows candidates immediately
echo -n "$DATA" | grep -qE '^[0-9a-fA-F]+$' && echo "hex-only charset"
echo -n "$DATA" | grep -qE '^[A-Za-z0-9+/=]+$' && echo "base64-charset-compatible"

# Purpose: length check — fixed lengths strongly suggest a hash, not encoding/encryption
echo -n "$DATA" | wc -c

# Purpose: try the cheapest possible transform first — decode, don't decrypt, until proven otherwise
echo "$DATA" | base64 -d 2>/dev/null | xxd | head
```

**Next Step**: route to `crypto-triage.md` for the full decision tree that
formalizes this identification process.

## Common Mistakes

- Attempting a brute-force/statistical attack on data that's actually just
  encoded (not encrypted) — always attempt decode first, it's nearly free.
- Treating a fixed-length hex string as "ciphertext" instead of
  recognizing common hash digest lengths (32 hex chars = MD5/128-bit, 40 =
  SHA-1, 64 = SHA-256).
- Assuming a "custom encryption" challenge requires real cryptanalysis when
  it's actually a broken/weak implementation (repeated XOR key, ECB mode
  visible block patterns, etc.) — check `xor.md` and `aes.md` for the
  specific implementation-flaw checklist before reaching for heavy tooling.
