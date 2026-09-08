# Linux Quick Reference

Environment assumption: a standard security-distro toolchain (Kali/
ParrotOS) or an equivalent minimal Linux box with these installed. Don't
over-engineer — reach for the built-in coreutils command before installing
a heavier tool when one will do.

```bash
# Purpose: identify file type by magic bytes, always the first command on any artifact
file ./target

# Purpose: hex dump for manual byte-level inspection
xxd ./target | less

# Purpose: hash for evidence/dedup tracking
sha256sum ./target

# Purpose: find files by name pattern recursively
find . -iname '*flag*'

# Purpose: find files by content match recursively (fast, respects binary detection)
grep -RniE 'flag\{' .

# Purpose: process/network state on a live system under investigation
ps aux
ss -tulpn

# Purpose: permissions/ownership anomalies (SUID binaries are a common privesc/CTF vector)
find / -perm -4000 -type f 2>/dev/null

# Purpose: decompress common archive formats without remembering per-format syntax
tar xf archive.tar.gz     # tar handles .tar/.tar.gz/.tar.bz2/.tar.xz via auto-detection
unzip archive.zip
7z x archive.7z

# Purpose: watch a file/directory for changes in real time (useful for dynamic-analysis artifacts)
watch -n 1 'ls -la /tmp/dropped_files/'
```

## Alternatives Table

| Task | Primary | Alternative |
|---|---|---|
| Recursive text search | `grep -R` | `ripgrep` (`rg`, much faster on large trees) |
| Archive extraction | `tar`/`unzip` | `7z x` (handles more formats uniformly) |
| Process inspection | `ps aux` | `htop`, `/proc/<pid>/` directly |
| Network state | `ss` | `netstat` (older, still common) |

## Common Mistakes

- Reaching for a heavy Python script when a one-line coreutils pipeline
  does the same job faster to write and run.
- Forgetting `2>/dev/null` on `find` commands over `/` — permission-denied
  noise buries real results.
- Not checking SUID/SGID binaries on a provided VM/container image when
  the challenge implies privilege escalation.
