# Social Media Investigation

## Fastest First-Pass Checklist

```bash
# Purpose: pull a public profile's basic info without needing to log in, where APIs allow it
curl -s "https://api.github.com/users/<username>" | jq

# Purpose: check web archive for deleted/edited posts
curl -s "http://web.archive.org/cdx/search/cdx?url=twitter.com/<username>*&output=json" | jq
```

## Technique: Profile Analysis

**When to use it**: given a specific social media handle/profile as a
starting point.

**Fast path**: manually review, in order of information density: bio text
(often contains a personal site, email, or another handle), pinned/recent
posts, follower/following lists (for smaller/niche accounts, mutual
connections are informative), linked external URLs.

**Indicators**: a bio linking to a personal domain or another platform —
direct pivot point (see `domains.md`/`usernames.md`).

## Technique: Post History & Timeline Reconstruction

**Fast path**: for platforms with accessible APIs (GitHub, some
Mastodon instances, public RSS feeds), pull structured history rather than
scrolling manually:
```bash
# Purpose: GitHub public activity feed — structured, easy to grep for specific event types
curl -s "https://api.github.com/users/<username>/events/public" | jq -r '.[] | "\(.created_at) \(.type) \(.repo.name)"'
```
**Indicators**: a timeline correlating with a specific event/timeframe
referenced elsewhere in the challenge (a log timestamp, a file's metadata
timestamp).

## Technique: Deleted / Edited Content Recovery

**Fast path**:
```bash
# Purpose: Wayback Machine snapshots of a specific profile/post URL
curl -s "http://archive.org/wayback/available?url=<profile_or_post_url>" | jq
```
**Indicators**: an archived snapshot shows content since deleted/edited —
frequently the actual point of a "find the deleted post" style challenge.

## Technique: Cross-Platform Correlation

**Fast path**: compare writing style, posting times/timezone pattern,
avatar image (hash comparison, see `images.md`), and any explicitly shared
links across platforms to establish (not assume) that two accounts belong
to the same entity.

**Indicators worth treating as evidence**: identical avatar file hash,
explicit cross-link ("also on: ..." in a bio), identical unique phrase/
handle appearing in both profiles' text.

**Indicators NOT sufficient alone**: similar username, similar general
topic of posts, similar profile picture *style* (not identical file).

## Common Mistakes

- Scrolling a profile manually when a structured API/export exists —
  costs significant time for no benefit on platforms with public APIs.
- Treating "probably the same person" pattern-matching as proof — always
  seek a concrete corroborating link (see Evidence vs Assumption in
  `general-workflow.md`).
- Not checking whether a profile/post still exists in archive.org before
  concluding content is permanently gone.
