# ELF Analysis (Linux Binaries)

## Fastest First-Pass Checklist (ELF)

```bash
# Purpose: confirm ELF, architecture, bitness, static/dynamic linking, stripped status
file ./binary

# Purpose: strings sweep for immediate wins
strings -n 8 ./binary | grep -iE 'flag|wtf\{|password|key|/bin/sh'

# Purpose: security mitigations at a glance (canary, NX, PIE, RELRO)
checksec --file=./binary

# Purpose: symbol table (empty/error if stripped)
nm ./binary

# Purpose: dynamic symbols even when stripped of static symbols
nm -D ./binary

# Purpose: imported/exported functions — what libc calls does it make?
objdump -T ./binary
```

## Technique: File Identification

**Fast path**: `file ./binary` — reports ELF class (32/64-bit), endianness,
architecture (x86-64, ARM, MIPS...), static vs dynamically linked, and
stripped status directly.

**Decision**: architecture ≠ host architecture → plan to run under QEMU
user-mode emulation (`qemu-x86_64-static`, `qemu-arm-static`) rather than
natively, if dynamic analysis becomes necessary.

## Technique: Strings

Already covered in the checklist above — always run before disassembly.
Grep for both obvious flag patterns and functionally interesting calls:
```bash
# Purpose: look for interesting libc function names embedded as strings/related to crypto
strings ./binary | grep -iE 'md5|sha|aes|xor|base64|rand|srand'
```

## Technique: Symbols, Sections, Protections

**Fast path**:
```bash
# Purpose: full section header listing — sizes and flags per section
readelf -S ./binary

# Purpose: program headers — segments loaded at runtime
readelf -l ./binary

# Purpose: dynamic section — shared library dependencies
readelf -d ./binary

# Purpose: security mitigation summary (best single command for this)
checksec --file=./binary
```
**Indicators**: no `Canary found` → stack-smashing challenge candidate; `No
PIE` → addresses are fixed, easier to reason about/exploit; `NX disabled`
→ shellcode-on-stack is viable if this is an exploitation-style RE
challenge.

**Next Step**: if this looks like a binary-exploitation challenge (not pure
"find the flag" RE), cross-reference with `debugging.md` and consider
whether it's actually a pwn-style challenge requiring `pwntools`.

## Technique: Disassembly & Decompilation

**Fast path (quick, no GUI)**:
```bash
# Purpose: disassemble just main() and immediate neighbors for a quick read
objdump -d ./binary --disassemble=main -M intel

# Purpose: full disassembly, Intel syntax (much more readable than AT&T for most people)
objdump -d -M intel ./binary | less
```
**Escalation (full decompilation)**: Ghidra or radare2 — see
`static-analysis.md` for the detailed workflow (headless Ghidra scripting,
r2 command sequences, xref-driven navigation).

**Indicators to look for while reading disassembly/decompiled output**:
calls to `strcmp`/`memcmp` (classic "compare input to secret" pattern),
XOR loops (custom encoding of the flag), calls to `system`/`exec*`
(command injection or a hidden shell), string comparisons against
short/suspicious constants.

## Technique: Dynamic Confirmation

**Fast path**:
```bash
# Purpose: just run it and see what it wants/does — cheapest possible dynamic step
./binary
echo "test_input" | ./binary

# Purpose: trace library calls to see what comparison/crypto functions actually get invoked
ltrace ./binary

# Purpose: trace syscalls (file access, network, exec) for behavioral confirmation
strace ./binary
```
See `debugging.md` and `dynamic-analysis.md` for breakpoint-driven deeper
work (dumping the compared secret straight out of a register/memory at the
`strcmp` call, for example — usually faster than reasoning about the
disassembly by hand).

## Common Mistakes

- Skipping `checksec` and missing that stack canary / PIE state changes
  which technique (pure static reasoning vs needing a debugger) is fastest.
- Disassembling in AT&T syntax out of habit when Intel (`-M intel`) is more
  readable for most people and directly matches Ghidra/IDA conventions.
- Not checking `nm -D` (dynamic symbols) when `nm` alone reports "no
  symbols" — many "stripped" binaries still expose dynamic symbols.
- Manually working out an XOR/comparison by reading assembly instead of
  just breakpointing the comparison and reading the value out of memory —
  dynamic confirmation is often faster than static derivation.
