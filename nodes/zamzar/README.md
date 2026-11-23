# Zamzar

- **Status:** done (OfficialApiNode package)
- **Folder:** `nodes/zamzar/`
- **Official API:** https://developers.zamzar.com/
- **Auth secret:** `api_key`
- **Researched:** 2026-08-20

Calls the official HTTP surface via `OfficialApiNode` + `api_spec.json`.
`job_create` INPUT `source_file` and `file_download` OUTPUT `file` are platform file ref-ids (`sq-file:` / `sq-ref:gridfs:`). Never inline bytes or base64.

## Operations

| operation | HTTP | Path |
|---|---|---|
| `job_create` | POST | `/jobs` |
| `job_get` | GET | `/jobs/{id}` |
| `file_download` | GET | `/files/{id}/content` |
| `format_list` | GET | `/formats` |

## Ports

| Port | Direction | Notes |
|---|---|---|
| `input` | INPUT | Per-operation fields (`source_file`, `target_format`, `id`). No secrets. |
| `output` | OUTPUT | `{ status, http_status, data, file? }` or `{ error }`. |

FUNCTION ports: none.

## Configuration

`operation` (select), `api_key` (password), `timeout_seconds`.

## Legal

- Terms: https://www.zamzar.com/terms/
- Official-API-first. No scrape. No unofficial web wrapper.
- Credit-based conversion. File retention window is in the vendor terms. job_create input and file_download OUTPUT are type file.
- Logs: shapes, ids, status codes, sizes — never file bytes or secrets.

## Implementation notes

- Engine: OfficialApiNode (JDK HTTP). No vendor SDK.
- `listen` / ingress: use a BPMN message start or catch event.
- Failures map to OUTPUT `{ error }`. Never throw past dispatch.
