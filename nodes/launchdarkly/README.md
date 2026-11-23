# LaunchDarkly

## Overview

StudioQuack builtin (`templateId` from `node_definition.json` `_id`, override `SQ_TEMPLATE_ID_LAUNCHDARKLY`). Handler: `OfficialApiNode` + `api_spec.json`. Calls the official [LaunchDarkly REST API](https://launchdarkly.com/docs/api) at `https://app.launchdarkly.com/api/v2`. Every request sends `LD-API-Version: 20240415` via spec `extraHeaders`.

Inbound provider webhooks are **not** handled by this node. `listen` / `trigger` operations are `kind: ingress`: use a BPMN message start, intermediate catch, or boundary message event. StudioQuack webhook ingress correlates onto those events.

## Ports

| Port | Direction | Type | Required | Schema / flow |
|---|---|---|---|---|
| `input` | INPUT | object | yes | Per-operation fields. Never put `api_token` here. |
| `output` | OUTPUT | object | yes | Success: `status=ok`, `http_status`, `data`. Failure: `{error}` only. |

## Operations

| operation | HTTP | Path | Notes |
|---|---|---|---|
| `flag_get` | GET | `/flags/{projectKey}/{featureKey}` | Official HTTP |
| `flag_list` | GET | `/flags/{projectKey}` | Official HTTP |
| `flag_toggle` | PATCH | `/flags/{projectKey}/{featureKey}` | Whole-body JSON Patch array (`patch`) |
| `flag_patch` | PATCH | `/flags/{projectKey}/{featureKey}` | Whole-body JSON Patch array (`patch`) |
| `segment_get` | GET | `/segments/{projectKey}/{environmentKey}/{segmentKey}` | Official HTTP |
| `environment_list` | GET | `/projects/{projectKey}/environments` | Official HTTP |
| `listen` | ingress | — | BPMN message event (no socket) |

## Configuration parameters

| Name | Type | Required | Secret | Purpose |
|---|---|---|---|---|
| `operation` | select | yes | no | Chooses the row above |
| `api_token` | password | yes | yes | LaunchDarkly access token. Authorization: <token> (no Bearer). LD-API-Version: 20240415 is sent via extraHeaders. |
| `timeout_seconds` | number | no | no | Default 25 |

## How it works

1. `OfficialApiHandlers` registers this folder key because `api_spec.json` exists.
2. Path `{placeholders}` are taken from input or config and URL-encoded.
3. JDK HTTP via `DataSupport.exchange`. Response cap is engine-bounded.
4. HTTP 2xx → `status=ok`. Other statuses → `error` plus `http_status`.
5. Ingress operations return an instruction to use BPMN message events — no HTTP call.

## Security & legal

- Secrets only from `configurationParameters.api_token`. Never logged, never returned.
- [LaunchDarkly REST API docs](https://launchdarkly.com/docs/api). Terms: https://launchdarkly.com/policies/terms-of-service/.
- LaunchDarkly terms. Audit flag changes. Official REST only.
- Official API only. No HTML scrape. File fields use type `file` (ref-id).
