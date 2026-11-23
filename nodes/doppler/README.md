# Doppler

StudioQuack builtin (`templateId` from `node_definition.json` `_id`, override `SQ_TEMPLATE_ID_DOPPLER`). Handler: `OfficialApiNode` + `api_spec.json`. Calls the official [Doppler API v3](https://docs.doppler.com/reference) at `https://api.doppler.com/v3`.

Inbound provider webhooks are **not** handled by this node. `listen` is `kind: ingress`: use a BPMN message start, intermediate catch, or boundary message event delivered through the StudioQuack engine's message-event ingress.

## Ports

| Port | Direction | Type | Required | Schema / flow |
|---|---|---|---|---|
| `input` | INPUT | object | yes | Per-operation fields. Never put `doppler_token` here. |
| `output` | OUTPUT | object | yes | Success: `status=ok`, `http_status`, `data`. Failure: `BPMN error` only. |

FUNCTION ports: none. `jobType` is `custom-script-executor`. Outbound HTTPS only.

## Operations

| operation | HTTP | Path | Notes |
|---|---|---|---|
| `get-secret` | GET | `/configs/config/secret` | Official HTTP |
| `list-secrets` | GET | `/configs/config/secrets` | Official HTTP |
| `update-secrets` | POST | `/configs/config/secrets` | Official HTTP |
| `delete-secret` | DELETE | `/configs/config/secret` | Official HTTP |
| `list-projects` | GET | `/projects` | Official HTTP |
| `list-configs` | GET | `/configs` | Official HTTP |
| `download-config` | GET | `/configs/config/secrets/download` | Official HTTP |
| `listen` | ingress | — | BPMN message event (no socket) |

## Configuration parameters

| Name | Type | Required | Secret | Purpose |
|---|---|---|---|---|
| `operation` | select | yes | no | Chooses the row above |
| `doppler_token` | password | yes | yes | Doppler service token (prefer per-config). Sent as Authorization: Bearer. StudioQuack secret. Never logged. |
| `project` | string | no | no | Project slug/id. Query/body for secret and config ops. Not required when the service token implies it. |
| `config` | string | no | no | Config name (e.g. dev). Query/body for secret ops. Not required when the service token implies it. |
| `timeout_seconds` | number | no | no | Default 25 |

## Secret values

GET/access operations still call the official GET (or access) HTTP method. Retrieved secret material must not be logged and should stay in StudioQuack secret bindings (bind OUTPUT `data` into a secret; do not print it, persist it in engine variables, or leave it on a logged edge).

Auth tokens, passwords, and retrieved secret bytes are never logged. Do not put them on INPUT, in engine variables, or on a logged edge.

## How it works

1. `OfficialApiNode` loads this folder because `api_spec.json` exists.
2. Path `{placeholders}` are taken from input or config and URL-encoded. Tenant hosts use `baseUrl` `https://` plus `{host}/…`.
3. Query keys in `queryFrom` resolve from input then config. JSON `bodyFrom` is INPUT-only.
4. JDK HTTP via `DataSupport.exchange`. HTTP 2xx → `status=ok`. Other statuses → `error` plus `http_status`.
5. Ingress operations return an instruction to use BPMN message events — no HTTP call.

## Security & legal

- Secrets only from password `configurationParameters`. Never logged, never returned on purpose.
- [Doppler API v3 docs](https://docs.doppler.com/reference). Terms: https://www.doppler.com/legal/terms.
- Prefer a service token over a personal token. get-secret and list-secrets still call official GET; retrieved raw/computed secret values must not be logged and should stay in StudioQuack secret bindings. download-config returns the config file body — treat it as secret material.
- Official API only. No HTML scrape. File fields use type `file` (ref-id).
