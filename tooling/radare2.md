# radare2 Quick Reference

Purpose: fast, scriptable, CLI-driven disassembly/analysis — the default
choice when Ghidra's GUI overhead isn't worth it, or for quick scripted
sweeps across many binaries (see `../reverse-engineering/static-analysis.md`
for the full workflow).

```bash
# Purpose: open with full auto-analysis, quiet mode
r2 -A -q ./binary
```

## Inside r2 (interactive commands)

```
afl                   # Purpose: list all analyzed functions
s sym.main            # Purpose: seek to a symbol (main)
pdf                   # Purpose: disassemble ("print disassemble function") current function
pdg                   # Purpose: decompile current function to pseudo-C (r2dec/r2ghidra plugin)
axt <addr>            # Purpose: cross-references TO an address — who calls/reads this?
axf <addr>            # Purpose: cross-references FROM an address — what does this call/read?
iz                    # Purpose: strings in recognized data sections
izz                   # Purpose: strings across the ENTIRE file (catches more than iz)
ii                    # Purpose: imported symbols
ie                    # Purpose: exported symbols/entry points
db <addr>             # Purpose: set a breakpoint (for r2's built-in debugger, `r2 -d`)
dc                    # Purpose: continue execution (debug mode)
```

## Scripted Usage (Non-Interactive)

```bash
# Purpose: run a sequence of r2 commands non-interactively, useful for automation
r2 -A -q -c 'afl; iz; izz' ./binary

# Purpose: extract disassembly of every function to a single file for offline grepping
r2 -A -q -c 'afl~[0]' ./binary | while read addr; do r2 -A -q -c "s $addr; pdf" ./binary; done > all_funcs.txt
```

## Indicators & Decisions

- `izz` finds strings `iz` doesn't → data outside standard string-bearing
  sections; always run both.
- `pdg` (decompile) available and clean → prefer reading it over raw `pdf`
  disassembly for logic-heavy functions.

## Alternatives

- Ghidra — better decompiler quality for complex/large binaries, at the
  cost of GUI startup time (`ghidra.md`).
- Cutter/Rizin — GUI frontends built on the same (Rizin-forked) engine,
  for when interactive visual navigation is preferred over pure CLI.

## Common Mistakes

- Only running `iz` and missing strings that `izz` would have caught.
- Not using cross-references (`axt`/`axf`) and instead manually
  scrolling/searching for usage sites.
- Forgetting `-A` (auto-analysis) on open, leaving function boundaries
  undetected and `afl` empty.
