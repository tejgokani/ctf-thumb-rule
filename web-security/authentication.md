# Authentication Testing

## Fastest First-Pass Checklist

```bash
# Purpose: check for default/weak credentials before anything more complex
curl -s -X POST https://target.example/login -d 'user=admin&pass=admin'

# Purpose: inspect any issued JWT structurally without a library
echo '<jwt>' | cut -d. -f1 | base64 -d 2>/dev/null; echo
echo '<jwt>' | cut -d. -f2 | base64 -d 2>/dev/null; echo

# Purpose: check session cookie attributes (HttpOnly, Secure, SameSite) for weaknesses
curl -sI https://target.example/login | grep -i set-cookie
```

## Technique: Weak / Default Credentials

**Fast path**: try `admin:admin`, `admin:password`, blank password, the
username reflected as the password, and any credentials hinted in the
challenge description or discovered via recon (source comments, config
files).

**Indicators**: a 200/redirect instead of 401/403 on a guessed credential
pair.

## Technique: JWT Analysis

**When to use it**: the app issues a JSON Web Token for session/auth.

**Fast path**:
```bash
# Purpose: decode header + payload (no verification, just reading claims)
python3 -c "
import base64, json, sys
token = sys.argv[1]
h, p, s = token.split('.')
def pad(x): return x + '=' * (-len(x) % 4)
print(json.dumps(json.loads(base64.urlsafe_b64decode(pad(h))), indent=2))
print(json.dumps(json.loads(base64.urlsafe_b64decode(pad(p))), indent=2))
" "$JWT"
```
**Indicators to check**:
- `"alg": "none"` accepted by the server → forge an unsigned token with
  modified claims (`role: admin`), drop the signature entirely.
- `alg: HS256` but the app also supports `RS256` with a known public key →
  algorithm confusion attack (sign with HS256 using the public key as the
  HMAC secret).
- Weak/guessable HMAC secret → crack offline:
```bash
# Purpose: brute-force a weak HS256 JWT secret with a wordlist
hashcat -a 0 -m 16500 jwt.txt /usr/share/wordlists/rockyou.txt
# or
python3 -c "import jwt; print(jwt.decode('$JWT', 'guessed_secret', algorithms=['HS256']))"
```
**Next Step**: with a cracked/bypassed secret, forge a token with
elevated claims and replay it against protected endpoints.

## Technique: Session Management Flaws

**Fast path**:
```bash
# Purpose: check if session tokens are predictable/sequential
for i in 1 2 3; do curl -s -c - https://target.example/login -d "user=test$i&pass=x" | grep session; done
```
**Indicators**: sequential/incrementing session IDs, session ID not
invalidated after logout (test by reusing an old cookie post-logout),
missing `HttpOnly`/`Secure` flags (session theft via XSS becomes viable).

## Technique: Password Reset / Account Recovery Flaws

**Indicators**: reset token predictable (timestamp-based, sequential,
short numeric), reset token not invalidated after use, reset token leaked
in a Referer header or logged response, no rate limit on reset-code
guessing (short numeric OTP + no lockout = brute-forceable).

```bash
# Purpose: brute-force a short numeric reset code with no rate limiting
for code in $(seq -w 0000 9999); do
    r=$(curl -s -o /dev/null -w '%{http_code}' https://target.example/reset -d "code=$code&user=victim")
    [ "$r" == "200" ] && echo "HIT: $code"
done
```

## Technique: Multi-Factor / Logic Bypass

**Indicators**: MFA step is a separate request that can be skipped
entirely by calling the post-MFA endpoint directly with the pre-MFA
session; MFA code check happens client-side only (visible in JS).

## Common Mistakes

- Not checking `alg: none` / algorithm-confusion on JWTs before jumping to
  brute-forcing the secret — the free bypass is much faster if it works.
- Testing authentication bypass techniques without first confirming
  exactly what a successful vs failed response looks like (status code,
  body content, redirect target) — leads to false negatives.
- Ignoring cookie flags (`HttpOnly`, `Secure`, `SameSite`) which often
  telegraph the intended vuln class (missing `HttpOnly` → challenge
  probably wants XSS-based session theft).
