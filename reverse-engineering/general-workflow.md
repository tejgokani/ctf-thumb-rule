# Reverse Engineering — General Workflow

## Static vs Dynamic Analysis

**Static analysis**: examining the binary without executing it — file
type, strings, symbols, sections, imports/exports, architecture,
disassembly, decompilation, control-flow graph, cross-references,
suspicious function identification. Always the first move — cheap, safe,
no risk of a booby-trapped binary doing something unwanted.

**Dynamic analysis**: executing the binary (in a sandboxed/controlled
environment) and observing behavior — debugger breakpoints, register/memory
state, syscalls, file activity, network activity, runtime-computed values
(e.g. a key generated at runtime that never appears as a static string).
Use when static analysis can't reveal a value because it's computed, not
stored — or to confirm a static hypothesis quickly by just running it.

## Fastest First-Pass Checklist (Any Binary)

```bash
# Purpose: identify format/arch/bitness before choosing tools
file ./binary

# Purpose: printable strings, longer minimum length cuts noise
strings -n 8 ./binary | less

# Purpose: check for obvious flag-shaped or hint strings immediately
strings ./binary | grep -iE 'flag|ctf|wtf\{|password|key'

# Purpose: symbol table — is this stripped? what functions/names remain?
nm ./binary 2>/dev/null || echo "stripped or non-ELF"

# Purpose: does it actually run, and what does it print?
./binary --help 2>&1 | head
```

## Format-Specific Docs

- ELF (Linux binaries) → `elf.md`
- PE (Windows binaries) → `pe.md`
- APK (Android) → `apk.md`
- Debugging workflows (GDB) → `debugging.md`
- Static analysis deep-dive (Ghidra/radare2/objdump) → `static-analysis.md`
- Dynamic analysis deep-dive (ltrace/strace/runtime) → `dynamic-analysis.md`

## Tool-Choice Decision Tree

```
Binary to analyze
│
├─ Need quick strings/symbols/imports only?
│     └─> objdump / nm / strings — fastest, no GUI overhead
│
├─ Need full decompilation to (pseudo-)C with cross-references,
│    and time budget allows GUI tooling?
│     └─> Ghidra — best free decompiler, handles most architectures
│
├─ Need fast CLI-driven disassembly/scripting/patching, or Ghidra
│    unavailable/too slow to spin up?
│     └─> radare2 (or Rizin/Cutter for GUI) — scriptable, fast startup
│
├─ Need to observe runtime behavior (values, branches actually taken)?
│     └─> GDB (with pwndbg/gef for CTF-friendly extensions) — debugging.md
│
├─ Need syscall/library-call trace without a full debugger session?
│     └─> strace (syscalls) / ltrace (library calls) — dynamic-analysis.md
│
└─ Packed/obfuscated binary that resists static analysis?
      └─> Dynamic unpacking: run under a debugger, breakpoint on entry,
           dump memory once unpacked (find OEP), then re-run static
           analysis on the dumped/unpacked image
```

## Packed / Obfuscated Binaries

**Indicators**: `file` shows very few strings for the binary's size, high
entropy in `.text`-equivalent sections, unusual/absent section names, a
tiny number of imports (packer stubs resolve imports at runtime), or a
known packer signature (`UPX!` string is a dead giveaway for UPX).

**Fast path**:
```bash
# Purpose: check for UPX and similar common packers first — often trivially reversible
strings ./binary | grep -i upx
upx -d ./binary -o ./binary_unpacked   # if UPX-packed

# Purpose: entropy scan to confirm packing when no packer string is found
binwalk -E ./binary
```
**Next Step**: if not a known packer, dynamic unpacking (run under GDB,
break after the unpacking stub finishes, dump memory) — see
`dynamic-analysis.md`.

## Common Mistakes (RE, general)

- Jumping straight to a disassembler/decompiler before running `strings` —
  many CTF binaries hand you the flag (or the key to derive it) in plain
  strings.
- Ignoring environment variables and command-line arguments the binary
  reads — `strace ./binary` or `strings` for `getenv`/`argv` usage patterns
  quickly reveals what input format is expected.
- Not checking whether the binary is even the right architecture to run
  locally — cross-arch binaries (ARM binary, x86 host) need `qemu-*-static`
  or emulation, not native execution.
- Spending disassembler time on a function whose name/xrefs suggest it's
  irrelevant (e.g. logging/init code) before confirming the "interesting"
  function via strings/symbol names/xrefs to `main`.
