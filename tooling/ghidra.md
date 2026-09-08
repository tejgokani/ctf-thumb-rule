# Ghidra Quick Reference

Purpose: full static-analysis suite with a strong decompiler (pseudo-C/
Java output) and cross-reference tooling — the default heavy-duty choice
for non-trivial binary RE (see `../reverse-engineering/static-analysis.md`
for the full workflow).

## GUI Workflow

1. Create a project, import the binary, run auto-analysis to completion
   (accept defaults unless you have a specific reason to change them).
2. **Window → Defined Strings** — same information as `strings` but with
   clickable cross-references straight to usage sites.
3. **Symbol Tree → Functions** — scan names for anything suspicious
   before reading linearly from `main`.
4. **Decompile window** — read pseudo-C, dramatically faster than reading
   raw disassembly for logic-heavy functions.
5. **Right-click a variable/string → Show References To** — jump directly
   to every usage site instead of scrolling manually.

## Headless Mode (Scripting/Automation)

```bash
# Purpose: run Ghidra's full analysis pipeline without opening the GUI — useful for batch/automated triage
analyzeHeadless /tmp/ghidra_proj CTFProj -import ./binary -postScript DecompileAll.java

# Purpose: run a custom post-analysis script (e.g. to dump every function's decompilation to text)
analyzeHeadless /tmp/ghidra_proj CTFProj -process binary -scriptPath ./scripts -postScript ExtractStrings.java
```

## Indicators & Decisions

- Auto-analysis takes excessively long on a large stripped binary →
  narrow scope first via `strings`/`nm` before letting Ghidra churn on
  the whole binary.
- Decompiler output for a specific function looks wrong/garbled →
  cross-check against the raw disassembly listing, or try radare2's `pdg`
  as a second opinion.

## Alternatives

- `radare2`/`Rizin`/`Cutter` — faster CLI startup, scriptable, good when
  Ghidra's GUI overhead isn't worth it for a quick look (see
  `radare2.md`).
- `objdump`/`nm` — for single-function or single-symbol quick checks
  where full decompilation is unnecessary overhead.

## Common Mistakes

- Reading the raw assembly listing instead of switching to the decompile
  window — pseudo-C reads dramatically faster for anything beyond a
  trivial function.
- Not using cross-references (Show References To) and instead scrolling
  manually to find where a string/constant is used.
- Running full auto-analysis on a huge binary when a targeted approach
  (import just the relevant section, or use headless mode with a specific
  function) would be much faster.
