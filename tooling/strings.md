# strings Quick Reference

Purpose: extract printable-character sequences from any binary file — the
cheapest possible first look at unknown content.

```bash
# Purpose: default strings, minimum length 4 — often too noisy for large binaries
strings ./artifact

# Purpose: longer minimum length cuts noise, surfaces more meaningful hits first
strings -n 8 ./artifact

# Purpose: search across all sections/data, not just ones strings(1) considers "loaded" — catches more
strings -a ./artifact

# Purpose: search for wide (UTF-16) strings too — common in Windows binaries
strings -e l ./artifact.exe    # little-endian 16-bit

# Purpose: pipe directly into a targeted grep instead of eyeballing full output
strings -n 8 ./artifact | grep -iE 'flag|wtf\{|password|key|token'
```

## Indicators & Decisions

- Flag-shaped or credential-shaped string found → validate per
  `../methodology/flag-identification.md` before treating as done.
- Very few/no meaningful strings in a binary that "should" have them
  (given its size) → likely packed/encrypted, see
  `../reverse-engineering/general-workflow.md`'s packing section.
- Wide/UTF-16 strings missing from default `strings` output on a Windows
  binary → always also run `-e l`.

## Alternatives

- `radare2`'s `iz`/`izz` — `izz` in particular finds strings outside
  standard string-bearing sections that plain `strings` sometimes misses.
- Ghidra's Defined Strings window — clickable, with cross-references
  directly to usage sites (a real advantage over flat `strings` output
  when you need to know *where* a string is used, not just that it
  exists).

## Common Mistakes

- Using the default minimum length (often 4) on a large binary and getting
  overwhelmed with noise — bump to `-n 8` or higher as a first pass.
- Forgetting `-a` and missing strings outside the sections `strings(1)`
  considers by default.
- Not checking for wide-character strings on Windows PE binaries.
