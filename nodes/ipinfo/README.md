# ipinfo

Official-API node driven by `OfficialApiNode` + `api_spec.json`.

| | |
|---|---|
| Folder | `nodes/ipinfo` |
| `templateId` | `53691951-fa1e-5c4e-bdb6-08e762386827` |
| Env override | `SQ_TEMPLATE_ID_IPINFO` |
| Category | Data & Analytics |
| Official docs | https://ipinfo.io/developers |
| Auth | `bearer` — config `token` |

## Operations

Verified 2026-08-22: [Developer Resource](https://ipinfo.io/developers) · [Lite API](https://ipinfo.io/developers/lite-api) · [API overview](https://ipinfo.io/developers/ipinfo-api) · [Batch](https://ipinfo.io/developers/batch-enrichment-api).

| operation | HTTP | URL |
|---|---|---|
| `lookup` | GET | `https://api.ipinfo.io/lookup/{ip}` (Core / Plus / Max — paid) |
| `lite` | GET | `https://api.ipinfo.io/lite/{ip}` (unlimited country + ASN) |
| `me` | GET | `https://api.ipinfo.io/lite/me` |
| `batch` | POST | `https://api.ipinfo.io/batch` | `ips` list is the whole JSON body (`bodyMap` `"$"`) |
| `lookup_legacy` | GET | `https://ipinfo.io/{ip}/json` (**deprecated**; still served) |

Hard cutover of `lookup` / `me` / `batch` onto `api.ipinfo.io`. Core self-lookup: `lookup` with `ip=me`. Free tokens should use `lite` / `me`.

## Ports

One INPUT `input`, one OUTPUT `output`. Output is `{ status, http_status, data }` or `{status,http_status,data}`.

## Legal

ipinfo.io terms. Bearer token. Docs also show `?token=` and Basic `-u $TOKEN:`. IP lookup can be PII.

Operator accepts the provider terms. Official HTTP only — no HTML scrape. Secrets are StudioQuack bindings; never logged.
