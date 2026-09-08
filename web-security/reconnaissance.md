# Web Reconnaissance

## Fastest First-Pass Checklist (Web App)

```bash
# Purpose: response headers reveal server/framework/tech stack fingerprints
curl -I https://target.example

# Purpose: robots.txt and sitemap often leak paths not linked from the UI
curl -s https://target.example/robots.txt
curl -s https://target.example/sitemap.xml

# Purpose: full page source, view what the server actually sends (not the rendered DOM)
curl -s https://target.example | less

# Purpose: enumerate linked JS bundles, then pull and grep each for endpoints/secrets
curl -s https://target.example | grep -oE 'src="[^"]+\.js"'

# Purpose: directory/file brute force against common wordlist
ffuf -u https://target.example/FUZZ -w /usr/share/wordlists/dirb/common.txt -mc 200,301,302,403
```

## Technique: Understand the App

**When to use it**: always first — before testing any specific vuln class.

**Fast path**: click through the app manually once, noting: what actions
exist (login, search, upload, profile edit), what looks server-rendered vs
client-rendered (SPA), and what the URL structure implies about backend
routing (`/api/v1/...` vs server-rendered pages).

## Technique: Enumerate Endpoints

**Fast path**:
```bash
# Purpose: content/directory discovery
gobuster dir -u https://target.example -w /usr/share/wordlists/dirb/common.txt -x php,html,json

# Purpose: subdomain enumeration if scope includes the whole domain
gobuster dns -d target.example -w /usr/share/wordlists/subdomains-top1million-5000.txt

# Purpose: API-specific wordlists find REST endpoints faster than generic ones
ffuf -u https://target.example/api/FUZZ -w /usr/share/seclists/Discovery/Web-Content/api/api-endpoints.txt
```
**Indicators**: 200/301/302/403 responses on non-linked paths — 403
specifically means "exists but blocked," worth investigating for
authz-bypass techniques (`authorization.md`).

## Technique: Inspect Source & JavaScript

**Fast path**:
```bash
# Purpose: pull every JS file referenced by the page for offline analysis
for js in $(curl -s https://target.example | grep -oE 'src="[^"]+\.js"' | cut -d'"' -f2); do
    curl -s "https://target.example/$js" -o "$(basename $js)"
done

# Purpose: grep all pulled JS for endpoint paths, API keys, comments left by developers
grep -RnoE '"(/api/[^"]+|/v[0-9]/[^"]+)"' ./*.js
grep -RniE 'api[_-]?key|secret|token|TODO|FIXME|debug' ./*.js
```
**Indicators**: hardcoded API keys/tokens, commented-out debug endpoints,
a client-side "admin check" that's trivially bypassable, source maps
(`.js.map`) leaking original unminified source.

**Next Step**: if a `.js.map` file exists, pull it — it reverses
minification and often exposes far more logic/comments than the minified
bundle:
```bash
# Purpose: source maps reverse minification — check for one alongside every JS bundle
curl -s https://target.example/app.js.map -o app.js.map
```

## Technique: Identify Parameters

**Fast path**:
```bash
# Purpose: passive param discovery from URLs already seen (crawl + collect)
gau target.example | grep '?' | sort -u

# Purpose: active param discovery via brute force against a known endpoint
ffuf -u "https://target.example/search?FUZZ=x" -w /usr/share/seclists/Discovery/Web-Content/burp-parameter-names.txt -fs <baseline_size>
```

## Technique: Identify AuthN/AuthZ Boundaries

Covered in depth in `authentication.md` and `authorization.md`. At recon
stage, just note: is there a login? What does an authenticated vs
unauthenticated response look like for the same endpoint? Are there
distinct roles implied by the UI (admin panel link, user-specific IDs in
URLs)?

## Burp Suite Workflow

1. Proxy all traffic through Burp (browser → 127.0.0.1:8080) to passively
   build the sitemap while manually clicking through the app.
2. **Target → Site map**: review every request Burp captured — often finds
   endpoints the UI never explicitly linked (background XHR calls).
3. **Repeater**: send any interesting request here to manually tamper with
   parameters/headers and replay quickly.
4. **Intruder**: for brute-force/fuzzing a specific parameter (IDs,
   tokens, wordlist-based path discovery) when CLI tools aren't as
   convenient (e.g. need to reuse an authenticated session cookie
   automatically).
5. **Decoder**: quick encode/decode (base64, URL, hex) without leaving the
   tool.

## CLI Alternatives to Burp

```bash
# Purpose: curl with saved session cookie for authenticated requests
curl -s -b "session=abc123" https://target.example/api/profile

# Purpose: replay/modify a captured request quickly via a raw request file
curl -s -X POST https://target.example/api/login -d '{"user":"a","pass":"b"}' -H 'Content-Type: application/json'
```

## Common Mistakes

- Testing vuln classes before basic recon — wastes time attacking endpoints
  that don't exist or misunderstanding the app's actual structure.
- Never checking `robots.txt`/`sitemap.xml`/JS bundles — some of the
  cheapest possible recon steps, frequently skipped.
- Only looking at rendered DOM (browser dev tools "Elements" tab) instead
  of the actual response body via `curl`/`view-source:` — client-side JS
  can alter the DOM after load, hiding what the server actually sent.
- Not checking for source maps next to minified JS.
