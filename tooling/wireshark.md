# Wireshark / tshark Quick Reference

Purpose: PCAP/network-capture analysis, GUI (Wireshark) and CLI (tshark)
— see `../digital-forensics/pcaps.md` for the full workflow this
supports.

## GUI Workflow (Wireshark)

1. **Statistics → Protocol Hierarchy** — scope the capture before diving
   in.
2. **Statistics → Conversations** — find the highest-volume or most
   unusual endpoint pair.
3. **Right-click a packet → Follow → TCP/UDP Stream** — read a full
   reassembled conversation.
4. **File → Export Objects → HTTP/SMB/...** — reconstruct transferred
   files without manual stream reading.
5. **Filter bar** — Wireshark display filter syntax
   (`http.request`, `dns`, `tcp.stream==3`, `frame contains "flag"`)
   applies identically in both GUI and `tshark -Y`.

## CLI Workflow (tshark)

```bash
# Purpose: protocol hierarchy — quick scope
tshark -r capture.pcap -q -z io,phs

# Purpose: conversations by volume
tshark -r capture.pcap -q -z conv,tcp

# Purpose: apply a display filter (same syntax as the Wireshark GUI filter bar)
tshark -r capture.pcap -Y "http.request"

# Purpose: reassemble and print a specific TCP stream
tshark -r capture.pcap -q -z follow,tcp,ascii,0

# Purpose: export all objects of a given protocol automatically
tshark -r capture.pcap --export-objects http,out_dir/

# Purpose: harvest cleartext credentials across supported protocols in one command
tshark -r capture.pcap -z credentials -q

# Purpose: quick keyword search across every packet's raw bytes
tshark -r capture.pcap -Y "frame contains \"flag\""
```

## Indicators & Decisions

Full indicator/decision detail lives in `../digital-forensics/pcaps.md` —
this doc is pure syntax reference.

## Alternatives

- `tcpdump` — capture-only, no analysis features; use tshark/Wireshark
  for actual analysis of a `.pcap` file.
- `NetworkMiner` — GUI tool specialized in automatic file/credential
  extraction from a capture, sometimes faster than manual export-objects
  work for very messy captures.

## Common Mistakes

- Opening a large capture in the GUI and scrolling manually instead of
  running `-z io,phs`/`-z conv` first to scope down.
- Manually reconstructing a transferred file from stream output instead
  of using `--export-objects`.
- Forgetting the credentials plugin (`-z credentials -q`) as a cheap
  first-pass check.
