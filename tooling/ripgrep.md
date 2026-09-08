# ripgrep (rg) Quick Reference

Purpose: `grep` alternative optimized for speed on large trees (decompiled
source, extracted filesystem images, big log sets). Prefer `rg` over
`grep -R` whenever the search tree is large — the difference is
significant on multi-gigabyte decompiled/extracted trees.

```bash
# Purpose: recursive, case-insensitive sweep — rg is recursive and respects binary-detection by default
rg -i 'flag|secret|key|password|token'

# Purpose: search binary files too (rg skips them by default, similar to grep's default)
rg -a 'flag{'

# Purpose: don't respect .gitignore — needed when searching a dumped/reconstructed git repo tree
rg --no-ignore -i 'flag'

# Purpose: only list matching filenames
rg -l 'flag{'

# Purpose: restrict search to specific file types for a faster, more targeted sweep
rg -t py -i 'password'
rg -g '*.smali' -i 'flag'

# Purpose: show N lines of context
rg -C 3 -i 'token'
```

## Indicators & Decisions

Same as `grep.md` — `rg` is a drop-in performance upgrade, not a
different search strategy. Use it whenever the target tree is large
(decompiled APK output, a large extracted filesystem, a big log
directory).

## Alternatives

- `grep -R` — always available with zero setup, fine for small trees.
- `ag` (the Silver Searcher) — similar performance profile, less
  commonly pre-installed than `rg` on modern security distros.

## Common Mistakes

- Forgetting `--no-ignore` when searching a directory containing a `.git`
  folder or `.gitignore` file — `rg` silently skips ignored paths by
  default, which can hide exactly the file you're looking for.
- Not restricting file type/glob (`-t`/`-g`) on a very large tree when a
  targeted search would return relevant hits much faster.
