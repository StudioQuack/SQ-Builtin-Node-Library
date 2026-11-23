# Codecov

## Overview

StudioQuack builtin (`templateId` from `node_definition.json` `_id`, override `SQ_TEMPLATE_ID_CODECOV`). Handler: `OfficialApiNode` + `api_spec.json`. Calls the official [Codecov API](https://docs.codecov.com/reference) at `https://api.codecov.io`.

Inbound provider webhooks are **not** handled by this node. `listen` / `trigger` operations are `kind: ingress`: use a BPMN message start, intermediate catch, or boundary message event. StudioQuack webhook ingress correlates onto those events.

## Ports

| Port | Direction | Type | Required | Schema / flow |
|---|---|---|---|---|
| `input` | INPUT | object | yes | Per-operation fields. Never put `token` here. |
| `output` | OUTPUT | object | yes | Success: `status=ok`, `http_status`, `data`. Errors raise a BPMN error for the error boundary event (INVALID_INPUT, AUTH_FAILED, RATE_LIMITED, HTTP_<status>, REQUEST_FAILED); secrets are redacted. |

## Operations

| operation | HTTP | Path | Notes |
|---|---|---|---|
| `repo_get` | GET | `/api/v2/{service}/{owner}/repos/{repo}/` | Official HTTP |
| `commit_get` | GET | `/api/v2/{service}/{owner}/repos/{repo}/commits/{sha}/` | Official HTTP |
| `coverage_get` | GET | `/api/v2/{service}/{owner}/repos/{repo}/commits/{sha}/` | Official HTTP |
| `flag_list` | GET | `/api/v2/{service}/{owner}/repos/{repo}/flags/` | Official HTTP |
| `compare` | GET | `/api/v2/{service}/{owner}/repos/{repo}/compare/` | query `base` + `head` (commit SHAs or branches), or `pullid` |

## Configuration parameters

| Name | Type | Required | Secret | Purpose |
|---|---|---|---|---|
| `operation` | select | yes | no | Chooses the row above |
| `token` | password | yes | yes | Codecov API token. Authorization: Bearer. |
| `timeout_seconds` | number | no | no | Default 25 |

## How it works

**Auth:** Bearer `token` lives only in instance config (`configurationParameters.token`), not on INPUT.

**AI Agent tool schema:** Map DuckTape `request` → INPUT (`operation`, `service`, `owner`, `repo`, `sha`, …) or clients see empty `properties: {}` while runtime still validates.

1. `OfficialApiHandlers` registers this folder key because `api_spec.json` exists.
2. Path `{placeholders}` are taken from input or config and URL-encoded. `{sha}` must be a **40-character hex** Git commit id (validated before HTTP).
3. JDK HTTP via `DataSupport.exchange`. Response cap is engine-bounded.
4. HTTP 2xx → `status=ok`. Non-2xx raise a BPMN error.
5. Ingress operations return an instruction to use BPMN message events — no HTTP call.

## Security & legal

- Secrets only from `configurationParameters.token`. Never logged, never returned.
- [Codecov API docs](https://docs.codecov.com/reference). Terms: https://about.codecov.io/terms-of-service/.
- Codecov terms. Token scopes apply. No badge HTML scrape.
- Official API only. No HTML scrape. File fields use type `file` (ref-id).
