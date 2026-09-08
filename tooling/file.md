# file(1) Quick Reference

Purpose: identify true file type via magic-byte signature matching,
independent of extension — always the very first command on any unknown
artifact.

```bash
# Purpose: basic type identification
file ./artifact

# Purpose: MIME-type-only output, useful for scripting/comparison
file --mime-type -b ./artifact

# Purpose: don't stop at the first match — some polyglot files have multiple valid interpretations
file -k ./artifact

# Purpose: batch-identify every file in a directory in one pass
file ./*
```

## Indicators & Decisions

- Confident, specific type string → route directly to that format's
  workflow doc.
- Generic "data" output → likely raw/encoded/encrypted, or a
  deliberately-mangled header — escalate to `strings`/`xxd`/`binwalk`
  (see `../digital-forensics/file-analysis.md`).
- Type disagrees with the given file extension → extension-vs-actual-type
  mismatch, a common and cheap CTF trick — trust `file`, not the
  filename.

## Alternatives

- Manual magic-byte lookup via `xxd`/hex editor against a signature table
  when `file`'s built-in magic database doesn't recognize a
  custom/obscure format.
- `TrID` — an alternative signature database, occasionally identifies
  formats `file` doesn't.

## Common Mistakes

- Trusting the file extension over `file`'s actual determination.
- Not using `-k` on files that might be polyglots (valid as two different
  formats simultaneously) — a common and deliberate CTF trick.
