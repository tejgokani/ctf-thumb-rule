# Solved Challenges — Write-Up Format & Requirements

Every solved challenge gets **two** artifacts in this directory:

1. A markdown write-up: `<challenge-slug>.md`, following the exact template
   below.
2. A plain-text write-up: `<challenge-slug>.txt`, following the exact
   three-section format below.

Both are required. The markdown file is the detailed technical record; the
`.txt` file is the compact required-format submission used for
competition write-up requirements.

See `example-crypto-warmup.md` and `example-crypto-warmup.txt` for a
fully worked (illustrative, non-competition) example of both formats.

## Markdown Write-Up Template

```markdown
# <Challenge Name>

**Category**: <Digital Forensics | Reverse Engineering | Web Security | Cryptography | Steganography | OSINT | ...>
**Difficulty**: <Easy | Medium | Hard | Insane, or the competition's stated point value>

## Challenge Description
<Verbatim or near-verbatim copy of what the challenge gave you: prompt text, provided files, connection info.>

## Initial Assessment
<What did `file`/triage reveal? What was your first read of the artifact before doing anything else?>

## Initial Approach
<What was your first hypothesis and why? Which category playbook/checklist did you start from?>

## Investigation
<The actual step-by-step investigation: commands run, in order, and what each revealed. This should read
as a reproducible log, not a summary — enough detail that someone else could follow it exactly.>

## Solution
<What technique ultimately worked, and why it worked (the mechanism, not just "I ran tool X").>

## Flag Identification
<How you confirmed this was the real flag: format match, provenance, reproducibility — per
methodology/flag-identification.md.>

## Reproduction
<Exact command sequence, from the original untouched artifact, that reproduces the flag end to end.>

## Key Insight
<The one sentence that would have made you solve this faster if you'd known it going in.>

## Tools Used
<List of tools/versions actually used.>

## Lessons Learned
<What generalizes beyond this specific challenge? (This should also be extracted into the relevant
category playbook per methodology/general-workflow.md's Knowledge Extraction process — never copy the
actual flag or challenge-specific secrets into the generic playbook, only the reusable technique.)>

## Difficulty/Efficiency Notes
<Was this challenge harder or easier than its point value suggested? Was there a faster path you
discovered only after solving it, worth recording for next time?>

## Flag
`<the flag>`
```

## Required TXT Write-Up Format

Exactly three sections, in this order, in a plain `.txt` file:

```
INITIAL APPROACH

<Free-text paragraph(s): what you saw first, what you assumed, and what your starting plan was.>

STEPS OF RECREATION

<Numbered or sequential list of the exact steps/commands taken, in order, sufficient for someone else
to reproduce the solve from the original artifact.>

IDENTIFICATION

<How the flag was confirmed as genuine: format match, extraction method, and the flag itself.>
```

Keep the `.txt` version tighter than the markdown version — it's the
compact record, not a duplicate of the full write-up. No markdown syntax
inside it (plain text only).

## Rules for This Directory

- One challenge = one `.md` + one `.txt` pair, same slug.
- Never fabricate a flag or present a template/example as a real solved
  competition challenge — the example files in this directory are
  explicitly labeled as illustrative templates, not real solves, and
  their flag values are placeholders (not from any real "What The Flag"
  competition).
- After writing a real solve, extract any generalizable technique into the
  relevant category playbook per `../methodology/general-workflow.md`'s
  Knowledge Extraction section — never leak challenge-specific secrets
  into those generic docs, only the reusable method.
- Category tag should match one of: Digital Forensics, Reverse
  Engineering, Web Security, Cryptography, Steganography, OSINT, or a
  more specific subcategory if useful (e.g. "Reverse Engineering — APK").
