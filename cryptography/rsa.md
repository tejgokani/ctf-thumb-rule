# RSA Attacks

## Fastest First-Pass Checklist

```bash
# Purpose: given n, check if it's small enough to factor instantly online/locally
python3 -c "
n = 0x...  # paste n here
import sympy
print(sympy.factorint(n))
"

# Purpose: RsaCtfTool automates identification of the applicable attack given whatever params you have
python3 RsaCtfTool.py --publickey key.pub --uncipher ciphertext.enc
```

## Technique: Factor a Small Modulus

**When to use it**: `n` is small enough to factor directly (a strong CTF
indicator: if the challenge gives you `n` as a specific number rather than
a 2048-bit key file, it's almost always factorable).

**Fast path**:
```bash
# Purpose: sympy factors moduli up to a few hundred bits quickly; use factordb for anything larger/already-known
python3 -c "import sympy; print(sympy.factorint(N))"
# or check http://factordb.com/index.php?query=<N> — many CTF-chosen n values are already in its database
```
**Next Step**: once `p, q` are known, compute `phi = (p-1)*(q-1)`, `d =
modinv(e, phi)`, then `m = pow(c, d, n)`.
```python
# Purpose: full RSA decryption once p and q are recovered
from sympy import mod_inverse
p, q, e, c = ..., ..., ..., ...
n = p*q
phi = (p-1)*(q-1)
d = mod_inverse(e, phi)
m = pow(c, d, n)
print(m.to_bytes((m.bit_length()+7)//8, 'big'))
```

## Technique: Common Modulus Attack

**When to use it**: same `n`, same message encrypted under two different
public exponents `e1`, `e2` (with `gcd(e1, e2) == 1`).

**Fast path**:
```python
# Purpose: recover plaintext without factoring n, using extended Euclid on e1,e2
from Crypto.Util.number import long_to_bytes
def egcd(a, b):
    if b == 0: return (a, 1, 0)
    g, x, y = egcd(b, a % b)
    return (g, y, x - (a//b)*y)
g, a, b = egcd(e1, e2)
m = (pow(c1, a, n) * pow(c2, b, n)) % n
print(long_to_bytes(m))
```

## Technique: Low Public Exponent Attack (e=3, no padding)

**When to use it**: `e` is small (commonly 3) and the message is short
enough that `m^e < n` (no modular wraparound occurred).

**Fast path**:
```python
# Purpose: if m^e never exceeded n, plain integer cube root recovers m directly
import gmpy2
m, exact = gmpy2.iroot(c, e)
```

## Technique: Wiener's Attack (Small Private Exponent d)

**When to use it**: `e` is large and `d` is suspected to be small
(`d < n^0.25` roughly) — private exponent was chosen too small for
"performance."

**Fast path**:
```bash
# Purpose: RsaCtfTool has Wiener's attack built in, avoids hand-implementing continued fractions
python3 RsaCtfTool.py -n $N -e $E --uncipher $C --attack wiener
```

## Technique: Fermat Factorization (p, q Close Together)

**When to use it**: `p` and `q` were generated too close to each other
(`|p - q|` small).

**Fast path**:
```python
# Purpose: Fermat's method — fast when p,q are close, converges quickly in that case
import gmpy2
a = gmpy2.isqrt(n) + 1
while True:
    b2 = a*a - n
    b = gmpy2.isqrt(b2)
    if b*b == b2:
        p, q = a+b, a-b
        break
    a += 1
```

## Technique: Multiple Ciphertexts, Same Message, Different Moduli (Hastad's Broadcast)

**When to use it**: same plaintext encrypted with small `e` (e.g. 3) under
several different `n` values (a "broadcast" scenario).

**Fast path**:
```python
# Purpose: CRT-combine ciphertexts across moduli, then take integer e-th root
from sympy.ntheory.modular import crt
import gmpy2
combined, _ = crt([n1, n2, n3], [c1, c2, c3])
m, exact = gmpy2.iroot(combined, 3)
```

## Common Mistakes

- Trying to attack RSA as if it were unbreakable "real" cryptography when
  a CTF-given `n` is small enough to just factor — always try factoring
  first, it's the cheapest possible check.
- Not checking factordb.com before writing custom factoring code — many
  CTF-chosen composite numbers are already in its database from prior
  competitions/challenges.
- Forgetting to check `gcd(n1, n2)` across multiple provided public keys
  in a challenge set — shared prime factors across "different" keys is a
  classic weak key-generation bug (`gcd` reveals a shared prime instantly).
- Assuming padding (PKCS#1) is absent without checking — low-exponent
  attacks specifically require unpadded/naive RSA to work.
