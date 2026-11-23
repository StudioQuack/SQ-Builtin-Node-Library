# Postman


environment_update takes `name` and `values` merged into the official `environment` envelope via bodyMap. Docs: https://learning.postman.com/docs/developer/postman-api/introduction/

## Overview

StudioQuack builtin (`templateId` from `node_definition.json` `_id`, override `SQ_TEMPLATE_ID_POSTMAN`). Handler: `OfficialApiNode` + `api_spec.json`. Calls the official [Postman API](https://learning.postman.com/docs/developer/postman-api/authentication) at `https://api.postman.com`.

Inbound provider webhooks are **not** handled by this node. `listen` / `trigger` operations are `kind: ingress`: use a BPMN message start, intermediate catch, or boundary message event. StudioQuack webhook ingress correlates onto those events.

## Ports

| Port | Direction | Type | Required | Schema / flow |
|---|---|---|---|---|
| `input` | INPUT | object | yes | Per-operation fields. Never put `api_key` here. |
| `output` | OUTPUT | object | yes | Success: `status=ok`, `http_status`, `data`. Failure: `{error}` only. |

## Operations

| operation | HTTP | Path | Notes |
|---|---|---|---|
| `collection_get` | GET | `/collections/{collection_uid}` | Official HTTP |
| `collection_run` | GET | `/collections/{collection_uid}/runs` | List collection run history (not a trigger/webhook run). |
| `environment_get` | GET | `/environments/{environment_uid}` | Official HTTP |
| `environment_update` | PUT | `/environments/{environment_uid}` | Official HTTP |
| `monitor_run` | POST | `/monitors/{monitor_uid}/run` | Official HTTP |
| `mock_get` | GET | `/mocks/{mock_uid}` | Official HTTP |

## Configuration parameters

| Name | Type | Required | Secret | Purpose |
|---|---|---|---|---|
| `operation` | select | yes | no | Chooses the row above |
| `api_key` | password | yes | yes | Postman API key. Header X-Api-Key. |
| `timeout_seconds` | number | no | no | Default 25 |

## How it works

**Default operation:** Omitted INPUT `operation` uses instance config (`collection_get`).

**UIDs:** Postman uses workspace resource UIDs. Happy-path agent tests need real `collection_uid` / `environment_uid` / etc. from the connected workspace.

**`environment_update` on unknown UID:** Postman often returns HTTP **400** with a generic validation message, while GET-style ops return **404** — provider behavior, not remapped by the node.

**AI Agent tool schema:** Map DuckTape `request` → INPUT (`operation`, `*_uid`, `environment` fields) or the exposed schema stays empty while runtime still validates.

1. `OfficialApiHandlers` registers this folder key because `api_spec.json` exists.
2. Path `{placeholders}` are taken from input or config and URL-encoded.
3. JDK HTTP via `DataSupport.exchange`. Response cap is engine-bounded.
4. HTTP 2xx → `status=ok`. Other statuses → `error` plus `http_status`.
5. Ingress operations return an instruction to use BPMN message events — no HTTP call.

## Security & legal

- Secrets only from `configurationParameters.api_key`. Never logged, never returned.
- [Postman API docs](https://learning.postman.com/docs/developer/postman-api/intro-api/). Terms: https://www.postman.com/legal/terms/.
- Postman Terms. Collection run is Postman Cloud. Not a local CLI runner.
- Official API only. No HTML scrape. File fields use type `file` (ref-id).
