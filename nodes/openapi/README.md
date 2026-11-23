# OpenAPI

## Overview

StudioQuack builtin (`templateId` from `node_definition.json` `_id`, override `SQ_TEMPLATE_ID_OPENAPI`). Handler: `OfficialApiNode` + `api_spec.json`. Generic GET stub against a configured host (default `https://example.com`); this is not an OpenAPI parser. Spec: https://spec.openapis.org/oas/latest.html.

Inbound provider webhooks are **not** handled by this node. `listen` / `trigger` operations are `kind: ingress`: use a BPMN message start, intermediate catch, or boundary message event. StudioQuack webhook ingress correlates onto those events.

## Ports

| Port | Direction | Type | Required | Schema / flow |
|---|---|---|---|---|
| `input` | INPUT | object | yes | Per-operation fields. Never put `auth_token` here. |
| `output` | OUTPUT | object | yes | Success: `status=ok`, `http_status`, `data`. Failure: `{error}` only. |

## Operations

| operation | HTTP | Path | Notes |
|---|---|---|---|
| `import_spec` | GET | `/{spec_path}` | GET stub — does not parse OpenAPI |
| `list_operations` | GET | `/{spec_path}` | Same GET as import_spec |
| `invoke` | GET | `/{path}` | GET only |

## Configuration parameters

| Name | Type | Required | Secret | Purpose |
|---|---|---|---|---|
| `operation` | select | yes | no | Chooses the row above |
| `auth_token` | password | yes | yes | Optional passthrough bearer for invoke. Leave unused for public specs. |
| `timeout_seconds` | number | no | no | Default 25 |

## How it works

1. `OfficialApiHandlers` registers this folder key because `api_spec.json` exists.
2. Path `{placeholders}` are taken from input or config and URL-encoded.
3. JDK HTTP via `DataSupport.exchange`. Response cap is engine-bounded.
4. HTTP 2xx → `status=ok`. Other statuses → `error` plus `http_status`.
5. Ingress operations return an instruction to use BPMN message events — no HTTP call.

## Security & legal

- Secrets only from `configurationParameters.auth_token`. Never logged, never returned.
- [OpenAPI 3.1 / HTTP (RFC 9110) docs](https://spec.openapis.org/oas/latest.html). Terms: https://www.rfc-editor.org/rfc/rfc9110.html.
- Parse official OpenAPI then HTTP. invoke follows HTTP Request rules (allowlisted methods, daemon SSRF/egress). No random site crawl. Packaged host is a placeholder; this node documents the contract more than it executes arbitrary hosts.
- Official API only. No HTML scrape. File fields use type `file` (ref-id).
