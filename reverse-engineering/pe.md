# PE Analysis (Windows Binaries)

## Fastest First-Pass Checklist (PE)

```bash
# Purpose: confirm PE, architecture, subsystem
file ./binary.exe

# Purpose: strings sweep (works fine on PE from Linux)
strings -n 8 ./binary.exe | grep -iE 'flag|wtf\{|password|key'

# Purpose: PE header details — sections, imports, compile timestamp
objdump -x ./binary.exe | less

# Purpose: dedicated PE inspection tool (from pev toolkit) if available
peframe ./binary.exe
```

## Technique: Header & Section Inspection

**Fast path**:
```bash
# Purpose: full header dump — machine type, timestamp, sections, characteristics
objdump -x ./binary.exe

# Purpose: import table — DLLs/functions the binary depends on, reveals intended behavior
objdump -p ./binary.exe | grep -A 200 'DLL Name'
```
**Indicators**: imports from `wininet.dll`/`winhttp.dll`/`ws2_32.dll` →
network activity likely, cross-check with any provided PCAP; imports from
`advapi32.dll` (`Crypt*` functions) → cryptography in play; imports from
`kernel32.dll` (`VirtualAlloc`, `WriteProcessMemory`, `CreateRemoteThread`)
→ possible process injection/packing behavior.

**Next Step**: unusual/very short import table + high section entropy →
treat as packed, see the packing section in `general-workflow.md`.

## Technique: Resources

**Fast path**: PE resources (icons, strings, embedded files, version info)
can hide data directly.
```bash
# Purpose: list embedded resources
python3 -c "
import pefile
pe = pefile.PE('binary.exe')
pe.parse_data_directories()
for entry in pe.DIRECTORY_ENTRY_RESOURCE.entries:
    print(entry.name, entry.id)
"
```
**Indicators**: an unusual resource type/name, or a `RCDATA` resource with
suspicious size — extract and treat as its own artifact per
`../digital-forensics/file-analysis.md`.

## Technique: Disassembly / Decompilation

Prefer Ghidra for PE — its Windows API type/signature knowledge
(`WinAPI` calling conventions, structure recovery) is considerably better
than manual `objdump` reading for anything beyond a trivial binary. Full
workflow in `static-analysis.md`.

Quick CLI alternative:
```bash
# Purpose: quick disassembly without a GUI session, useful for a single small function
objdump -d -M intel ./binary.exe | less
```

## Technique: Dynamic Analysis (Windows binary, non-Windows host)

If no Windows/VM environment is available:
```bash
# Purpose: run a Windows PE under Wine for basic dynamic behavior observation
wine ./binary.exe

# Purpose: trace Win32 API calls under Wine (rough equivalent of strace/ltrace)
WINEDEBUG=+relay wine ./binary.exe 2>&1 | less
```
**Common Mistakes**: assuming Wine behaves identically to real Windows —
anti-debug/anti-VM checks in the binary may behave differently or the
binary may simply fail to run; if behavior looks wrong, note it and fall
back to static analysis or a real Windows sandbox/VM if the competition
provides one.

## Common Mistakes (PE, general)

- Ignoring the PE compile timestamp (`objdump -x` reports it) — sometimes
  a deliberate hint or corroborates a timeline from another artifact.
- Not checking resources/version info — a surprisingly common hiding spot,
  since most people only look at code sections.
- Trying to run a 32-bit-only PE without the right Wine/emulation
  architecture configured.
