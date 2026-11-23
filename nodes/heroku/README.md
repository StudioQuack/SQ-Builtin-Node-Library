# Heroku

## Overview

StudioQuack builtin (`templateId` from `node_definition.json` `_id`, override `SQ_TEMPLATE_ID_HEROKU`). Handler: `OfficialApiNode` + `api_spec.json`. Calls the official [Heroku Platform API](https://devcenter.heroku.com/articles/platform-api-reference) at `https://api.heroku.com`. V3 sends `Accept: application/vnd.heroku+json; version=3` via spec `extraHeaders`.

Inbound provider webhooks are **not** handled by this node. `listen` / `trigger` operations are `kind: ingress`: use a BPMN message start, intermediate catch, or boundary message event. StudioQuack webhook ingress correlates onto those events.

## Ports

| Port | Direction | Type | Required | Schema / flow |
|---|---|---|---|---|
| `input` | INPUT | object | yes | Per-operation fields. Never put `api_key` here. |
| `output` | OUTPUT | object | yes | Success: `status=ok`, `http_status`, `data`. Errors raise a BPMN error (INVALID_INPUT, AUTH_FAILED, RATE_LIMITED, HTTP_<status>, REQUEST_FAILED); secrets are redacted. |

## Operations

| operation | HTTP | Path | Notes |
|---|---|---|---|
| `app_get` | GET | `/apps/{app_id_or_name}` | Official HTTP |
| `app_list` | GET | `/apps` | Official HTTP |
| `app_create` | POST | `/apps` | Official HTTP |
| `dyno_create` | POST | `/apps/{app_id_or_name}/dynos` | Official HTTP |
| `dyno_restart` | DELETE | `/apps/{app_id_or_name}/dynos` | Official HTTP |
| `config_get` | GET | `/apps/{app_id_or_name}/config-vars` | Official HTTP |
| `config_set` | PATCH | `/apps/{app_id_or_name}/config-vars` | Whole-body flat JSON map (`config_vars`; null deletes a key) |
| `release_list` | GET | `/apps/{app_id_or_name}/releases` | Official HTTP |
| `build_create` | POST | `/apps/{app_id_or_name}/builds` | Official HTTP |
| `listen` | ingress | — | BPMN message event (no socket) |

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | app_get | no | Official API operation. EXACT: app_get, app_list, app_create, dyno_create, dyno_restart, config_get, config_set, release_list, build_create, listen. listen is BPMN ingress, not ... |
| `api_key` | password | yes |  | yes | Heroku API key. Authorization: Bearer. Accept: application/vnd.heroku+json; version=3 is sent on every request via extraHeaders. |
| `app_id_or_name` | string | no |  | no | Default Heroku app uuid or name when INPUT app_id_or_name is empty. |
| `region` | string | no |  | no | Request-body parameter `region` for operation: app_create. |
| `stack` | string | no |  | no | Request-body parameter `stack` for operation: app_create. |
| `name` | string | no |  | no | Request-body parameter `name` for operation: app_create. |
| `time_to_live` | number | no |  | no | dyno_create time_to_live — seconds until the one-off is killed. Example: 1800. |
| `size` | string | no |  | no | dyno_create size. EXACT: standard-1x / standard-2x / performance-m / performance-l / eco / basic / private-s / private-m / private-l / … |
| `attach` | boolean | no |  | no | dyno_create attach. true attaches this request to the dyno (one-off). Default false. |
| `command` | string | no |  | no | Request-body parameter `command` for operation: dyno_create. |
| `env` | object | no |  | no | dyno_create env overlay (NAME→value) for this one-off only. |
| `type` | string | no |  | no | dyno_create type (process type). Example: web / worker / run. |
| `config_vars` | object | no |  | no | PATCH /apps/{app}/config-vars whole-body map of NAME→value (bodyMap $). Set a value to null to delete. config_set. Example: {"LOG_LEVEL":"info","OLD_VAR":null}. |
| `source_blob` | object | no |  | no | build_create source_blob {url, checksum?, version?}. url is a gzip tarball URL. Example: {"url":"https://…/source.tgz"}. |
| `timeout_seconds` | number | no | 25 | no | Connect + read timeout. Default 25. Keep at or below the daemon stateful budget (~300s). |

## How it works

1. `OfficialApiHandlers` registers this folder key because `api_spec.json` exists.
2. Path `{placeholders}` are taken from input or config and URL-encoded.
3. JDK HTTP via `DataSupport.exchange`. Response cap is engine-bounded.
4. HTTP 2xx → `status=ok`. Other statuses raise a BPMN error (`HTTP_<status>` / `AUTH_FAILED` / `RATE_LIMITED`).
5. Ingress operations return an instruction to use BPMN message events — no HTTP call.

## Security & legal

- Secrets only from `configurationParameters.api_key`. Never logged, never returned.
- [Heroku Platform API docs](https://devcenter.heroku.com/articles/platform-api-reference). Terms: https://www.heroku.com/policy/tos.
- Heroku Terms. Platform API is official. Config vars are secrets — never log.
- Official API only. No HTML scrape. File fields use type `file` (ref-id).
