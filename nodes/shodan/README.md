# Shodan

Official [Shodan REST API](https://developer.shodan.io/api). **Lookup only.** `host` / `search` read banners already in the index. This node must not become an unauthorized scanning tool. Do not wrap `POST /shodan/scan`, internet crawl, exploits, or the Streaming API.

- **Docs:** https://developer.shodan.io/api
- **Base:** `https://api.shodan.io`
- **Auth:** query `key` (configKey `key`)
- **Handler:** `OfficialApiNode` via `api_spec.json`.

## Operations

| operation | HTTP | Path |
|---|---|---|
| `host` | GET | `/shodan/host/{ip}` |
| `search` | GET | `/shodan/host/search` (query param `query`) |
| `dns-resolve` | GET | `/dns/resolve` (Shodan **membership** required) |
| `dns-reverse` | GET | `/dns/reverse` (membership required) |
| `account-profile` | GET | `/account/profile` |

`search` requires INPUT `query` (alias `q`). Agent tools must map DuckTape `request` fields; nested `args`/`request` envelopes are flattened.

`dns-resolve` / `dns-reverse`: wire API expects comma-separated `hostnames` / `ips`; JSON arrays from tools are coerced.

## Configuration parameters

| Name | Type | Secret | Purpose |
|---|---|---|---|
| `operation` | select | no | Default verb |
| `key` | password | yes | Query param key |
| `timeout_seconds` | number | no | Default 25 |

## Ports

`input` / `output` only. Errors raise a BPMN error for the error boundary event (INVALID_INPUT, AUTH_FAILED, RATE_LIMITED, HTTP_<status>, REQUEST_FAILED); secrets are redacted.

## Legal

- Terms: https://static.shodan.io/legal/terms.html
- Credits: https://help.shodan.io/the-basics/credit-types-explained
- On-demand scanning is out of scope: https://help.shodan.io/the-basics/on-demand-scanning
- Results can include banner payloads and geolocation. Do not use lookups to target third parties for attack.
- Search uses official query param `query` (not `q`). INPUT `q` is kept as a deprecated alias and is ignored by Shodan.
