# Flag Identification — Recognizing a Real Flag

## Flag Prefix Patterns

Most CTF competitions use a fixed, structured prefix so both humans and
scoring infrastructure can recognize a flag on sight. Common patterns you
will encounter across competitions (verify the actual format for *this*
competition the moment you see the first example — often in a "welcome" or
sample challenge):

```
flag{...}
FLAG{...}
CTF{...}
WTF{...}
wtf{...}
whatflag{...}
<competition_name>{...}
```

Structure inside the braces is typically:
- Alphanumeric plus underscores/hyphens, sometimes a short human-readable
  phrase (`flag{not_all_bytes_are_equal}`).
- Sometimes a hash-like token (`flag{9f86d081884c7d659a2feaa0c55ad015}`).
- Occasionally base64/hex encoded further inside the braces — read the
  challenge to see if a second decode step is expected before submission.

**Document the confirmed format for the current competition** at the top of
your working notes as soon as you see one real example (e.g. from a sample
challenge or the rules page), and treat that as authoritative for the rest
of the event over any generic pattern listed here.

## Never Blind-Submit Every `flag{...}`-Shaped String

A regex match for `[a-zA-Z0-9_]+\{[^}]+\}` is a **candidate locator**, not
proof. CTF artifacts routinely contain decoy strings that are shaped like
flags but aren't:

- Red herrings deliberately planted by the challenge author.
- Format strings, template placeholders, or example text from the
  underlying framework/library the challenge was built on (e.g. a `flag{}`
  literally appearing in a library's own test fixtures).
- Fragments in binary padding or in a totally unrelated part of a large
  extracted archive.

```bash
# Purpose: locate flag-shaped candidates in decoded/extracted output — a locator, not a verdict
grep -RnoE '[A-Za-z0-9_]{2,20}\{[^}]{3,80}\}' work/
```

Treat every hit as a hypothesis to validate (see below), not an answer to
submit.

## Context Validation — Before Submitting

Confirm all of the following before submitting a candidate flag:

1. **Format match** — matches the competition's confirmed flag pattern
   exactly (prefix, braces, allowed character set).
2. **Provenance** — you can point to the specific artifact and specific
   transformation chain that produced this string (see
   `evidence-handling.md`'s reproducibility standard). If you can't explain
   *why* this string came out of *this* challenge's data, don't submit it.
3. **Uniqueness / plausibility** — is this the only flag-shaped string in
   the output, or one of several? If several, the real one is usually the
   one that required the actual solve technique to reach (e.g. after the
   final decode step), not one sitting unencoded in a decoy file.
4. **Not cross-challenge contamination** — especially in a shared `work/`
   habit, verify the candidate actually came from *this* challenge's
   artifact copy, not leftover output from a previous challenge's scratch
   directory.
5. **Reproducible on a clean copy** — re-run your recorded command chain
   against the original untouched artifact (from `evidence/`) and confirm
   you get the same string. If it doesn't reproduce cleanly, you don't yet
   understand the solve well enough to be confident it's correct.

## Practical Workflow

```bash
# Purpose: candidate found mid-investigation — validate before treating as done
echo "candidate: flag{example_candidate_only}" 
# 1. Does it match today's confirmed competition format?
# 2. Which exact command produced it? (should already be in work/log.txt)
# 3. Re-run that command chain against evidence/original_artifact.bin
# 4. Only then: submit, and immediately record it in solved-challenges/
```

If a candidate fails validation, don't discard the investigation — it's
often a genuine partial result (e.g. a decoy pointing at the real location,
or one layer of a multi-layer encoding) that feeds back into Phase 4
(Hypothesis) of `general-workflow.md`.
