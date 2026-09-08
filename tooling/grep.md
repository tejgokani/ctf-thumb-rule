# grep Quick Reference

Purpose: pattern search across text/decompiled output/log files — the
workhorse for every broad sweep in this repository ("find flag-shaped
strings," "find suspicious function names," etc.).

```bash
# Purpose: case-insensitive, recursive, extended-regex search — the default CTF-sweep invocation
grep -RniE 'flag|secret|key|password|token' ./source_tree/

# Purpose: only filenames, not matching lines — useful for a quick "which files have this" scan
grep -Rl 'flag{' ./extracted/

# Purpose: print N lines of context around a match — helps judge relevance fast without opening the file
grep -RniE -C 3 'password' ./logs/

# Purpose: search binary files too (default grep often skips binaries) — critical for extracted/carved data
grep -a 'flag{' ./carved_data.bin

# Purpose: exact flag-shaped pattern extraction with only-matching output
grep -RnoE '[A-Za-z0-9_]{2,20}\{[^}]{3,80}\}' ./work/
```

## Indicators & Decisions

- Zero matches on an obvious keyword sweep → don't conclude "not here" —
  the data may be encoded/binary; re-triage rather than trusting a text
  grep on non-text content.
- Many matches (noise) → narrow the pattern (add more specific keywords,
  restrict file extensions with `--include`) rather than reading all hits
  manually.

## Alternatives

- `ripgrep` (`rg`) — see `ripgrep.md` — dramatically faster on large
  trees, respects `.gitignore` by default (disable with `--no-ignore`
  when searching a dumped repo where that matters).

## Common Mistakes

- Forgetting `-a` when grepping binary/carved data — default grep treats
  binary files as "binary matches" and suppresses line output.
- Case-sensitive searches missing `Flag`/`FLAG` variants — always `-i`
  unless there's a specific reason not to.
- Not using `-R` and only searching the current directory when a
  recursive sweep across an extracted tree was needed.
