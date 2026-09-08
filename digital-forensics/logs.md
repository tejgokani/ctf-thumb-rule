# Log Analysis

## Fastest First-Pass Checklist

```bash
# Purpose: identify log format/source before assuming a schema
head -n 20 access.log

# Purpose: rough timeline of first/last entries
head -n 1 access.log && tail -n 1 access.log

# Purpose: quick keyword sweep for obvious indicators
grep -inE 'flag|error|fail|denied|unauthorized|admin|token|secret' access.log
```

## Technique: Web Server Access Log Analysis

**When to use it**: Apache/Nginx-style `access.log` artifacts.

**Fast path**:
```bash
# Purpose: most-requested URIs, sorted — spot unusual/one-off endpoints
awk '{print $7}' access.log | sort | uniq -c | sort -rn | head -30

# Purpose: unique client IPs, sorted by request volume — find the outlier
awk '{print $1}' access.log | sort | uniq -c | sort -rn | head -20

# Purpose: isolate all requests from one suspicious IP, in order
grep '^1\.2\.3\.4' access.log

# Purpose: pull all non-2xx/3xx status codes — errors and probing attempts
awk '{print $9}' access.log | sort | uniq -c | sort -rn
```
**Indicators**: a single IP hitting many distinct paths in a short window
(scanning/fuzzing), a request to an unusual path
(`/admin/backup.zip`, `/.git/config`, `/api/debug`), a 200 response on a
path that "shouldn't" exist.

**Next Step**: pull the full request/response context for the suspicious
line; if the log references a file path, check whether that path is one of
the challenge's other artifacts.

**Common Mistakes**: assuming Combined Log Format column positions without
checking — custom log formats shift field order; always `head` first to
confirm columns.

## Technique: Authentication / Auth Log Analysis

**Fast path**:
```bash
# Purpose: failed vs successful login attempts (Linux auth.log / secure)
grep -iE 'failed|invalid|accepted' /var/log/auth.log

# Purpose: count failed attempts per source IP — brute force indicator
grep 'Failed password' /var/log/auth.log | awk '{print $(NF-3)}' | sort | uniq -c | sort -rn
```
**Indicators**: a burst of failed logins followed by one success (brute
force → compromise); a successful login from an unusual source IP/time.

## Technique: Windows Event Log (EVTX)

**Fast path**:
```bash
# Purpose: convert EVTX to readable text/JSON for grepping (python-evtx or Yal libraries)
python3 -m evtx.dump security.evtx > security.txt
# or, on Kali/ParrotOS with evtx installed:
evtx_dump.py security.evtx -o json > security.json

# Purpose: search converted output for interesting Event IDs (4624=logon, 4625=failed logon, 4688=process creation)
grep -E '"EventID": (4624|4625|4688)' security.json
```
**Indicators**: Event ID 4688 (process creation) showing an unusual command
line; 4624/4625 clusters indicating login activity relevant to the
challenge's timeline.

## Technique: Application / Custom Log Correlation

**Fast path**:
```bash
# Purpose: normalize/extract timestamps for cross-log correlation
grep -oE '[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9:]+' app.log | sort -u

# Purpose: merge and time-sort multiple log sources for a unified timeline
cat access.log auth.log app.log | sort -k4 > merged_timeline.log   # adjust -k for actual timestamp field
```
**Indicators**: a single timestamp/session ID appearing across multiple log
sources — this is usually the thread to pull to reconstruct "what
happened," which is frequently the whole point of a forensics log
challenge.

## Common Mistakes

- Grepping for `flag` only in lowercase — always add `-i`.
- Not checking log *rotation* — the interesting entry may be in a `.1` or
  `.gz` rotated file, not the live log.
- Treating a log purely as text instead of checking timestamps/timezone
  consistency, which is often key to correlating events across sources.
