# AES & Symmetric Cipher Attacks

## Fastest First-Pass Checklist

```bash
# Purpose: check for ECB mode's visible weakness — repeated ciphertext blocks
python3 -c "
data = bytes.fromhex('$CIPHERTEXT_HEX')
blocks = [data[i:i+16] for i in range(0, len(data), 16)]
print('repeated blocks:', len(blocks) != len(set(blocks)))
"

# Purpose: check ciphertext length is a multiple of 16 (block cipher indicator, not stream)
python3 -c "print(len(bytes.fromhex('$CIPHERTEXT_HEX')) % 16)"
```

## Technique: ECB Mode Block Repetition

**When to use it**: AES-ECB is in play — identical 16-byte plaintext
blocks always produce identical ciphertext blocks, which leaks structure.

**Fast path**: as above — scan for repeated 16-byte ciphertext blocks. On
images specifically, this is visually dramatic (the classic "ECB penguin"
— encrypting an image in ECB mode leaves recognizable shapes visible in
the ciphertext because repeated color regions produce repeated blocks).

**Indicators**: repeated blocks found → confirms ECB, and depending on
challenge setup may allow byte-at-a-time ECB decryption (if you can submit
chosen plaintext that gets concatenated with a secret and encrypted) —
classic "ECB oracle" challenge pattern.

**Next Step (ECB oracle / byte-at-a-time decryption)**:
```python
# Purpose: recover an unknown secret appended before encryption, one byte at a time, via block alignment
# Requires an oracle: encrypt(attacker_controlled_prefix + secret) -> ciphertext
def oracle(data): ...  # calls the challenge's encryption endpoint
known = b""
block_size = 16
for i in range(len(secret_estimate)):
    pad = b"A" * (block_size - 1 - (i % block_size))
    target_block = oracle(pad)[: (i//block_size+1)*block_size]
    for guess in range(256):
        test = pad + known + bytes([guess])
        if oracle(test)[: (i//block_size+1)*block_size] == target_block:
            known += bytes([guess])
            break
```

## Technique: CBC Mode — IV Reuse / Predictable IV

**When to use it**: IV is reused across multiple messages, or is
predictable/all-zero.

**Indicators**: two ciphertexts under the same key share the same first
block despite different plaintext prefixes → IV reuse; XOR-ing the first
blocks of two such ciphertexts XORs the corresponding plaintexts (crib
dragging becomes viable if one plaintext or part of it is known/guessable).

## Technique: CBC Padding Oracle

**When to use it**: the server distinguishes (via error message, timing,
or status code) between "padding was valid" and "padding was invalid" when
decrypting attacker-modified ciphertext.

**Fast path**:
```bash
# Purpose: padbuster automates a full CBC padding-oracle attack given a working oracle endpoint
padbuster https://target.example/decrypt <ciphertext_base64> 16 -encoding 0
```
**Indicators**: a distinguishable error (different response body/status
code/timing) when a tampered ciphertext's PKCS#7 padding fails to validate
vs when it validates but the underlying plaintext is garbage.

## Technique: CTR / Stream Cipher — Nonce Reuse

**When to use it**: same key+nonce used to encrypt two different
plaintexts under CTR mode (or any stream cipher) — reduces to a two-time-
pad situation, effectively the same weakness as `xor.md`'s repeated-key
case.

**Fast path**: XOR the two ciphertexts together — this cancels the
keystream and yields the XOR of the two plaintexts, then apply
crib-dragging/frequency analysis exactly as in `xor.md`.

## Technique: Key/IV Recovery from Weak Derivation

**Indicators**: key derived from a short/guessable seed (a password run
through a fast hash with no salt, a timestamp used as a key, a short
numeric PIN as the key directly).

**Fast path**: brute-force the small keyspace directly rather than
attacking AES itself:
```python
# Purpose: brute-force a small keyspace (e.g. a 4-digit PIN used as a key/seed) directly
from Crypto.Cipher import AES
for pin in range(10000):
    key = str(pin).zfill(4).encode().ljust(16, b'\0')
    try:
        pt = AES.new(key, AES.MODE_ECB).decrypt(ciphertext)
        if b"flag" in pt or b"WTF{" in pt:
            print(pin, pt)
    except Exception:
        pass
```

## Common Mistakes

- Assuming "AES" means the attack must be cryptanalytic — in CTF
  practice it's almost always a mode-of-operation or key-management flaw,
  not breaking the AES algorithm itself.
- Not checking block alignment/repeated blocks before assuming CBC/CTR —
  ECB is the cheapest thing to rule in or out and changes the entire
  attack strategy.
- Forgetting padding validation — after any decrypt attempt, check that
  PKCS#7 padding at the end is actually well-formed; garbage padding
  usually means wrong key/IV/mode assumption, not "decryption partially
  worked."
