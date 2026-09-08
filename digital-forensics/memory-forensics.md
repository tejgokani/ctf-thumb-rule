# Memory Forensics

## Fastest First-Pass Checklist (Memory Image)

```bash
# Purpose: identify the OS profile Volatility3 needs (auto-detects in vol3, explicit imageinfo in vol2)
vol.py -f memdump.raw windows.info

# Purpose: list running processes at time of capture
vol.py -f memdump.raw windows.pslist

# Purpose: list network connections at time of capture
vol.py -f memdump.raw windows.netscan

# Purpose: dump command history (cmd.exe / powershell)
vol.py -f memdump.raw windows.cmdline
```
(Use `linux.*` / `mac.*` plugin namespaces instead of `windows.*` for those
OS families — Volatility3 uses symbol tables to detect the right profile
automatically.)

## Technique: Image Identification

**When to use it**: first, always — before running any OS-specific plugin.

**Fast path**:
```bash
# Purpose: file(1) often identifies raw memory dumps, LiME format, or hibernation files
file memdump.raw

# Purpose: Volatility3 self-detects OS/profile from the image, no manual profile needed
vol.py -f memdump.raw windows.info
```
**Indicators**: `windows.info`/`linux.info` succeed and print a kernel
version/build → correct plugin family. Failure/empty output → try the other
OS family, or check for a non-standard capture format needing conversion
(e.g. VMware `.vmem`, VirtualBox `.sav`).

## Technique: Process Enumeration

**Fast path**:
```bash
# Purpose: list processes (parent/child relationships, PID, creation time)
vol.py -f memdump.raw windows.pstree

# Purpose: cross-check against pslist — processes hidden from pstree but present in pslist = rootkit indicator
vol.py -f memdump.raw windows.pslist
```
**Indicators**: an unexpected process name, a process with no legitimate
parent, a process running from a suspicious path (`\Users\...\Temp\`,
`/tmp/`), or a process present in one listing but not another
(cross-view discrepancy = hiding/injection).

**Next Step**: dump the suspicious process's memory for further analysis:
```bash
# Purpose: dump a specific process's memory region to disk for offline strings/RE
vol.py -f memdump.raw -o out/ windows.memmap --pid <PID> --dump
```

## Technique: Network Connections

**Fast path**:
```bash
# Purpose: active/historical connections, associated PID/process
vol.py -f memdump.raw windows.netscan
```
**Indicators**: a connection to an unusual port or the connection whose
remote address/port is referenced elsewhere in the challenge (e.g. matches
a PCAP artifact from the same challenge set).

## Technique: DLLs / Loaded Modules

**Fast path**:
```bash
# Purpose: list DLLs loaded by a process — unusual DLL path = injection candidate
vol.py -f memdump.raw windows.dlllist --pid <PID>

# Purpose: detect DLL/code injection via VAD (Virtual Address Descriptor) permission anomalies
vol.py -f memdump.raw windows.malfind
```
**Indicators**: `malfind` flags a memory region with `PAGE_EXECUTE_READWRITE`
permissions and no backing file — classic injected-shellcode signature.

**Next Step**: dump the flagged region and disassemble/strings it:
```bash
# Purpose: extract the malfind-flagged region's raw bytes for follow-up static analysis
vol.py -f memdump.raw -o out/ windows.malfind --pid <PID> --dump
```

## Technique: Command History & Credentials/Tokens

**Fast path**:
```bash
# Purpose: recover typed command-line history from console buffers
vol.py -f memdump.raw windows.cmdline

# Purpose: recover hashes from SAM (needs SYSTEM + SAM hives, extracted via registry plugins below)
vol.py -f memdump.raw windows.hashdump

# Purpose: search decompressed memory for cached credentials / tokens generically
strings memdump.raw | grep -iE 'password|token|api[_-]?key|bearer|BEGIN.*PRIVATE KEY'
```
**Indicators**: plaintext credentials in a console buffer or clipboard
plugin (`windows.clipboard`); a Bearer token or private key string directly
recoverable via `strings`.

## Technique: Registry Artifacts

**Fast path**:
```bash
# Purpose: list registry hives present in the image
vol.py -f memdump.raw windows.registry.hivelist

# Purpose: print a specific key's values (e.g. Run keys for persistence, or a custom key holding a flag)
vol.py -f memdump.raw windows.registry.printkey --key "Software\Microsoft\Windows\CurrentVersion\Run"
```
**Indicators**: persistence entries in `Run`/`RunOnce` pointing at a
suspicious binary/script — cross-reference with `pslist`/`filescan`.

## Common Mistakes

- Trying to manually specify a Volatility2-style `--profile` with
  Volatility3 (vol3 doesn't need it — let it self-detect).
- Not dumping flagged process/region memory before it's needed — dump early
  once `malfind`/suspicious process identified, don't re-run the whole
  pipeline later.
- Ignoring `strings` on the raw image as a cheap parallel path — sometimes
  faster than the full Volatility plugin chain for a simple "find the flag
  in memory" challenge.
