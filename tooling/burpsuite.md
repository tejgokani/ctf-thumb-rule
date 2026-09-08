# Burp Suite Quick Reference

Purpose: intercepting proxy + toolkit for manual and semi-automated web
testing — see `../web-security/reconnaissance.md` for the full workflow
this supports, and per-vuln-class docs for specific payloads.

## Core Workflow

1. **Proxy**: route browser traffic through Burp (default
   `127.0.0.1:8080`), browse the app normally to passively build the site
   map.
2. **Target → Site map**: review every captured request — surfaces
   background XHR calls the UI never explicitly links.
3. **Proxy → HTTP history**: full request/response log, filterable.
4. **Repeater**: send any request here (right-click → Send to Repeater)
   to manually tamper with parameters/headers and replay quickly —
   the primary tool for iterative payload testing.
5. **Intruder**: automate fuzzing a specific parameter (wordlist-based
   path discovery, brute-force, IDOR ID sweeps) using an authenticated
   session automatically.
6. **Decoder**: quick encode/decode (base64, URL, hex, gzip) without
   leaving the tool — useful mid-investigation for a quick transform
   check.
7. **Comparer**: diff two responses byte-for-byte — useful for spotting
   subtle differences between an authorized and unauthorized response
   (authz testing).

## CLI Alternatives (No Burp / Scripted Workflow Preferred)

```bash
# Purpose: manually replay a request with a session cookie, equivalent to Repeater for simple cases
curl -s -b "session=abc123" https://target.example/api/profile

# Purpose: fuzz a parameter across a wordlist, equivalent to Intruder for simple cases
ffuf -u "https://target.example/api/resource/FUZZ" -w ids.txt -H "Cookie: session=abc123"
```

## Common Mistakes

- Not proxying the *entire* app session before starting manual testing —
  missing background XHR calls that only Burp's passive site map would
  have caught.
- Using Repeater for what should be an Intruder job (iterating many
  values) — wastes significant manual time versus letting Intruder
  automate the sweep.
- Forgetting Comparer exists and manually eyeballing two large responses
  for differences instead of diffing them directly.
