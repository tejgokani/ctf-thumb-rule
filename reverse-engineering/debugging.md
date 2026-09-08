# Debugging (GDB-Centric)

## Fastest First-Pass Checklist

```bash
# Purpose: launch under GDB with pwndbg/gef extensions for CTF-friendly context views
gdb ./binary

# Purpose: break at main and run, get an immediate look at args/register state
gdb -q -ex 'b main' -ex run ./binary

# Purpose: disassemble main from within GDB, Intel syntax
gdb -q -batch -ex 'set disassembly-flavor intel' -ex 'disas main' ./binary
```

## Technique: Breakpoint-Driven Value Recovery

**When to use it**: static analysis shows a comparison (`strcmp`,
`memcmp`, a manual byte-loop) against a value that isn't a plain string
(computed, obfuscated, or loaded at runtime) — breakpointing the
comparison and reading memory is almost always faster than deriving the
value by hand from disassembly.

**Fast path**:
```bash
# Purpose: break right at the call to strcmp/memcmp identified in static analysis
gdb -q ./binary
(gdb) b *0x0000555555555239        # address of the call instruction, from objdump/Ghidra
(gdb) run
(gdb) x/s $rsi                     # print the string argument as text (adjust register per calling convention)
(gdb) x/s $rdi
```
**Indicators**: one of the two compared buffers is the secret/expected
value in plaintext at the moment of comparison, even if it was
constructed/decoded at runtime and never exists as a static string.

**Next Step**: capture the value, it's frequently the flag or the input
required to reach the flag branch.

## Technique: Register & Memory Inspection

**Fast path**:
```bash
# Purpose: full register dump at current breakpoint
(gdb) info registers

# Purpose: examine memory at an address as hex bytes / string / instructions
(gdb) x/20xb $rsp
(gdb) x/s $rax
(gdb) x/5i $pc

# Purpose: watch a memory location or register for changes and break automatically
(gdb) watch *0x0000555555558020
```

## Technique: Stepping & Control Flow

**Fast path**:
```bash
# Purpose: step over/into to reach a specific branch (e.g. the "flag correct" path)
(gdb) next     # step over function calls
(gdb) step     # step into function calls
(gdb) continue # run until next breakpoint

# Purpose: force execution down the "success" branch directly (skip failed checks) to see what happens next
(gdb) set $eflags |= (1 << 6)   # example: force ZF for a je to be taken — adjust per actual comparison logic
```
**Indicators**: forcing the success branch reveals a subsequent
print/flag-construction step that's otherwise gated behind a check you
haven't (and don't need to) fully satisfy statically.

## Technique: pwndbg / gef Quality-of-Life

**Fast path**:
```bash
# Purpose: pwndbg auto-prints registers/stack/disassembly context on every stop — install once, use always
git clone https://github.com/pwndbg/pwndbg && cd pwndbg && ./setup.sh

# Purpose: gef alternative, similarly CTF-oriented context printing
bash -c "$(curl -fsSL https://gef.blah.cat/sh)"
```
**Why**: raw GDB requires manually re-running `info registers`/`x/`
commands after every step; pwndbg/gef print full context automatically on
every stop, which is a significant speed multiplier across a whole
debugging session.

## Common Mistakes

- Reading disassembly and manually computing an XOR/comparison result by
  hand instead of just breakpointing and reading the actual runtime value —
  slower and error-prone for anything beyond a trivial single instruction.
- Forgetting `set disassembly-flavor intel` and fighting AT&T syntax out
  of habit.
- Not using pwndbg/gef, and re-typing `info registers`/`x/20xb $rsp` after
  every single step manually.
- Breaking at the wrong address because you copied it from a different
  build/run (ASLR/PIE) — with PIE enabled, compute the runtime address as
  `base + offset` (`info proc mappings` in GDB, or disable ASLR for local
  debugging: `set disable-randomization on`, GDB's default).
