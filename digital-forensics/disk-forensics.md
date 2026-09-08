# Disk / Image Forensics

## Fastest First-Pass Checklist (Disk Image)

```bash
# Purpose: identify image type/format (raw dd, E01, VMDK, etc.)
file ./disk.img

# Purpose: enumerate partitions/volume layout without mounting
mmls ./disk.img

# Purpose: identify filesystem type per partition
fsstat -o <sector_offset> ./disk.img

# Purpose: list files including deleted ones, with timestamps
fls -o <sector_offset> -r -p ./disk.img
```

## Technique: Partition Discovery & Filesystem ID

**When to use it**: any raw/`.img`/`.dd`/`.E01` disk image artifact.

**Tools**: The Sleuth Kit (`mmls`, `fsstat`, `fls`, `icat`, `istat`),
`fdisk -l`, `parted`.

**Fast path**:
```bash
# Purpose: mmls shows partition table with starting sector offsets
mmls disk.img
# Purpose: multiply the reported start sector by sector size (usually 512) for mount offset
# e.g. start=2048 -> offset=2048*512=1048576
```

**Expected Results**: a partition table listing offsets, sizes, and
descriptions (NTFS, FAT32, ext4, HFS+, etc.).

**Next Step**: mount read-only for casual browsing, or use Sleuth Kit
directly for evidence-safe access without mounting:
```bash
# Purpose: read-only, offset-aware mount so nothing on the image is modified
sudo mount -o ro,loop,offset=1048576 disk.img /mnt/evidence
```

## Technique: Deleted File Recovery

**Fast path**:
```bash
# Purpose: fls -d lists only deleted entries still present in the filesystem's directory structure
fls -o 2048 -r -d disk.img

# Purpose: icat exports file content by inode number, works even for deleted-but-unallocated inodes
icat -o 2048 disk.img <inode> > recovered_file

# Purpose: generic carving pass for known file types across unallocated space
foremost -i disk.img -o carved_output/
# or
photorec disk.img
```
**Indicators**: `fls -d` entries marked `*` (deleted). `foremost`/`photorec`
successfully carve recognizable file types from raw unallocated blocks.

**Next Step**: run `file`/`exiftool`/`strings` on every recovered file —
treat each as a fresh artifact per `file-analysis.md`.

**Common Mistakes**: mounting an image read-write and accidentally altering
timestamps/journal state before extraction — always `ro,loop`.

## Technique: Timeline Analysis

**Fast path**:
```bash
# Purpose: build a bodyfile of every file's MAC(B) timestamps
fls -r -m / -o 2048 disk.img > body.txt

# Purpose: convert bodyfile into a human-readable timeline
mactime -b body.txt -d > timeline.csv
```
**Indicators**: a burst of file creation/modification at one timestamp
correlating with the challenge's implied incident time; anomalous
timestamps (files "created" before the OS install date) suggesting
timestomping.

**Next Step**: cross-reference timeline entries against
`logs.md` and any recovered browser/user-activity artifacts below.

## Technique: Browser Artifacts & Recycle Bin / User Activity

**Fast path**:
```bash
# Purpose: Chrome/Chromium history is a SQLite DB — query directly
sqlite3 "Default/History" "SELECT url, title, datetime(last_visit_time/1000000-11644473600,'unixepoch') FROM urls ORDER BY last_visit_time DESC;"

# Purpose: Firefox equivalent
sqlite3 places.sqlite "SELECT url, title, datetime(last_visit_date/1000000,'unixepoch') FROM moz_places ORDER BY last_visit_date DESC;"

# Purpose: Windows Recycle Bin ($Recycle.Bin) metadata files ($I*) hold original path + delete time
python3 -c "
# Purpose: parse a Windows $I recycle-bin metadata file header
import struct
d = open('\$IABCDEF','rb').read()
print('deleted size:', struct.unpack('<q', d[8:16])[0])
print('original path:', d[24:].decode('utf-16-le').rstrip('\x00'))
"
```
**Indicators**: browser history showing the flag-relevant site/search;
recycle-bin metadata revealing the original path of a deleted flag file
even if the `$R*` content file itself is gone.

**Next Step**: if only the `$I` metadata survives (content gone), the
original filename/path itself may be the clue needed (e.g. it names another
artifact to look for elsewhere in the image).

## Technique: Hidden Files & Mounted Image Inspection

**Fast path**:
```bash
# Purpose: find dotfiles/hidden Windows-attribute files not shown by default listings
fls -r -p disk.img | grep -E '/\.'          # unix hidden
# Purpose: Windows hidden/system attribute files, once mounted
find /mnt/evidence -iname '.*' -o -iname 'Thumbs.db' -o -iname 'desktop.ini'
```
**Common Mistakes**: forgetting that `ls`/`find` on a normal mount hide
dotfiles by default without `-a`/explicit patterns; not checking Alternate
Data Streams on NTFS mounts (see `file-analysis.md`'s ADS section).

## Common Mistakes (Disk Forensics, general)

- Mounting read-write and altering evidence state.
- Skipping `mmls`/offset calculation and trying to mount the raw image
  directly as a filesystem (fails silently or mounts the wrong partition).
- Not checking every partition — flags are sometimes hidden in a small
  secondary/hidden partition, not the main OS volume.
