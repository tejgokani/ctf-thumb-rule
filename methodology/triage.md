# Triage — Decision Trees, Decision Priorities, and Tunnel Vision

This is the consolidated decision-making reference. Category-specific
decision trees live in their own docs and are cross-linked below; this file
holds the trees that span categories plus the meta-rules for how to choose
between competing next steps.

## Consolidated Decision Trees

### Tree 1 — Unknown File

```
Unknown file
│
├─ file <artifact>
│   ├─ Known type recognized (PNG/JPEG/ZIP/PDF/ELF/PE/PCAP/etc.)
│   │     └─> Go to that format's category workflow
│   │         (digital-forensics/common-file-formats.md,
│   │          reverse-engineering/elf.md or pe.md,
│   │          digital-forensics/pcaps.md, etc.)
│   │
│   └─ "data" / unrecognized / no clean magic match
│         └─> strings -n 8, xxd | head/tail, binwalk -e
│             ├─ Recognizable header buried mid-file → extension/type mismatch,
│             │    re-triage as that type (see digital-forensics/file-analysis.md)
│             ├─ binwalk finds embedded archive/image/filesystem → extract, recurse triage
│             └─ High entropy throughout, no structure → likely compressed or
│                  encrypted blob → cryptography/crypto-triage.md
```

### Tree 2 — APK

```
APK
│
├─ file + unzip -l + strings triage
│     └─> apktool d (resources+manifest) AND jadx -d (java source) in parallel
│           ├─ AndroidManifest.xml: exported components, permissions, suspicious
│           │    activity/service/receiver names
│           │        └─> Suspicious component found?
│           │              YES → targeted static read of that component's code
│           │              NO  → continue broad grep sweep
│           ├─ grep -RniE 'flag|secret|key|password|token|http' apk_out jadx_out
│           │        └─> Hit found?
│           │              YES → validate it's not a red herring, extract, verify
│           │              NO  → inspect assets/, res/raw/, res/values/, lib/*.so
│           └─ Still nothing?
│                 └─> Escalate: smali analysis of suspicious method, Frida hook,
│                      dynamic run + logcat, native .so RE (reverse-engineering/apk.md)
```

### Tree 3 — Web App

```
Web app
│
├─ recon (reconnaissance.md): headers, robots.txt, sitemap, framework fingerprint
│     └─> endpoints + params enumerated (view-source, JS bundles, ffuf)
│           └─> authn present?
│                 YES → test authn (weak creds, JWT, session) — authentication.md
│                 NO  → skip to authz / direct object checks
│           └─> authz boundaries identified (roles, IDs in URLs/bodies)?
│                 YES → test IDOR/BOLA — authorization.md
│           └─> input handling: any reflected/stored input, file ops, DB-backed
│                 lookups, template rendering?
│                 └─> map each sink to a vuln class (injection.md, xss.md, ssrf.md,
│                      file-upload.md, path-traversal.md, command-injection.md)
│                       └─> targeted test with minimal payload, confirm, escalate
```

Full versions with commands: `web-security/reconnaissance.md`,
`reverse-engineering/apk.md`, `digital-forensics/file-analysis.md`.

Format-specific trees also exist in: `reverse-engineering/elf.md`,
`reverse-engineering/pe.md`, `digital-forensics/pcaps.md`,
`digital-forensics/memory-forensics.md`, `cryptography/crypto-triage.md`,
`steganography/images.md`, `osint/general-workflow.md`.

## Decision Priorities — Choosing Between Competing Next Steps

When more than one next step looks plausible, rank candidates by, in order:

1. **Likelihood of success** — does the indicator actually point here, or
   is this just "a thing CTF challenges sometimes do"? Prefer steps backed
   by a concrete observed indicator (a string, a header, a byte pattern)
   over steps backed by genre convention alone.
2. **Time required** — a 10-second command that might work beats a
   10-minute setup that might work slightly better. Try cheap first.
3. **Information gained even on failure** — a step that narrows the search
   space (e.g. `file`, entropy check) is worth doing early even if it
   doesn't itself produce the flag, because it informs every later
   decision.
4. **Complexity** — prefer the standard tool invocation over a custom
   script, unless the standard tool has already failed or the task is
   inherently iterative/bulk.
5. **Reliability** — prefer deterministic techniques (parsing, decoding)
   over probabilistic ones (fuzzing, brute force) when both are plausible.
6. **Reproducibility** — prefer techniques you can re-run cleanly and
   explain, since Phase 7/8 require exactly that.

## Avoid Tunnel Vision

The challenge category label is a hint, not a guarantee. Watson's default
posture is mild skepticism toward the label itself:

- **"Crypto" might actually be stego, OSINT, or plain encoding.** A
  "crypto" challenge that hands you an image is very often "decode the
  base64 string hidden via LSB in this image" — i.e., stego + encoding, not
  a cipher to break. Don't start attacking an AES ciphertext until you've
  confirmed via entropy/structure that it *is* ciphertext and not, say,
  base64 of something else, or an image with a payload appended.
- **"RE" might be solved without reversing anything.** Check `strings`,
  environment variables, hardcoded credentials/URLs, and config files
  before opening a disassembler. Many "reverse this binary" challenges have
  the flag sitting in a string table or a debug symbol.
- **"Forensics" might be RE or crypto in disguise.** A "forensics" PCAP
  challenge might just be "here's a TLS-wrapped protocol, and the actual
  puzzle is decrypting an app-layer blob captured inside it" — that's
  crypto once you've extracted the bytes.
- **"OSINT" might require decoding first.** A social media bio or image
  caption that's OSINT-flavored may contain a base64/hex blob that needs
  `cryptography/crypto-triage.md` before the OSINT pivot even makes sense.

**Rule of thumb**: if your first hypothesis fails twice, stop iterating
within the current category and explicitly re-ask "what category is this
*actually*?" before spending more tool-time. Re-triage (Phase 2) rather
than grinding Phase 5 investigations against the wrong theory.
