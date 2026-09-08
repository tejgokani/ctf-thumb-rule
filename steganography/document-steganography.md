# Document Steganography (PDF, Office, Text)

## Fastest First-Pass Checklist

```bash
# Purpose: metadata first
exiftool document.pdf

# Purpose: strings sweep — many document-stego "tricks" are just visually-hidden plain text
strings -n 8 document.pdf | less

# Purpose: check for embedded/appended files
binwalk document.pdf
pdfdetach -saveall document.pdf
```

## Technique: Whitespace / Invisible-Character Steganography

**When to use it**: a plain-text or Word document where hidden data is
encoded via invisible characters (trailing whitespace, zero-width
Unicode characters) rather than a binary payload.

**Fast path**:
```bash
# Purpose: reveal trailing whitespace patterns (common: spaces=0, tabs=1, a whitespace-based binary encoding)
cat -A document.txt | grep -E ' \$| I\$'

# Purpose: detect and strip zero-width Unicode steganography (zero-width space/joiner/non-joiner)
python3 -c "
data = open('document.txt', encoding='utf-8').read()
zwc = {'​':'0', '‌':'1', '‍':' '}  # example mapping; confirm actual chars present first
present = [c for c in zwc if c in data]
print('zero-width chars found:', present)
"
```
**Indicators**: `cat -A` reveals a consistent pattern of trailing spaces/
tabs per line; Python detects zero-width Unicode codepoints
(`U+200B`, `U+200C`, `U+200D`, `U+FEFF`) embedded in otherwise normal text.

**Next Step**: map the whitespace/zero-width character pattern to binary,
then to ASCII — this is a direct, mechanical decode once the encoding
scheme is confirmed.

## Technique: Hidden Text / Formatting in Office Documents

**When to use it**: `.docx`/`.pptx`/`.xlsx` — these are ZIP archives; text
can be hidden via white-on-white font color, tiny font size, or text boxes
positioned off-slide/off-page, none of which show in a casual read but all
of which persist in the underlying XML.

**Fast path**:
```bash
# Purpose: docx/pptx/xlsx are ZIPs — pull raw document XML directly, bypassing any visual rendering
unzip -p document.docx word/document.xml | less

# Purpose: search XML directly for text runs with suspicious formatting (hidden, white color, tiny size)
unzip -p document.docx word/document.xml | grep -oE '<w:t[^>]*>[^<]+</w:t>'
grep -o 'w:val="FFFFFF"' <(unzip -p document.docx word/document.xml)   # white-on-white text marker
```
**Indicators**: text runs present in the XML that don't appear in the
rendered document view — confirms hidden/invisible formatting rather than
actually deleted content.

## Technique: Revision History / Tracked Changes

**Fast path**:
```bash
# Purpose: tracked-changes/deleted content persists in the XML even if "accepted" visually in the UI
unzip -p document.docx word/document.xml | grep -oE '<w:del[^>]*>.*?</w:del>'
```
**Indicators**: `<w:ins>`/`<w:del>` elements present, revealing insertion/
deletion history not visible in the final rendered document.

## Technique: PDF Layers / Hidden Objects

**Fast path**: covered fully in
`../digital-forensics/common-file-formats.md`'s PDF section — object
inspection (`pdf-parser.py`), embedded files (`pdfdetach`), and content
stream decompression all apply here. Additionally check for Optional
Content Groups (PDF "layers") that may be hidden by default:
```bash
# Purpose: list PDF layers (Optional Content Groups) — a layer can be hidden from default view but still present
pdf-parser.py -s OCG document.pdf
```

## Common Mistakes

- Reading a document only visually (rendered view) instead of its raw
  underlying markup/XML — hidden-formatting tricks are invisible in
  render but trivially visible in source.
- Not checking for zero-width Unicode characters, which are invisible even
  in most text editors — a hex/codepoint-level check is required to spot
  them, not a visual read.
- Forgetting Office documents are ZIP archives and can be unzipped/
  inspected directly rather than only interacted with through the
  originating application.
