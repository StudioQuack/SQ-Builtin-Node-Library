# SonarQube

## Overview

StudioQuack builtin (`templateId` from `node_definition.json` `_id`, override `SQ_TEMPLATE_ID_SONARQUBE`). Handler: `OfficialApiNode` + `api_spec.json`. Calls the official [SonarQube Web API](https://docs.sonarsource.com/sonarqube-server/extension-guide/web-api) at `{base_url}/api` (config `base_url`; default SonarCloud `https://sonarcloud.io`).

Inbound provider webhooks are **not** handled by this node. `listen` / `trigger` operations are `kind: ingress`: use a BPMN message start, intermediate catch, or boundary message event. StudioQuack webhook ingress correlates onto those events.

## Ports

| Port | Direction | Type | Required | Schema / flow |
|---|---|---|---|---|
| `input` | INPUT | object | yes | Per-operation fields. Never put `token` here. |
| `output` | OUTPUT | object | yes | Success: `status=ok`, `http_status`, `data`. Errors raise a BPMN error for the error boundary event (INVALID_INPUT, AUTH_FAILED, RATE_LIMITED, HTTP_<status>, REQUEST_FAILED); secrets are redacted. |

## Operations

| operation | HTTP | Path | Notes |
|---|---|---|---|
| `project_get` | GET | `/components/show` | Official HTTP |
| `project_create` | POST | `/projects/create` | query `name`, `project` (key), `organization` (SonarCloud), optional `visibility`, `mainBranch` |
| `issue_search` | GET | `/issues/search` | queryFrom includes componentKeys, severities, impactSeverities, ps, p |
| `measures_get` | GET | `/measures/component` | Official HTTP |
| `qualitygate_get` | GET | `/qualitygates/show` | Official HTTP |
| `qualitygate_status` | GET | `/qualitygates/project_status` | Official HTTP |
| `listen` | ingress | — | BPMN message event (no socket) |

## Configuration parameters

| Name | Type | Required | Secret | Purpose |
|---|---|---|---|---|
| `operation` | select | yes | no | Chooses the row above |
| `token` | password | yes | yes | SonarQube / SonarCloud user token. Authorization: Bearer. |
| `base_url` | string | no | no | SonarQube Server URL without `/api`. Empty = `https://sonarcloud.io`. |
| `component` | string | no | no | Default project key for `project_get` / `measures_get` when INPUT omits `component`. |
| `organization` | string | no | no | Default org for `project_create` / `issue_search`. |
| `timeout_seconds` | number | no | no | Default 25 |

**AI Agent tools:** INPUT lists every query param in `api_spec.json` `queryFrom` (including `id`, `organization`, `projects`, `projectId`, `analysisId`). `mergeSonarQubeInputs` maps `projectKey` → `component` or `project` per operation.

## How it works

1. `OfficialApiHandlers` registers this folder key because `api_spec.json` exists.
2. Path `{placeholders}` are taken from input or config and URL-encoded.
3. JDK HTTP via `DataSupport.exchange`. Response cap is engine-bounded.
4. HTTP 2xx → `status=ok`. Other statuses → `error` plus `http_status`.
5. Ingress operations return an instruction to use BPMN message events — no HTTP call.

## Security & legal

- Secrets only from `configurationParameters.token`. Never logged, never returned.
- [SonarQube Web API docs](https://docs.sonarsource.com/sonarqube-server/extension-guide/web-api). Terms: https://www.sonarsource.com/legal/sonarqube/terms/.
- SonarSource terms. SonarCloud host is packaged; self-host uses the same /api paths.
- Official API only. No HTML scrape. File fields use type `file` (ref-id).
