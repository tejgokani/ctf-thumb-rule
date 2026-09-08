# OSINT — General Workflow

## Fastest First-Pass Checklist (OSINT Target)

```bash
# Purpose: search-engine operators narrow results fast, before manual browsing
# site: restricts to a domain; intitle:/intext: match specific fields; filetype: finds leaked docs
# example queries (run in a browser, not scriptable generically):
#   site:target.example filetype:pdf
#   intext:"exact string from challenge"
#   "exact username" -site:excludeme.com

# Purpose: username pivot across many platforms at once
sherlock <username>

# Purpose: WHOIS/RDAP for domain targets
whois target.example

# Purpose: certificate transparency logs reveal subdomains even for unlisted/internal-looking hosts
curl -s "https://crt.sh/?q=%25.target.example&output=json" | jq -r '.[].name_value' | sort -u
```

## Technique: Evidence vs Assumption

**Core discipline for OSINT**: every conclusion must trace back to a
specific, checkable source. "This is probably the same person because the
username is similar" is an assumption; "this account's bio links to the
same GitHub repo referenced in the challenge" is evidence. Log the source
URL/timestamp for every fact used in the solve — OSINT write-ups without
provenance are not verifiable and often wrong.

## Technique: Search Engine Operators

**When to use it**: first, cheap, and often decisive.

**Useful operators**:
```
site:example.com          restrict to a domain
intitle:"exact phrase"    phrase must appear in page title
intext:"exact phrase"     phrase must appear in page body
filetype:pdf               restrict to a file type (also: doc, xls, sql, log, env)
"exact phrase"             exact match, quotes matter
-excludeterm                exclude a term/site
inurl:admin                 phrase must appear in the URL
```
**Indicators**: a direct hit is the fastest possible OSINT win — always
try targeted search-engine queries before pivoting to platform-specific
tools.

## Technique: Establishing a Pivot Point

Every OSINT challenge gives you at least one starting anchor: a username,
an email, an image, a domain, a piece of text. Identify which category it
is and jump to the matching doc:

- Username/handle → `usernames.md`
- Domain/hostname/IP → `domains.md`
- Image (person, place, object) → `images.md`
- Social media profile/post → `social-media.md`
- Location implied by an image/description → `geolocation.md`

## Technique: Chaining Pivots

OSINT solves are rarely one hop. A typical chain: username found in a
challenge's provided file → pivot to that username's public profiles
(`usernames.md`) → a bio links a personal domain → domain recon
(`domains.md`) → WHOIS/cert-transparency reveals another subdomain →
that subdomain hosts the flag or a further clue.

**Discipline**: at each hop, write down the specific piece of evidence
that justified the pivot (a matching avatar hash, an identical bio phrase,
a linked URL) — don't pivot on vibes/similarity alone, verify.

## Common Mistakes

- Pivoting on assumption (similar username, similar writing style) instead
  of concrete linking evidence (shared image hash, explicit cross-link,
  identical unique string).
- Not recording source URLs as you go — makes the required write-up
  (`../solved-challenges/README.md`) much harder to produce accurately
  after the fact.
- Giving up on a dead platform/account without checking web archives
  (`web.archive.org`) for historical snapshots — deleted/edited content is
  often still recoverable.
- Missing that OSINT challenges sometimes require a decode step first
  (see `../methodology/triage.md`'s tunnel-vision warning) — a bio/caption
  containing a base64 blob isn't itself the OSINT clue, it's an encoding
  step that needs `../cryptography/crypto-triage.md` first.
