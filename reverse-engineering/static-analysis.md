# Static Analysis Deep-Dive — Ghidra & radare2

## Fastest First-Pass Checklist

```bash
# Purpose: headless Ghidra analysis + auto-decompile every function to a text dump, no GUI needed
analyzeHeadless /tmp/ghidra_proj CTFProj -import ./binary -postScript DecompileAll.java

# Purpose: radare2 quick analysis + list of interesting functions
r2 -A -q -c 'afl' ./binary
```

## Ghidra Workflow

**When to use it**: need full decompilation to pseudo-C with
cross-references — best default choice when GUI time is available and the
binary is non-trivial.

**Fast path**:
1. Import binary, let auto-analysis run to completion.
2. Window → Defined Strings — same value as `strings` but clickable with
   xrefs directly to where each string is used.
3. Symbol Tree → Functions — look for suspicious names first
   (`check_flag`, `validate`, `decrypt`); if stripped, look at `main` and
   follow calls outward.
4. Decompile window (press the toggle) reads far faster than the assembly
   listing for logic-heavy functions.
5. Right-click any variable/string → "Show References To" to jump straight
   to usage sites instead of scrolling.

**Headless mode** (for scripting/automation, e.g. bulk-analyzing many
binaries or extracting all strings+xrefs to a file):
```bash
# Purpose: run Ghidra's analysis pipeline without the GUI, useful for automation/CI-style triage
analyzeHeadless /tmp/proj CTF -import ./binary -scriptPath ./scripts -postScript ExtractStrings.java
```

**Indicators to look for in the decompiler view**: string comparisons
against short constants, XOR/rotate loops over an input buffer, calls to
`system`/`popen`/`exec*`, suspicious magic numbers matching known crypto
constants (e.g. AES S-box, MD5/SHA initialization constants).

## radare2 Workflow

**When to use it**: need fast CLI-driven analysis/scripting, or Ghidra is
too slow/unavailable for a quick look.

**Fast path**:
```bash
# Purpose: open with full auto-analysis (-A), quiet mode
r2 -A -q ./binary

# Inside r2:
afl                   # Purpose: list all analyzed functions
s sym.main            # Purpose: seek to main
pdf                   # Purpose: disassemble ("print disassemble function") current function
pdg                   # Purpose: decompile current function (r2dec/r2ghidra plugin) to pseudo-C
axt <addr>            # Purpose: cross-references TO an address (who calls/reads this?)
iz                    # Purpose: strings in data sections
izz                   # Purpose: strings across the ENTIRE file, including sections iz misses
```
**Indicators**: `izz` finding strings `iz` doesn't is common when data is
outside standard string-bearing sections — always run both.

**Scripting example**:
```bash
# Purpose: batch-extract every function's disassembly to a single text file for offline grepping
r2 -A -q -c 'afl~[0]' ./binary | while read addr; do r2 -A -q -c "s $addr; pdf" ./binary; done > all_funcs.txt
```

## objdump / nm — Fast CLI-Only Alternative

When neither Ghidra nor radare2 is warranted (a single function, a quick
sanity check):
```bash
# Purpose: disassemble one named function only
objdump -d --disassemble=check_flag -M intel ./binary

# Purpose: list all symbols with type/binding info
nm -CD ./binary   # -C demangles C++ names
```

## Common Mistakes

- Letting Ghidra's auto-analysis run on a huge binary without narrowing
  scope first (e.g. via strings/symbol names) — analysis time balloons on
  large stripped binaries; target specific functions when possible.
- Reading the assembly listing instead of the decompiler view for
  logic-heavy code — pseudo-C is dramatically faster to read.
- Forgetting `izz` in radare2 (`iz` alone misses strings outside standard
  sections) — same principle as `strings -a` vs default `strings`.
- Not cross-referencing (`axt` in r2, "Show References To" in Ghidra) and
  instead scrolling manually to find where a string/constant is used.
