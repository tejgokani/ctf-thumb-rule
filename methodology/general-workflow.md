# General Workflow — The 8-Phase Challenge-Solving Protocol

This is Watson's default operating loop for any challenge, in any category.
Skip phases only when the artifact is trivially obvious (e.g. a flag
literally printed in the challenge description) — otherwise run them in
order.

## Phase 1 — Parse

Read everything before touching a tool.

- Full challenge title, description, category tag, point value.
- Every literal string in the prompt: filenames, hostnames, usernames,
  version numbers, "flavor text" — CTF authors rarely waste words.
- Attached files: names, extensions, and counts (a `.zip` containing 3 files
  is a different puzzle than one containing 30).
- Any provided connection info (`nc host port`, a URL) — note it but don't
  connect yet if the category is forensics/RE/crypto and a static artifact
  is also provided.

```bash
# Purpose: list everything you were given, sizes, and quick type ID in one pass
ls -la ./challenge_files/ && file ./challenge_files/*
```

**Decision**: does the description name a technology, format, or technique
explicitly ("this JWT looks weird", "check the pixels")? If yes, that's a
strong prior — but verify it in Phase 2, don't just trust it.

## Phase 2 — Triage

Identify what you actually have before running category-specific tooling.
This phase is category-agnostic and always the same first few commands:

```bash
# Purpose: work on a copy, never the original artifact
mkdir -p work && cp -v ./challenge_files/* work/ && cd work

# Purpose: identify true file type from magic bytes, independent of extension
file ./*

# Purpose: hash every artifact now, before any modification — for evidence log and dedup
sha256sum ./* > ../hashes.txt

# Purpose: pull printable strings — often reveals format, embedded flag, or hints instantly
strings -n 8 ./artifact | less

# Purpose: eyeball the first/last bytes for magic numbers, headers, trailers, appended data
xxd ./artifact | head -n 20
xxd ./artifact | tail -n 20
```

**Decision**: route to the category playbook matching the true file type
(not the extension, not the challenge category label):
- Known structured format (APK, ELF, PE, PCAP, image, PDF, ZIP, memory
  image) → jump to that format's doc in `digital-forensics/` or
  `reverse-engineering/`.
- Plain text / short string with unusual character distribution → jump to
  `cryptography/crypto-triage.md`.
- A URL / running service → jump to `web-security/reconnaissance.md`.
- A username / handle / domain / image of a person or place →
  `osint/general-workflow.md`.
- `file` says image/audio/video and dimensions/duration look "too clean" or
  size is anomalously large for content → also check
  `steganography/` in parallel, don't wait for forensics to dead-end first.

## Phase 3 — Standard Workflow

Every category has a documented, ordered fast path (see section 6-style
docs across the repo). Run it top-to-bottom before improvising. Do not skip
steps because they "seem unlikely to matter" — the entire point of the
standard workflow is that it's cheaper to run than to reason about skipping.

Examples of standard workflows (full detail in their own docs):
- APK → `reverse-engineering/apk.md`
- PCAP → `digital-forensics/pcaps.md`
- Image → `steganography/images.md` + `digital-forensics/metadata.md`
- Web app → `web-security/reconnaissance.md`
- ELF → `reverse-engineering/elf.md`
- ZIP/archive → `digital-forensics/common-file-formats.md`
- PDF → `digital-forensics/common-file-formats.md`
- Git repo → `digital-forensics/common-file-formats.md`
- Encoded text → `cryptography/crypto-triage.md` (identify encoding *before*
  attacking as ciphertext)

## Phase 4 — Hypothesis

State, explicitly (in your working notes), a specific and falsifiable
theory:

> "The flag is base64-encoded and hidden in a PNG tEXt chunk, because
> `exiftool` showed a non-standard chunk name and the string that decodes
> from it is valid base64 alphabet."

A good hypothesis names: where the flag/data is, what transform was applied,
and what tool/command will confirm or kill it in under a minute. If you
can't state all three, you're not ready to leave Phase 3 — go collect more
triage data.

## Phase 5 — Targeted Investigation

Run the minimum command(s) needed to confirm or kill the hypothesis. Don't
reach for the heaviest tool first (e.g. don't open Ghidra to check one
string — `strings | grep` first).

**Decision**: hypothesis confirmed → extract and move to Phase 7
(Verification). Hypothesis killed → return to Phase 4 with the new data
point, or if two hypotheses in a row fail, return to Phase 2 and
re-triage — you likely mis-identified the artifact or category (see
tunnel-vision warning in `triage.md`).

## Phase 6 — Automation

If the solve requires anything iterative (bruteforcing a key/password,
trying N encodings, extracting every frame of a video, scanning every
endpoint), write a short script rather than doing it by hand. Time spent
scripting a 10-second loop is time saved on the 11th manual repetition, and
scripts are the reproduction record for Phase 8.

```python
# Purpose: skeleton for "try every plausible single-byte XOR key and keep printable results"
data = open("artifact.bin", "rb").read()
for key in range(256):
    out = bytes(b ^ key for b in data)
    if b"flag{" in out or b"WTF{" in out:
        print(key, out)
```

Keep scripts in `work/` alongside the artifact copy — they're part of the
evidence trail (see `evidence-handling.md`).

## Phase 7 — Verification

Before submitting, confirm (see `flag-identification.md` for full detail):
1. The string matches the competition's known flag format.
2. It was actually derived from *this* challenge's artifact — not a
   leftover from a different challenge's scratch work.
3. You can reproduce it from the original artifact via your recorded
   command sequence, end to end, on a clean copy.
4. The extraction method is defensible — you can explain *why* this string
   is the flag, not just that a regex matched.

## Phase 8 — Documentation

Two required outputs, both described fully in `solved-challenges/README.md`:

1. A markdown write-up following the fixed template (Category, Difficulty,
   Description, Initial Assessment, Approach, Investigation, Solution, Flag
   Identification, Reproduction, Key Insight, Tools Used, Lessons Learned,
   Efficiency Notes, Flag).
2. A plain `.txt` write-up in the exact three-section format: INITIAL
   APPROACH / STEPS OF RECREATION / IDENTIFICATION.

## Knowledge Extraction & Continuous Improvement

After every solve — win or instructive loss — ask: *what part of this is
reusable?* If a technique, tool invocation, or indicator wasn't already
documented, add it to the relevant category playbook (not just the
challenge write-up). This is how the knowledge base compounds in usefulness
across a competition and across future competitions.

Rules for extraction:
- Generalize the technique ("PNG custom chunks can carry arbitrary data —
  check with `exiftool -a -u` or a chunk-walker script") — never the
  challenge-specific secret itself.
- **Never** copy a real flag, a real password, or challenge-specific
  identifying content into a generic playbook doc. Playbooks describe
  method, not the answer to a specific past challenge.
- If a mistake cost significant time, add it to that doc's "Common
  Mistakes" section so it isn't repeated.
- If an entirely new fast path emerges, add it as a new "Technique" entry
  following the standard structure (Technique / When to Use It / Fast Path /
  Tools / Expected Results / Indicators / Failure Conditions / Next Step /
  Common Mistakes).

This keeps the repository accurate to "record fundamentals once, then reuse
them to solve challenges via the shortest reasonable path" — the core
Watson philosophy stated in the root `README.md`.
