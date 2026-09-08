# PCAP Analysis — Wireshark & tshark

## Fastest First-Pass Checklist (PCAP)

```bash
# Purpose: confirm capture format and link-layer type
file capture.pcap

# Purpose: protocol hierarchy — what's actually inside, at a glance
tshark -r capture.pcap -q -z io,phs

# Purpose: conversations by endpoint pair, sorted by volume
tshark -r capture.pcap -q -z conv,tcp
tshark -r capture.pcap -q -z conv,udp

# Purpose: pull every file Wireshark's export-objects feature can reconstruct, by protocol
tshark -r capture.pcap --export-objects http,/tmp/http_objs
tshark -r capture.pcap --export-objects smb,/tmp/smb_objs

# Purpose: harvest cleartext credentials automatically
tshark -r capture.pcap -z credentials -q

# Purpose: dump all DNS queries — common exfil/covert-channel vector
tshark -r capture.pcap -Y dns -T fields -e dns.qry.name
```

## Technique: Protocol Hierarchy & Conversations

**When to use it**: always first — establishes scope before deep-diving.

**Tools**: `tshark -z io,phs` (protocol hierarchy statistics), `tshark -z
conv,ip/tcp/udp` (conversations), Wireshark GUI: Statistics → Protocol
Hierarchy / Conversations / Endpoints.

**Expected Results**: a tree of protocols with packet/byte counts; a table
of endpoint pairs with volume.

**Indicators**: unexpected protocol present (FTP, Telnet, SMB, IRC in an
otherwise modern capture) → high-value, investigate first. One
disproportionately large conversation → likely the file-transfer or
exfiltration channel.

**Next Step**: filter to the interesting protocol/conversation, then follow
its stream (see below).

## Technique: Follow TCP/UDP Stream

**Fast path (GUI)**: right-click a packet → Follow → TCP Stream (or UDP
Stream). Shows the full reassembled conversation, both directions,
color-coded.

**Fast path (CLI)**:
```bash
# Purpose: reassemble and print one TCP stream's payload as ASCII
tshark -r capture.pcap -q -z follow,tcp,ascii,0
# stream index (the trailing 0) comes from tshark -r capture.pcap -T fields -e tcp.stream
```
**Indicators**: readable HTTP request/response, a login form POST body, an
FTP command sequence, chat/IRC text — often contains the flag directly or a
next-step hint.

**Next Step**: if the stream contains a file transfer, use export objects
instead of manual stream reading (cleaner extraction).

## Technique: HTTP Traffic

**Fast path**:
```bash
# Purpose: list every HTTP request with method + URI + response code
tshark -r capture.pcap -Y http.request -T fields -e http.request.method -e http.host -e http.request.uri

# Purpose: extract all files transferred over HTTP automatically
tshark -r capture.pcap --export-objects http,http_out/

# Purpose: dump full HTTP objects list with content-type from Wireshark GUI equivalent
tshark -r capture.pcap -Y http -T fields -e http.request.full_uri -e http.content_type
```
**Indicators**: a request URI containing a base64/hex-looking parameter, a
downloaded file with an interesting content-type (application/zip,
image/png sent over HTTP), a `flag` or `token` parameter.

## Technique: DNS Analysis (Exfil / Covert Channel)

**Fast path**:
```bash
# Purpose: list every distinct DNS query name — long/high-entropy subdomains suggest DNS tunneling/exfil
tshark -r capture.pcap -Y dns.flags.response==0 -T fields -e dns.qry.name | sort -u

# Purpose: extract TXT record responses specifically — common flag/data carrier
tshark -r capture.pcap -Y "dns.txt" -T fields -e dns.txt
```
**Indicators**: many subdomains under one base domain with base32/base64-ish
labels (e.g. `4a6f696e5468654369727175 .exfil.example`) — classic DNS
tunneling encoding pattern.

**Next Step**: concatenate the subdomain labels in packet order, decode
(hex/base32), reassemble the exfiltrated payload — this is frequently the
whole challenge.

## Technique: FTP / SMTP / Cleartext Protocols

**Fast path**:
```bash
# Purpose: FTP commands and responses, including USER/PASS credential exchange
tshark -r capture.pcap -Y ftp -T fields -e ftp.request.command -e ftp.request.arg

# Purpose: SMTP commands, useful for extracting email content/attachments in transit
tshark -r capture.pcap -Y smtp -T fields -e smtp.req.command -e smtp.req.parameter
```
**Indicators**: `USER`/`PASS` FTP commands with the actual creds visible;
SMTP `DATA` command followed by full email body including attachments
(export via `--export-objects imf,out/`).

## Technique: TLS Metadata (Without Decryption)

**Fast path**:
```bash
# Purpose: even without decrypting payload, SNI/cert metadata is visible in the handshake
tshark -r capture.pcap -Y "tls.handshake.type==1" -T fields -e tls.handshake.extensions_server_name
tshark -r capture.pcap -Y "tls.handshake.type==11" -T fields -e x509sat.uTF8String
```
**Indicators**: SNI hostname reveals the actual service contacted even when
payload is encrypted — useful for OSINT-style pivots or narrowing which
conversation matters.

## Technique: Suspicious Payload / Packet Filtering

**Fast path**:
```bash
# Purpose: search every packet's raw bytes for a keyword across the whole capture
tshark -r capture.pcap -Y "frame contains \"flag\""

# Purpose: same, case-insensitive, matching a competition-specific prefix once known
tshark -r capture.pcap -Y "frame matches \"(?i)wtf\\{\""
```
**Indicators**: a direct hit is the fastest possible win — always try this
before deep protocol-specific analysis.

## Common Mistakes

- Opening a large capture directly in the Wireshark GUI and scrolling
  manually instead of running `-z io,phs`/`-z conv` first to scope down.
- Forgetting `tshark -Y "frame contains ..."` as a cheap first-pass search —
  it's often faster than the "proper" protocol-aware workflow.
- Not exporting objects and instead manually reconstructing files
  byte-by-byte from stream output (error-prone; use `--export-objects`).
- Ignoring UDP conversations because HTTP/TCP habits dominate — DNS, and
  custom UDP protocols, are common in these challenges.
