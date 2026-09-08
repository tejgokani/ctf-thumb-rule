# Source Code Analysis (Web)

## Fastest First-Pass Checklist

```bash
# Purpose: pull raw HTML source (not the rendered DOM) — comments and hidden fields live here
curl -s https://target.example | less

# Purpose: broad grep sweep for secrets/hints across a provided source tree
grep -RniE 'flag|secret|password|api[_-]?key|todo|fixme|debug' ./source/

# Purpose: check for a leftover .git directory exposed on the live server
curl -s -o /dev/null -w '%{http_code}\n' https://target.example/.git/config
```

## Technique: When Full Source Is Provided

**When to use it**: challenge hands you the app's source tree directly
(common in "White-box" style web challenges).

**Fast path**:
```bash
# Purpose: find the actual route/controller handling logic before testing blind
grep -RnE 'app\.(get|post|put|delete)|@app\.route|Route::(get|post)' ./source/

# Purpose: find hardcoded secrets/credentials committed to source
grep -RniE "password|secret|api_key|token" ./source/ --include='*.env' --include='*.config.*' --include='*.py' --include='*.js' --include='*.php'

# Purpose: check for exposed .env or config files that should never ship
find ./source/ -iname '.env*' -o -iname 'config.*'
```
**Indicators**: hardcoded credentials, a debug flag left `True`, a
comparison logic bug (e.g. `==` used for a security-sensitive comparison
in a loosely-typed language, enabling type juggling).

**Next Step**: once the vulnerable route/logic is identified in source,
jump directly to the matching technique doc (`injection.md`, `xss.md`,
etc.) instead of black-box probing — you already know exactly what to
send.

## Technique: Exposed `.git` on a Live Server

**When to use it**: no source provided directly, but you suspect (or want
to check) that a `.git` directory was accidentally deployed alongside the
live app.

**Fast path**:
```bash
# Purpose: confirm .git/config is reachable — if so, the whole repo is likely reconstructable
curl -s https://target.example/.git/config

# Purpose: automate full repo reconstruction from an exposed .git directory
git-dumper https://target.example/.git/ ./dumped_repo
```
**Next Step**: once dumped, treat it exactly as a provided Git repo — see
`../digital-forensics/common-file-formats.md`'s Git section (history,
deleted files, dangling commits).

## Technique: Language-Specific Type Juggling / Loose Comparison Bugs

**Indicators (PHP)**: `==` used instead of `===` for comparing hashes or
user input — `"0e12345" == "0e67890"` evaluates true in PHP (both parse as
scientific notation 0), a classic magic-hash bypass.

**Fast path**:
```bash
# Purpose: test a magic-hash style bypass against a loose (==) hash comparison
curl -s -X POST https://target.example/login -d 'password[]=x'   # array-vs-string type confusion in PHP
```
**Indicators**: passing an array where a string is expected causes a
comparison function (`strcmp`, `md5`) to return `NULL`/`false` in a way
that a loose `==` check against another `false`-ish value incorrectly
passes.

## Common Mistakes

- Black-box probing a target when full source was already provided —
  always read the source first; it directly tells you the vulnerable
  sink and exact payload shape needed, which is dramatically faster than
  blind testing.
- Not checking for `.git` exposure as a first move on any live target with
  no provided source — a very cheap check with a very high payoff when it
  hits.
- Missing language-specific comparison quirks (PHP loose typing, JS `==`
  coercion) when reading authentication/validation logic in source.
