# Evidence Handling — Preservation, Reproducibility, Safety Boundary

## Safety Boundary (read first)

Every technique in this repository — forensic parsing, disassembly,
debugging, injection testing, credential/key recovery, OSINT pivoting — is
scoped strictly to:

- Artifacts provided by a CTF competition (files, PCAPs, memory images,
  binaries, challenge web apps).
- Official competition infrastructure explicitly provided for the
  challenge (a `nc host port`, a challenge-hosted web app, a provided VM).
- Sandboxed/lab environments the practitioner controls.
- Testing explicitly authorized in writing (e.g. a bug bounty scope, an
  employer-sanctioned pentest).

Nothing here is to be applied to production systems, third-party
infrastructure, or any target without explicit authorization. If a
challenge artifact appears to reference or interact with real, live,
non-competition infrastructure, stop and treat that as out of scope rather
than continuing the technique against it.

## Preserve Evidence

Work on copies, never originals. Never modify or "fix" a provided artifact
in place if a byte-for-byte copy is available.

```bash
# Purpose: create a working copy before running anything else, and hash the original
mkdir -p work evidence
cp -v challenge_artifact.bin evidence/original_artifact.bin
sha256sum evidence/original_artifact.bin > evidence/original.sha256
cp -v evidence/original_artifact.bin work/artifact.bin
```

If a technique is destructive or unpacking (extracting an archive,
decompiling, carving with `binwalk -e`), always run it against `work/`, and
re-copy from `evidence/` if you need a clean artifact again.

## Reproducibility Standard

For every solve, record enough that a third party (or you, a week later)
can reproduce the exact flag from the exact original artifact with zero
guesswork. Minimum record:

1. **Input file(s)** — original filename(s) and their SHA-256 hash(es).
2. **Tools used** — name and version (`exiftool -ver`, `binwalk --version`,
   `python3 --version`, etc.). Version matters: tool behavior changes.
3. **Exact commands** — full command lines, in the order run, including
   flags/parameters. Not paraphrased.
4. **Output at each step** — the actual output (or a representative
   excerpt) that informed the next decision, not just "it worked."
5. **Transformation sequence** — the ordered chain of operations applied to
   go from raw artifact to flag (e.g. `binwalk -e` → carve `payload.zip` →
   unzip with password `x` → base64-decode `note.txt` → flag). This chain
   *is* the technical proof of the solve.
6. **Final extraction method** — precisely how the flag string was
   isolated from its surrounding context (grep pattern, script, manual
   read) so it's clear it wasn't hand-typed or guessed.

Keep a running log file per challenge in `work/`:

```bash
# Purpose: append every command + a one-line note on its result to a running log
echo '$ exiftool artifact.png' >> work/log.txt
exiftool artifact.png | tee -a work/log.txt
echo '# -> Comment field contains base64-looking string, see next command' >> work/log.txt
```

This log becomes the backbone of both the markdown write-up and the
required `.txt` write-up in `solved-challenges/` (see that directory's
`README.md` for the exact templates) — write the log as you go, not
retroactively from memory, because memory of exact commands and outputs
degrades fast and the write-up must be accurate, not approximate.

## Common Evidence-Handling Mistakes

- Running extraction/decoding tools directly on the only copy of the
  artifact, then losing the original state when a tool mangles it.
- Not hashing the artifact before modifying it — makes it impossible to
  later prove which file a flag actually came from.
- Solving interactively in a shell history with no log, then being unable
  to reconstruct the exact command sequence for the write-up.
- Mixing scratch work from multiple challenges in the same `work/`
  directory — always use one working directory per challenge.
