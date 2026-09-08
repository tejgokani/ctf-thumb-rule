# Network Forensics (General)

This document covers general network-forensics reasoning that applies
beyond a single `.pcap` file (e.g. live capture triage, log-correlated
network incidents). For the full PCAP-specific tool workflow (Wireshark +
tshark), see `pcaps.md`.

## Fastest First-Pass Checklist

```bash
# Purpose: identify capture format/link type before deeper analysis
file capture.pcap

# Purpose: quick protocol breakdown to know what's actually in the capture
tshark -r capture.pcap -q -z io,phs
```

## Technique: Scoping the Investigation

**When to use it**: immediately, to avoid analyzing irrelevant traffic in a
large/noisy capture.

**Fast path**:
```bash
# Purpose: list unique conversations by endpoint pair — find the "interesting" pair fast
tshark -r capture.pcap -q -z conv,ip

# Purpose: list all distinct protocols seen, with packet/byte counts
tshark -r capture.pcap -q -z io,phs
```
**Indicators**: one conversation pair with disproportionate traffic volume,
or a rare/unexpected protocol (e.g. FTP or Telnet in an otherwise
HTTPS-only capture) — investigate that pair/protocol first.

**Next Step**: filter the capture to just that conversation before running
deeper tools — cuts noise and speeds every subsequent command:
```bash
# Purpose: write a filtered subset containing only the interesting conversation
tshark -r capture.pcap -Y "ip.addr==1.2.3.4 && ip.addr==5.6.7.8" -w filtered.pcap
```

## Technique: Credential & Secret Harvesting Across Protocols

**Fast path**:
```bash
# Purpose: tshark has built-in credential extraction across common cleartext protocols
tshark -r capture.pcap -z credentials -q
```
**Indicators**: any output at all — this plugin specifically flags
HTTP Basic auth, FTP, Telnet, and other cleartext credential exchanges.

**Next Step**: correlate the harvested credential with a login attempt seen
elsewhere (a web challenge, an SSH banner) — network forensics challenges
in "What The Flag" frequently chain into a second artifact.

## Technique: Encrypted Traffic (TLS) Handling

**Indicators**: `tshark -z io,phs` shows heavy TLS volume with no visible
payload.

**Next Step**: check whether the challenge provided a TLS key log file
(`SSLKEYLOGFILE` format) or private key — if so:
```bash
# Purpose: decrypt TLS in Wireshark/tshark using a provided key log file
tshark -r capture.pcap -o "tls.keylog_file:keys.log" -Y http
```
If no key material is provided, TLS decryption is out of scope — the flag
is almost certainly in the unencrypted portions of the capture (DNS
lookups, TCP handshake metadata, an earlier cleartext protocol, or a
sibling artifact), not in breaking TLS itself. Re-triage rather than
attempting to attack TLS encryption directly.

## Common Mistakes

- Trying to "crack" TLS instead of checking for provided key material or
  pivoting to unencrypted parts of the capture.
- Analyzing the entire raw capture with heavy tools when a 30-second
  conversation filter would isolate the relevant traffic in one command.
- Missing DNS traffic — DNS queries/responses are a common covert channel
  for flag exfiltration in these challenges (see `pcaps.md`'s DNS section).
