# ONLYOFFICE

- **Status:** done (OfficialApiNode package)
- **Folder:** `nodes/onlyoffice/`
- **Official API:** https://api.onlyoffice.com/docs/docs-api/additional-api/conversion-api/
- **Auth secret:** `jwt_secret`
- **Researched:** 2026-08-22

Calls the official HTTP surface via `OfficialApiNode` + `api_spec.json`.
Downloaded payloads may appear as OUTPUT `file` (platform file ref-id). Never inline bytes or base64 as the stored type.

## Operations

| operation | HTTP | Path |
|---|---|---|
| `convert` | POST | `/converter` |

## Ports

| Port | Direction | Notes |
|---|---|---|
| `input` | INPUT | Per-operation fields. No secrets. |
| `output` | OUTPUT | `{ status, http_status, data, file? }` or `{ error }`. |

FUNCTION ports: none.

## Configuration

`operation` (select), auth secrets (password), `docs_base_url`, `timeout_seconds`.

## Legal

- Terms: Customer-hosted — no vendor SaaS terms.
- Official-API-first. No scrape. No unofficial web wrapper.
- Community edition is AGPL — keep the server customer-hosted. Conversion source is INPUT `url`. OUTPUT may include `file`. Not a SaaS ToS if self-hosted.
- Logs: shapes, ids, status codes, sizes — never file bytes or secrets.

## Implementation notes

- Engine: OfficialApiNode (JDK HTTP). No vendor SDK.
- `listen` / ingress: use a BPMN message start or catch event.
- Failures map to OUTPUT `{ error }`. Never throw past dispatch.
