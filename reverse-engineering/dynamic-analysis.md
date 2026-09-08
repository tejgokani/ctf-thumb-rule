# Dynamic Analysis Deep-Dive — ltrace, strace, Runtime Observation

## Fastest First-Pass Checklist

```bash
# Purpose: trace all library calls (esp. libc functions like strcmp/memcmp/malloc)
ltrace ./binary

# Purpose: trace all syscalls (file/network/process activity)
strace ./binary

# Purpose: both together, with strings expanded and output to file for grepping
strace -f -s 200 -o strace.log ./binary
ltrace -f -s 200 -o ltrace.log ./binary
```

## Technique: Library Call Tracing (ltrace)

**When to use it**: need to see *what* the binary compares/computes
without setting individual breakpoints — ltrace shows every libc call with
its actual argument values.

**Fast path**:
```bash
# Purpose: -s increases the string-argument print length (default truncates aggressively)
ltrace -s 200 ./binary
```
**Indicators**: a `strcmp("user_input", "actual_secret")` line appears
directly in the trace — this is frequently a one-command solve for
"reverse this simple check" style challenges.

**Failure Conditions**: statically linked binary (ltrace can't intercept
calls that aren't going through the dynamic linker/PLT) — falls back to
`strace`-only or GDB breakpointing instead.

## Technique: Syscall Tracing (strace)

**When to use it**: need to see file access, network activity, process
creation/execution — works even on statically linked binaries (syscalls go
through the kernel regardless of linking).

**Fast path**:
```bash
# Purpose: follow forked children too, expand strings, log to file
strace -f -s 200 -o strace.log ./binary

# Purpose: filter to just file operations
strace -e trace=open,openat,read,write ./binary

# Purpose: filter to just network operations
strace -e trace=network ./binary
```
**Indicators**: `openat` on an unexpected path (a hidden config/flag file
the binary reads); `connect`/`sendto` revealing a hardcoded
host:port (cross-reference with any PCAP artifact); `execve` revealing a
subprocess/shell command constructed at runtime.

## Technique: Runtime Value Extraction via /proc

**When to use it**: need to inspect a running process's memory/state
without a full debugger session.

**Fast path**:
```bash
# Purpose: dump a running process's memory maps
cat /proc/<pid>/maps

# Purpose: dump the actual memory of a mapped region (requires appropriate permissions)
dd if=/proc/<pid>/mem bs=1 skip=<start_addr> count=<size> of=dump.bin
```
**Next Step**: `strings`/analyze `dump.bin` as its own artifact.

## Technique: Environment & Argument Fuzzing

**When to use it**: static analysis shows the binary reads
`getenv`/`argv` values that gate behavior.

**Fast path**:
```bash
# Purpose: try common env vars the binary might check
FLAG=test ./binary
DEBUG=1 ./binary

# Purpose: try providing an argument the binary's usage/help text implies it expects
./binary $(python3 -c "print('A'*40)")
```

## Common Mistakes

- Using `ltrace` on a statically-linked binary and getting no useful
  output, then giving up instead of falling back to `strace`/GDB.
- Not increasing string length (`-s`) and missing the interesting part of
  a truncated argument in the trace output.
- Running dynamic analysis on a binary without first confirming (via
  `file`) that it matches the host architecture — silent failures or
  crashes otherwise.
- Ignoring `strace`'s syscall output for `execve` calls that reveal a
  dynamically-constructed command — a very common CTF pattern for hiding a
  flag-printing shell command behind an innocuous-looking main binary.
