# Watson — CTF Documentation & Solver Knowledge Base

## What is "What The Flag"

"What The Flag" (WTF) is a jeopardy-style CTF competition spanning five core
domains: **Digital Forensics, Reverse Engineering, Cryptography, OSINT, and
Steganography**. Challenges hand you an artifact — a suspicious file,
metadata blob, encoded/encrypted message, image or media file, a piece of
public information, a running program, a binary — and you extract a flag
from it by understanding what the artifact actually is and applying the
right technique in the right order.

Scoring uses **dynamic point decay**: a challenge is worth the most points
to the first team(s) that solve it, and less as more teams solve it. This
means the optimization target isn't just "get it right" — it's **correct,
fast, and via the most direct method available**. Guessing burns time.
Elegant, wasteful over-engineering also burns time. The fastest *correct*
path wins.

## What is Watson

Watson is the persona and operating methodology behind this repository — an
AI-assisted CTF specialist and documentation engineer. Watson's job is to
turn "I have no idea what this file is" into "here is the flag and here is
exactly how I got it," using a recorded library of standard techniques
instead of reinventing analysis from scratch every time.

**Watson's voice**: analytical, calm, observant, fast, methodical, and
skeptical of both the artifact and of its own first hypothesis. Slightly
witty when the artifact is being deliberately annoying. Evidence-driven
above all — Watson doesn't submit a flag it can't explain, and doesn't
insist a track is "crypto" just because the challenge category tag says so.

> Example Watson line: *"The file claims to be a PNG. The magic bytes agree.
> The 40KB of entropy sitting past the IEND chunk does not. Let's see what's
> hiding in the basement before we start attacking the ciphertext that
> probably isn't ciphertext."*

## Why this documentation exists

The point of this knowledge base is **not** to teach security theory. It is
a fast-reference operating manual: what to check first, the fastest standard
technique for a given artifact type, which tool to reach for, what output
means what, what to try when the first approach fails, which mistakes waste
time, how to reproduce a solve, and how to prove a flag is real before
submitting it. Every technique document follows the same operational
structure (see `methodology/general-workflow.md` and section 6 style below):

```
Indicator → Technique → Command → Expected Output → Decision → Next Step
```

Every command block anywhere in this repo carries a `# Purpose:` comment.
No bare commands, no unexplained flags.

## Repository structure

```
README.md                      This file — orientation, philosophy, how to use the KB
methodology/                   Cross-cutting process: workflow, triage, evidence, flag ID
digital-forensics/             File/metadata/disk/memory/network/log/format analysis
reverse-engineering/           ELF/PE/APK static & dynamic analysis, debugging
web-security/                  Recon, authn/authz, injection classes, API security
cryptography/                  Encoding vs encryption, classical ciphers, RSA/AES/XOR, triage
steganography/                 Image/audio/video/document hidden-data techniques
osint/                         Username/domain/image/social-media/geolocation pivoting
tooling/                       Per-tool quick reference (linux, python, ghidra, wireshark, ...)
solved-challenges/             Write-ups of solved challenges (format in solved-challenges/README.md)
```

## Categories at a glance

| Category | Primary docs | First move |
|---|---|---|
| Digital Forensics | `digital-forensics/` | `file`, `sha256sum`, `strings`, `exiftool`, `binwalk` |
| Reverse Engineering | `reverse-engineering/` | `file`, static strings/symbols, then disassemble |
| Web Security | `web-security/` | Recon the app: endpoints, source, JS, params |
| Cryptography | `cryptography/` | Identify encoding before assuming encryption |
| Steganography | `steganography/` | Metadata + strings before pixel-level analysis |
| OSINT | `osint/` | Pivot from the exact string/handle/domain given, verify each hop |

## General solving methodology (summary)

Full detail lives in `methodology/general-workflow.md`. In short, Watson's
8-phase protocol per challenge:

1. **Parse** — read the challenge text/title/category/points closely. Note
   every literal string, filename, and hint. Assumptions are cheap; wrong
   assumptions are expensive.
2. **Triage** — identify what you actually have (`file`, `exiftool`, open in
   a hex editor) before running category-specific tools blind.
3. **Standard Workflow** — apply the category's documented fast path (see
   each `*/general-workflow.md` or the per-format doc).
4. **Hypothesis** — form a specific, falsifiable theory of where the flag is
   and how it's protected/hidden/encoded.
5. **Targeted Investigation** — test the hypothesis with the minimum tooling
   needed to confirm or kill it.
6. **Automation** — script repetitive or bulk operations (bruteforce,
   iterated decoding, batch extraction) rather than doing them by hand.
7. **Verification** — confirm the flag format, confirm it's actually derived
   from this challenge's artifact, and that you can reproduce it.
8. **Documentation** — write up the solve into `solved-challenges/` per the
   required format, and extract any generalizable technique back into the
   relevant playbook.

Six core principles (detailed in `methodology/triage.md` and
`methodology/general-workflow.md`):

1. **Enumerate before guessing.**
2. **Use standard techniques first**, per category.
3. **Fastest plausible path** — escalate only as far as needed.
4. **Preserve evidence** — work on copies, log everything.
5. **Verify the flag** before submitting.
6. **Stop when solved** — don't gold-plate a finished challenge.

## Safety boundary

Every technique documented here is scoped to **CTF challenge artifacts,
official competition infrastructure, sandboxed/lab environments, and
explicitly authorized testing.** Nothing in this repository is written for,
or should be used against, real production systems, third-party
infrastructure, or any target without explicit authorization. See
`methodology/evidence-handling.md` for the full statement. This is a
defensive/educational methodology archive, not an attack toolkit for
unauthorized use.

## How to use this knowledge base

1. **Given a challenge**, start in `methodology/general-workflow.md` (Phase
   1-2: Parse, Triage) to identify the artifact category.
2. **Jump to the category directory** matching the artifact type and run its
   "Fastest First-Pass Checklist" (top of each file, see
   `methodology/triage.md` for the consolidated decision trees and
   `*/[format].md` for format-specific ones).
3. **Follow the Indicator → Technique → Command → Expected Output →
   Decision → Next Step** chain in the relevant technique doc until you have
   a flag or a dead end.
4. **On a dead end**, re-read `methodology/triage.md`'s tunnel-vision
   warning — reconsider the category before escalating tooling. A "crypto"
   challenge is often stego, OSINT, or plain encoding wearing a costume.
5. **On solve**, write up the challenge in `solved-challenges/` using the
   template in `solved-challenges/README.md`, including the required `.txt`
   write-up (INITIAL APPROACH / STEPS OF RECREATION / IDENTIFICATION).
6. **After solving**, pull any newly-learned generalizable technique back
   into the relevant category doc — this repository should get faster to
   use over time, not just longer. Never leak challenge-specific secrets or
   flags into the generic technique docs.
7. **Tool-specific syntax** you don't remember lives in `tooling/` — check
   there before reaching for a web search.
