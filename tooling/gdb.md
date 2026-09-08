# GDB Quick Reference

Purpose: dynamic debugging — breakpoints, register/memory inspection,
stepping — the primary tool for confirming runtime values that static
analysis can't directly reveal (see `../reverse-engineering/debugging.md`
for the full workflow).

```bash
# Purpose: launch under GDB
gdb ./binary

# Purpose: launch, set a breakpoint, and run in one line (good for scripted/quick sessions)
gdb -q -ex 'b main' -ex run ./binary

# Purpose: use Intel disassembly syntax (much more readable than AT&T for most people)
gdb -q -ex 'set disassembly-flavor intel' ./binary
```

## Inside GDB (interactive commands)

```
b *0xADDRESS           # Purpose: breakpoint at a specific address
b function_name         # Purpose: breakpoint at a named function
run                      # Purpose: start execution
continue / c             # Purpose: resume after a breakpoint
next / n                 # Purpose: step over (don't enter called functions)
step / s                 # Purpose: step into called functions
info registers           # Purpose: dump all register values
x/20xb $rsp               # Purpose: examine memory as hex bytes
x/s $rax                  # Purpose: examine memory as a null-terminated string
disas function_name       # Purpose: disassemble a function
watch *0xADDRESS           # Purpose: break automatically when this memory location changes
set disable-randomization on   # Purpose: disable ASLR for reproducible addresses across runs
```

## Indicators & Decisions

- A `strcmp`/`memcmp` call identified via static analysis → breakpoint
  right at the call, read both string arguments with `x/s` — usually
  faster than deriving the compared value by hand.
- PIE-enabled binary, addresses shift between runs → compute runtime
  address as `base + offset` (`info proc mappings`), or disable ASLR
  locally for a stable debugging session.

## Alternatives

- pwndbg / gef — extensions that auto-print full context (registers,
  stack, disassembly, useful annotations) on every stop, eliminating
  repeated manual `info registers`/`x/` calls. Strongly recommended for
  any CTF debugging session beyond a trivial one-breakpoint check.
- `strace`/`ltrace` — when only syscall/library-call visibility is
  needed, without full breakpoint-driven debugging (`dynamic-analysis.md`).

## Common Mistakes

- Not setting `disassembly-flavor intel` and fighting AT&T syntax out of
  habit.
- Manually re-running `info registers`/`x/20xb $rsp` after every step
  instead of installing pwndbg/gef, which prints this automatically.
- Breakpointing at an address copied from a different run/build without
  accounting for ASLR/PIE offset changes.
