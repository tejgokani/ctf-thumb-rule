# Username & Email Pivoting

## Fastest First-Pass Checklist

```bash
# Purpose: sweep dozens of platforms for a matching username in one pass
sherlock <username>

# Purpose: alternate/complementary tool with a different platform list
python3 -m maigret <username>

# Purpose: search-engine pivot on the raw username string as a supplement to automated tools
# (browser) "exact_username" -site:excludethenoise.com
```

## Technique: Automated Username Sweeps

**When to use it**: given a specific handle/username, first move.

**Fast path**: `sherlock`/`maigret` as above — output a list of platforms
where the username exists. Manually verify each hit (avoid false
positives from generic "username taken" pages that don't confirm an
actual matching profile).

**Indicators**: multiple platforms with matching profile photos/bios/
posting history strongly suggest the same real individual/entity, not
coincidental name reuse.

**Next Step**: read each confirmed profile for links to other pivots
(personal site, other social accounts, email addresses, location tags).

## Technique: Email Pivoting

**Fast path**:
```bash
# Purpose: check if an email is associated with known breach data (useful for OSINT-legitimate provenance,
# not for credential stuffing — only use to establish identity, never attempt login with found credentials)
# (browser) haveibeenpwned.com/account/<email>   — confirms account existence/breach association

# Purpose: Gravatar hash lookup — many platforms use MD5(lowercased email) as an avatar key
echo -n "target@example.com" | md5sum
# then check https://www.gravatar.com/avatar/<hash> and https://en.gravatar.com/<hash>.json for a public profile
```
**Indicators**: a populated Gravatar profile reveals a real name, other
linked accounts, or a bio directly.

## Technique: GitHub / Code-Hosting Investigation

**When to use it**: username is plausibly a developer/technical target, or
the challenge context implies a repo exists.

**Fast path**:
```bash
# Purpose: enumerate a user's public repos, commit emails, and org memberships
curl -s "https://api.github.com/users/<username>/repos" | jq -r '.[].full_name'
curl -s "https://api.github.com/users/<username>/events/public" | jq -r '.[].type'

# Purpose: commit metadata often reveals a real email even when the GitHub profile hides it
git log --all --format='%ae' | sort -u    # once a relevant repo is cloned
```
**Indicators**: a commit author email differing from the profile's public
email, a starred/forked repo revealing an interest or affiliation, an
organization membership narrowing the target's employer/context.

## Technique: Cross-Platform Correlation via Avatar Image

**Fast path**:
```bash
# Purpose: hash a profile picture to check for exact reuse across platforms (same file, different site)
sha256sum avatar.jpg

# Purpose: reverse image search for near-identical (not just byte-identical) reuse — see images.md
```
**Indicators**: identical avatar (same hash, or visually identical via
reverse image search) across two platforms is strong linking evidence —
much stronger than username similarity alone.

## Common Mistakes

- Trusting `sherlock`/`maigret` hits without manual verification — many
  platforms return a "profile exists" page even for unregistered
  usernames (false positive), especially on less common tools.
- Assuming a matching username across platforms is the same person without
  any corroborating evidence (photo, bio, writing style, linked accounts).
- Never attempt to log in with credentials found via breach-data lookups —
  that crosses from OSINT into unauthorized access; see the Safety
  Boundary in `../methodology/evidence-handling.md`.
