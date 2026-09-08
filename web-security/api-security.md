# API Security

## Fastest First-Pass Checklist

```bash
# Purpose: look for API documentation/schema endpoints — huge time-saver if present
curl -s https://target.example/swagger.json
curl -s https://target.example/openapi.json
curl -s https://target.example/api-docs

# Purpose: try common API versioning/base paths if no docs are exposed
for p in api api/v1 api/v2 graphql; do
    curl -s -o /dev/null -w "%{http_code} /$p\n" https://target.example/$p
done
```

## Technique: Schema/Documentation Discovery

**When to use it**: always first for any API-driven target — a
Swagger/OpenAPI spec hands you the entire endpoint + parameter map for
free, eliminating most of the enumeration work in `reconnaissance.md`.

**Indicators**: a reachable `/swagger.json`, `/openapi.yaml`, or GraphQL
introspection response.

**Next Step**: parse the spec for every endpoint/param and prioritize
anything mentioning admin, debug, internal, or export functionality.

## Technique: GraphQL-Specific Testing

**Fast path**:
```bash
# Purpose: introspection query reveals the full schema, including fields not used by the official client
curl -s -X POST https://target.example/graphql -H 'Content-Type: application/json' \
  -d '{"query":"{__schema{types{name,fields{name}}}}"}'

# Purpose: probe for a specific sensitive-looking field/type found in the schema
curl -s -X POST https://target.example/graphql -H 'Content-Type: application/json' \
  -d '{"query":"{ user(id: 1) { id, email, isAdmin } }"}'
```
**Indicators**: introspection is enabled (not disabled in production as it
should be) → full schema visible, including internal/unused fields and
mutations not exposed in the app's own UI.

**Next Step**: try batch queries / query aliasing to bypass naive
per-request rate limiting, and check for missing authorization on
individual resolvers (a field-level IDOR).

## Technique: Mass Assignment

**Indicators**: an API accepts a JSON body and blindly maps it onto a
model/object — extra fields beyond what the UI sends may still be
processed server-side.

**Fast path**:
```bash
# Purpose: add fields the official client never sends, see if the server accepts them anyway
curl -s -X POST https://target.example/api/users -H 'Content-Type: application/json' \
  -d '{"username":"test","password":"test","isAdmin":true,"role":"admin"}'
```

## Technique: Rate Limiting / Business Logic Abuse

**Fast path**:
```bash
# Purpose: check if repeated identical requests are throttled at all
for i in $(seq 1 50); do curl -s -o /dev/null -w "%{http_code}\n" https://target.example/api/login -d 'user=a&pass=b'; done | sort | uniq -c
```
**Indicators**: no 429/throttling after many rapid requests → brute-force/
enumeration paths that "shouldn't" work in theory become viable in
practice.

## Technique: API Versioning Gaps

**Fast path**:
```bash
# Purpose: an older API version may lack a security fix present in the current one
curl -s https://target.example/api/v1/users/1
curl -s https://target.example/api/v2/users/1
```
**Indicators**: `v1` exposes data/functionality that `v2` correctly
restricts — legacy endpoints are frequently left reachable and unpatched.

## Common Mistakes

- Manually enumerating endpoints when a schema/docs endpoint would have
  given the full map for one request.
- Testing only the "current" API version and missing an unrestricted
  legacy version still deployed alongside it.
- Not checking GraphQL introspection — often left enabled even when REST
  endpoints are well-locked-down, because it's a less familiar surface to
  the app's own developers.
