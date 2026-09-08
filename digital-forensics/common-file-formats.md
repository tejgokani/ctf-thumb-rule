# Common File Formats — ZIP, PDF, Git Repositories

## ZIP / Archive Analysis

### Fastest First-Pass Checklist

```bash
# Purpose: list contents + compression method + sizes without extracting
unzip -l archive.zip

# Purpose: verify archive integrity and detect encryption per-entry
unzip -v archive.zip

# Purpose: check for and read any archive comment field (often holds a hint/flag)
python3 -c "import zipfile; z=zipfile.ZipFile('archive.zip'); print(z.comment)"
```

### Technique: Metadata, Listing, Extraction

**Fast path**:
```bash
# Purpose: extract everything to an isolated directory
unzip archive.zip -d extracted/

# Purpose: extract a single entry of interest without touching the rest
unzip archive.zip 'path/inside/archive.txt' -d extracted/
```
**Indicators**: entry list includes a file that looks out of place (a
`.txt` next to images, a nested archive, a `.git/` directory).

**Next Step**: `file`/`strings`/re-triage every extracted item.

### Technique: Nested Archives

**Indicators**: an extracted item is itself a ZIP/RAR/7z/tar.

**Fast path**:
```bash
# Purpose: recursively find and list any archive-within-archive
find extracted/ -type f -exec file {} \; | grep -i archive
```
**Next Step**: repeat extraction on each nested archive; track depth in
your evidence log so the chain is reproducible.

### Technique: Password-Protected Archives

**Fast path**:
```bash
# Purpose: confirm encryption and identify hash format for cracking tools
zip2john archive.zip > archive.hash

# Purpose: dictionary attack with a standard wordlist
john --wordlist=/usr/share/wordlists/rockyou.txt archive.hash

# Purpose: brute-force small/known-pattern passwords directly (faster for short numeric/known-format passwords)
fcrack zip -u -D -p /usr/share/wordlists/rockyou.txt archive.zip
```
**Indicators**: `unzip` prompts for a password or errors with "encrypted."
Check the challenge text/other artifacts first — the password is often
provided elsewhere (another file, the challenge description, a previous
challenge's flag) before resorting to cracking.

**Common Mistakes**: jumping straight to brute force before checking
whether the password was already handed to you in-challenge.

### Technique: Archive Comments

**Indicators**: `zipinfo -z archive.zip` or the Python snippet above returns
non-empty comment text — ZIP comments are an easy, commonly-used hiding
spot since most people never check them.

## PDF Analysis

### Fastest First-Pass Checklist

```bash
# Purpose: metadata (author, creator tool, dates)
exiftool document.pdf

# Purpose: printable strings — quick win for unencoded flags/hints
strings document.pdf | less

# Purpose: enumerate PDF internal objects/structure
pdfinfo document.pdf
pdf-parser.py document.pdf   # from didier stevens' pdf-tools
```

### Technique: Object & Stream Inspection

**Fast path**:
```bash
# Purpose: list every object with its type, flag suspicious ones (JS, embedded files, launch actions)
pdf-parser.py --stats document.pdf

# Purpose: search for and dump JavaScript objects specifically
pdf-parser.py -s javascript document.pdf

# Purpose: decompress a specific FlateDecode stream object for inspection
pdf-parser.py -o <object_number> -f document.pdf
```
**Indicators**: `/JS` or `/JavaScript` objects present (uncommon in benign
PDFs, common in RE-flavored forensics challenges); `/EmbeddedFile` objects;
an object stream that decompresses to a flag-shaped string.

**Next Step**: for embedded files:
```bash
# Purpose: extract all embedded/attached files from a PDF
pdfdetach -saveall document.pdf
```

### Technique: Decompression of Content Streams

Most PDF content streams are `FlateDecode` (zlib). If manual extraction is
needed beyond `pdf-parser.py -f`:
```python
# Purpose: manually zlib-inflate a raw stream you've extracted between "stream"/"endstream" markers
import zlib
data = open("raw_stream.bin","rb").read()
print(zlib.decompress(data))
```

### Common Mistakes

- Not checking for JavaScript/embedded files — treating a PDF as "just
  text" when the flag lives in an attachment or a JS variable.
- Running `strings` on a PDF and expecting to see the visible page text —
  page text is usually inside compressed streams and won't show in raw
  `strings` output; use `pdf-parser.py -f` or `pdftotext` instead.
```bash
# Purpose: extract the human-visible rendered text (post-decompression)
pdftotext document.pdf - 
```

## Git Repository Analysis

### Fastest First-Pass Checklist

```bash
# Purpose: full commit history, one line per commit
git log --all --oneline

# Purpose: every branch, including ones not currently checked out
git branch -a

# Purpose: search full history (all commits, all branches) for secrets/flags
git log --all -p | grep -iE 'flag|secret|key|password|token'
```

### Technique: History & Deleted File Recovery

**When to use it**: a provided `.git` directory or a repo where "look at
the history" is implied.

**Fast path**:
```bash
# Purpose: find commits that deleted files — often where secrets get "removed" but remain in history
git log --diff-filter=D --summary --all

# Purpose: show a deleted file's last content before removal
git show <commit>^:<path/to/deleted_file>

# Purpose: search every blob ever committed for a pattern, independent of current tree state
git rev-list --all | xargs -I{} git grep -l 'flag{' {}
```
**Indicators**: a commit message like "remove secret key" or "oops"; a file
present in an old commit but absent from `HEAD`.

### Technique: Branches & Dangling Commits

**Fast path**:
```bash
# Purpose: find commits not reachable from any branch/tag (e.g. reset --hard leftovers, still in .git/objects)
git fsck --unreachable --no-reflog

# Purpose: inspect a dangling commit directly by hash
git show <dangling_commit_hash>

# Purpose: reflog can reveal branch/commit history even after local resets
git reflog show --all
```
**Indicators**: `git fsck` reports unreachable commits/blobs — a very
common CTF Git-forensics trick (author committed the flag, then `git reset
--hard` + force-pushed a "clean" history, but the object is still on disk).

### Technique: Config, Hooks, and Stash

**Fast path**:
```bash
# Purpose: repo config sometimes contains credentials or a remote URL hint
cat .git/config

# Purpose: hooks can contain scripts with hardcoded secrets
ls -la .git/hooks/ && cat .git/hooks/*

# Purpose: stashed changes are easy to forget and easy to overlook
git stash list && git stash show -p stash@{0}
```

### Common Mistakes

- Only checking `git log` on the current branch — always add `--all`.
- Assuming a deleted-and-committed file is gone — it lives in `.git/objects`
  until garbage collected, which CTF repos rarely are.
- Forgetting `git fsck --unreachable` for the dangling-commit case, which is
  a very common "hidden flag" pattern in Git-forensics challenges.
