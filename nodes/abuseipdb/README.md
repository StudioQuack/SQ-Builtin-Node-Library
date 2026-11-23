# AbuseIPDB

Official [AbuseIPDB APIv2](https://docs.abuseipdb.com/). IP reputation, verified abuse reports, JSON blacklist. Do not submit speculative reports. Category IDs: https://www.abuseipdb.com/categories.

- **Docs:** https://docs.abuseipdb.com/
- **Base:** `https://api.abuseipdb.com/api/v2`
- **Auth:** header `Key` (prefix empty), configKey `api_key`
- **Handler:** `OfficialApiNode` via `api_spec.json`.

## Operations

| operation | HTTP | Path |
|---|---|---|
| `check` | GET | `/check` |
| `report` | POST | `/report` |
| `blacklist` | GET | `/blacklist` |
| `check-block` | GET | `/check-block` |

## Configuration parameters

| Name | Type | Secret | Purpose |
|---|---|---|---|
| `operation` | select | no | Default verb |
| `api_key` | password | yes | Header Key |
| `timeout_seconds` | number | no | Default 25 |

## Ports

`input` / `output` only. Failures raise a BPMN error (INVALID_INPUT, AUTH_FAILED, RATE_LIMITED, HTTP_<status>, REQUEST_FAILED); secrets are redacted.

## Legal

- Terms: https://www.abuseipdb.com/legal · Privacy: https://www.abuseipdb.com/privacy
- False or speculative reports can suspend reporting privileges. IP addresses may be personal data.
- Official APIv2 only. No APIv1, no plaintext blacklist, no bulk-report in this node (`POST /bulk-report` is CSV multipart). check-block is GET `/check-block?network=`.

## Engine gaps

- `report` official body is form-urlencoded. OfficialApiNode serializes JSON.
