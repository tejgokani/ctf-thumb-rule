# Domain & Infrastructure Recon

## Fastest First-Pass Checklist

```bash
# Purpose: registration info — registrant, dates, name servers
whois target.example

# Purpose: full DNS record sweep
dig target.example ANY
dig target.example MX
dig target.example TXT
dig target.example NS

# Purpose: subdomains via certificate transparency logs — works even for "hidden" subdomains never linked publicly
curl -s "https://crt.sh/?q=%25.target.example&output=json" | jq -r '.[].name_value' | sort -u
```

## Technique: WHOIS / RDAP

**When to use it**: first move on any domain target.

**Fast path**: `whois target.example` — registrant name/org (if not
privacy-protected), registration/expiry dates, name servers.

**Indicators**: registrant details not redacted (older domains, ccTLDs
with different privacy defaults) directly identify an owner; matching name
servers/registrar across two "unrelated" domains suggest common
ownership/infrastructure.

## Technique: DNS Enumeration

**Fast path**:
```bash
# Purpose: check every common record type individually — some resolvers don't return everything for ANY
for t in A AAAA MX TXT NS CNAME SOA; do echo "== $t =="; dig +short target.example $t; done

# Purpose: TXT records commonly carry SPF/verification strings that reveal third-party service usage
dig +short target.example TXT
```
**Indicators**: TXT records revealing a `google-site-verification`,
`v=spf1 include:...` (reveals email provider), or a direct flag/hint
string (CTF authors sometimes hide data directly in DNS TXT records).

## Technique: Certificate Transparency

**When to use it**: enumerate subdomains that were never linked publicly
but had a TLS certificate issued (every public CA logs issuance).

**Fast path**:
```bash
# Purpose: crt.sh queries the certificate transparency log database directly
curl -s "https://crt.sh/?q=%25.target.example&output=json" | jq -r '.[].name_value' | sort -u
```
**Indicators**: a subdomain like `internal-admin.target.example` or
`staging.target.example` appearing in the cert log despite no public link
to it — a very common CTF technique for hiding a "hidden" challenge
endpoint.

## Technique: Historical / Archived Content

**Fast path**:
```bash
# Purpose: Wayback Machine snapshots — content since removed/edited may still be recoverable
curl -s "http://archive.org/wayback/available?url=target.example" | jq

# Purpose: full list of archived URLs under a domain (useful for finding old/removed paths)
curl -s "http://web.archive.org/cdx/search/cdx?url=target.example*&output=json" | jq
```
**Indicators**: an old snapshot shows content/paths since removed from the
live site — CTF challenges sometimes point at a domain specifically
*because* the interesting content is only in an archived version.

## Technique: IP / Infrastructure Relationships

**Fast path**:
```bash
# Purpose: resolve and check IP ownership/ASN — reveals hosting provider, and shared IPs with other domains
dig +short target.example
whois <resolved_ip>

# Purpose: reverse DNS / other domains sharing the same IP (shared hosting correlation)
curl -s "https://api.hackertarget.com/reverseiplookup/?q=<resolved_ip>"
```
**Indicators**: two domains sharing an IP/ASN with corroborating evidence
elsewhere (same registrant, same TXT verification code) — infrastructure
relationship confirms a common-ownership hypothesis.

## Common Mistakes

- Only checking `whois` and giving up when registration is privacy-
  protected — DNS records, cert transparency, and archive.org frequently
  succeed where WHOIS is redacted.
- Not checking TXT records specifically — an easy-to-overlook record type
  that CTF challenges deliberately use because most people forget it.
- Treating a shared IP alone as proof of relationship without
  corroborating evidence — shared hosting providers put many unrelated
  domains on the same IP; it's a lead, not proof by itself.
