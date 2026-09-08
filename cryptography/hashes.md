# Hash Identification & Attacks

## Fastest First-Pass Checklist

```bash
# Purpose: length-based identification is fast and usually sufficient
echo -n "$HASH" | wc -c
# 32 hex = MD5 | 40 hex = SHA-1 | 64 hex = SHA-256 | 128 hex = SHA-512
# 24-31 base64 chars w/ $ prefix pattern = bcrypt/other salted format, inspect prefix

# Purpose: dedicated hash-identification tool for anything non-obvious
hashid "$HASH"
name-that-hash -t "$HASH"

# Purpose: cheapest possible attack — check if it's already a known/common value
echo -n "password" | md5sum   # compare manually, or use an online hash lookup for common values
```

## Technique: Length & Format Identification

**Fast path**: as above — digest length narrows candidates immediately.
Prefix patterns matter too: `$2a$`/`$2b$` = bcrypt, `$1$` = MD5-crypt,
`$6$` = SHA-512-crypt, `$argon2` = Argon2.

**Next Step**: once algorithm is identified, decide attack strategy based
on whether it's salted (bcrypt/Argon2 always are) — salted hashes can't
use precomputed rainbow tables, only dictionary/brute-force per-hash.

## Technique: Lookup / Rainbow Table (Unsalted Common Hashes)

**When to use it**: unsalted MD5/SHA-1/SHA-256 of a common/weak value
(likely password, common word, or short string).

**Fast path**:
```bash
# Purpose: check a local common-hash lookup db if available (e.g. hashcat's --show against a wordlist)
hashcat -m 0 -a 0 hash.txt /usr/share/wordlists/rockyou.txt --show
```
**Indicators**: cracks instantly against `rockyou.txt` → value was a common
password/word, not a random secret — this is the expected case for most
CTF "crack this hash" challenges.

## Technique: Dictionary / Brute-Force Attack

**Fast path**:
```bash
# Purpose: dictionary attack — try rockyou.txt first, it covers most CTF-chosen weak values
hashcat -m 0 -a 0 hash.txt /usr/share/wordlists/rockyou.txt

# Purpose: identify the correct hashcat mode number for the algorithm first
hashcat --help | grep -i sha256    # -> confirm mode number, e.g. 1400 for raw SHA-256

# Purpose: brute-force a short/known-format value (e.g. "flag is a 4-digit PIN hashed")
hashcat -m 0 -a 3 hash.txt ?d?d?d?d
```
**Common Mistakes**: picking the wrong `-m` mode number — always verify
via `hashcat --help | grep -i <algo>` rather than guessing.

## Technique: Salted Hash Cracking

**When to use it**: bcrypt/Argon2/scrypt or a salt is visibly
concatenated with the hash.

**Fast path**:
```bash
# Purpose: bcrypt mode in hashcat (slow by design — cost factor matters for feasibility)
hashcat -m 3200 -a 0 hash.txt /usr/share/wordlists/rockyou.txt

# Purpose: john the ripper as an alternative, auto-detects many salted formats
john --wordlist=/usr/share/wordlists/rockyou.txt hash.txt
```
**Indicators**: bcrypt cost factor (the number after `$2b$`) directly
affects crack time — a high cost factor in a CTF context is itself a
signal the intended solve is NOT brute force (look for another path: a
logic flaw, a leaked plaintext elsewhere, a weak/short guessable space).

## Technique: Hash Length Extension Attacks

**When to use it**: app computes `hash(secret + user_input)` with a
Merkle-Damgård hash (MD5, SHA-1, SHA-256) and you can control
`user_input` and observe the resulting hash, without knowing `secret`.

**Fast path**:
```bash
# Purpose: hashpump automates length-extension attacks against vulnerable MD5/SHA1/SHA256 constructions
hashpump -s <known_hash> -d <known_data> -a <data_to_append> -k <secret_length_guess_or_range>
```
**Indicators**: the app's signature scheme is literally `hash(secret ||
data)` (not HMAC) — this is the textbook vulnerable construction.

## Common Mistakes

- Trying to "decrypt" a hash — hashes are one-way; the only paths are
  lookup, dictionary/brute-force, or exploiting a construction flaw
  (length extension, weak salt).
- Not checking digest length before choosing a hashcat mode — wastes time
  running the wrong algorithm's attack.
- Ignoring an unusually high cost factor (bcrypt/Argon2) as a signal that
  brute force isn't the intended path at all.
