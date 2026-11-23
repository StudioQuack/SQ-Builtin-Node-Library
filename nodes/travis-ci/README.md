# Travis CI

## Overview

StudioQuack builtin (`templateId` from `node_definition.json` `_id`, override `SQ_TEMPLATE_ID_TRAVIS_CI`). Handler: `OfficialApiNode` + `api_spec.json`. Calls the official [Travis CI API v3](https://docs.travis-ci.com/user/developer/) at `https://api.travis-ci.com`. V3 sends `Travis-API-Version: 3` via spec `extraHeaders`.

Inbound provider webhooks are **not** handled by this node. `listen` / `trigger` operations are `kind: ingress`: use a BPMN message start, intermediate catch, or boundary message event. StudioQuack webhook ingress correlates onto those events.

## Ports

| Port | Direction | Type | Required | Schema / flow |
|---|---|---|---|---|
| `input` | INPUT | object | yes | Per-operation fields. Never put `api_token` here. |
| `output` | OUTPUT | object | yes | Success: `status=ok`, `http_status`, `data`. Errors raise a BPMN error (INVALID_INPUT, AUTH_FAILED, RATE_LIMITED, HTTP_<status>, REQUEST_FAILED); secrets are redacted. |

## Operations

| operation | HTTP | Path | Notes |
|---|---|---|---|
| `build_get` | GET | `/build/{build_id}` | Official HTTP |
| `build_list` | GET | `/repo/{slug}/builds` | Official HTTP |
| `build_cancel` | POST | `/build/{build_id}/cancel` | Official HTTP |
| `build_restart` | POST | `/build/{build_id}/restart` | Official HTTP |
| `build_trigger` | POST | `/repo/{slug}/requests` | Official HTTP |

## Configuration parameters

| Name | Type | Required | Secret | Purpose |
|---|---|---|---|---|
| `operation` | select | yes | no | Chooses the row above |
| `api_token` | password | yes | yes | Travis CI API token. Authorization: token <token>. Travis-API-Version: 3 is sent via extraHeaders. travis-ci.com (org is legacy). |
| `timeout_seconds` | number | no | no | Default 25 |

## How it works

1. `OfficialApiHandlers` registers this folder key because `api_spec.json` exists.
2. Path `{placeholders}` are taken from input or config and URL-encoded.
3. JDK HTTP via `DataSupport.exchange`. Response cap is engine-bounded.
4. HTTP 2xx → `status=ok`. Other statuses raise a BPMN error (AUTH_FAILED, RATE_LIMITED, HTTP_<status>, REQUEST_FAILED).
5. Ingress operations return an instruction to use BPMN message events — no HTTP call.

## Security & legal

- Secrets only from `configurationParameters.api_token`. Never logged, never returned.
- [Travis CI API v3 docs](https://docs.travis-ci.com/user/developer/). Terms: https://www.ideracorp.com/legal/TravisCI.
- Travis CI terms. Use travis-ci.com. Travis-API-Version: 3 is sent on every request.
- Official API only. No HTML scrape. File fields use type `file` (ref-id).

## Trigger body

`build_trigger` POST `/repo/{slug}/requests` accepts `branch`, `sha`, `message`, `config`, `merge_mode` (nested under `request`) per https://docs.travis-ci.com/user/triggering-builds/.
