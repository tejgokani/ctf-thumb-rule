# Python Quick Reference (Custom Analysis / Automation)

Python is the default choice for anything iterative, bulk, or requiring
custom logic that no single CLI tool covers — bruteforce loops, custom
decoders, binary parsing. Prefer standard library where possible; reach
for `pwntools`/`pycryptodome`/`requests` for their specific domains.

```python
# Purpose: read raw bytes for any binary/file-format work
data = open("artifact.bin", "rb").read()

# Purpose: common encodings, all standard library, no install needed
import base64, binascii
base64.b64decode(s)
binascii.unhexlify(s)          # hex -> bytes
binascii.hexlify(b)            # bytes -> hex

# Purpose: single-byte XOR bruteforce skeleton (see cryptography/xor.md for full version)
for key in range(256):
    out = bytes(b ^ key for b in data)

# Purpose: pwntools — the standard toolkit for interacting with CTF network services and binaries
from pwn import *
io = remote('host', 1337)
io.sendline(b'payload')
print(io.recvline())

# Purpose: pycryptodome — AES/RSA primitives without hand-rolling crypto math
from Crypto.Cipher import AES
from Crypto.Util.number import long_to_bytes, bytes_to_long

# Purpose: requests — scripted web interaction beyond what curl one-liners comfortably express
import requests
s = requests.Session()
r = s.post("https://target.example/login", json={"user": "a", "pass": "b"})

# Purpose: struct — parse fixed binary layouts (file formats, packet headers) precisely
import struct
magic, length = struct.unpack(">4sI", data[:8])
```

## When to Reach for a Script vs a CLI Tool

Write a script when: the task is iterative (bruteforce, batch decode), needs
custom parsing logic (a nonstandard binary format), or chains multiple
transforms that would be awkward as a shell pipeline. Use a CLI tool
directly when a well-known single-purpose tool already does exactly the
job (`base64 -d`, `xxd`, `openssl`) — don't reimplement what already
exists as a one-liner.

## Common Mistakes

- Reimplementing base64/hex decode by hand instead of using the standard
  library's `base64`/`binascii` modules.
- Not padding base64 strings correctly before decoding (`base64.b64decode`
  fails on incorrect padding) — pad explicitly:
```python
# Purpose: correct padding before decoding a base64 string that may be missing '=' padding
def b64pad(s): return s + "=" * (-len(s) % 4)
```
- Writing a bruteforce loop that doesn't short-circuit on a match — always
  add an early-exit condition once a flag-shaped result is found, to avoid
  wasting time on remaining iterations.
