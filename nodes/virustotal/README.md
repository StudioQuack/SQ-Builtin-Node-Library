# VirusTotal

Official [VirusTotal API v3](https://docs.virustotal.com/reference/overview). Reputation lookups and submissions. Uploading a customer file **shares that sample with VirusTotal** (participating vendors + Community visibility). Treat `scan-file` as a disclosure of customer content (may include PHI/PII). Prefer `get-file-report` by hash when you already have MD5/SHA-1/SHA-256.

- **Docs:** https://docs.virustotal.com/reference/overview
- **Base:** `https://www.virustotal.com/api/v3`
- **Auth:** header `x-apikey` (prefix empty), configKey `api_key`
- **Handler:** `OfficialApiNode` via `api_spec.json`.

## Operations

| operation | HTTP | Path |
|---|---|---|
| `scan-url` | POST | `/urls` |
| `scan-file` | POST | `/files` |
| `get-file-report` | GET | `/files/{id}` |
| `get-url-report` | GET | `/urls/{id}` |
| `get-ip-report` | GET | `/ip_addresses/{ip}` |
| `get-domain-report` | GET | `/domains/{domain}` |
| `search` | GET | `/search` |

## Configuration parameters

| Name | Type | Secret | Purpose |
|---|---|---|---|
| `operation` | select | no | Default verb |
| `api_key` | password | yes | x-apikey header |
| `timeout_seconds` | number | no | Default 25 |

## Ports

`input` / `output` only. Failures: `{ error }` — never includes secrets.

## Legal

- Public vs Premium: https://docs.virustotal.com/reference/public-vs-premium-api
- Terms pointer: https://docs.virustotal.com/docs/terms-of-service
- Public API: published quotas apply (Community key). Do not use reports to harm. Uploaded files leave the customer host.
- Official REST v3 only. No API v2, no sample download, no Livehunt/Retrohunt/feeds, no Premium `/intelligence/search`.

## Engine gaps

- `scan-url` official body is `application/x-www-form-urlencoded` field `url`. OfficialApiNode serializes JSON.
- `scan-file` official body is multipart `file` (max 32MB on POST /files; larger files use GET /files/upload_url then POST to that URL — not in this node). OfficialApiNode cannot upload bytes from a file ref-id as multipart.
