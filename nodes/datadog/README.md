# Datadog

StudioQuack builtin. Handler: `OfficialApiNode` + `api_spec.json`. Official [Datadog HTTP API](https://docs.datadoghq.com/api/latest/) at `https://api.{site}` (default `datadoghq.com`).

Auth: `api_key` → `DD-API-KEY`, `app_key` → `DD-APPLICATION-KEY`.

`downtime_schedule` ([Schedule a downtime](https://docs.datadoghq.com/api/latest/downtimes/)) takes purpose-oriented fields (`scope`, `message`, `monitor_id` / `monitor_tags`, `start`, `end`, `recurrences`) and nests them under JSON:API `data.attributes`. `incident_create` ([Create an incident](https://docs.datadoghq.com/api/latest/incidents/create-an-incident/)) takes `title`, `customer_impacted`, `fields`, …. `data` remains an escape hatch for a prebuilt JSON:API document.

`listen` is ingress: use a BPMN message event.

## Ports

| Port | Direction | Data flow |
|---|---|---|
| `input` | INPUT | Per-operation fields. Never put `api_key` or `app_key` here. |
| `output` | OUTPUT | `{status, http_status, data}`. Errors raise a BPMN error for the error boundary event (INVALID_INPUT, AUTH_FAILED, RATE_LIMITED, HTTP_<status>, REQUEST_FAILED); secrets are redacted. |

## Operations

| operation | HTTP | Path |
|---|---|---|
| `metric_submit` | POST | `/api/v2/series` |
| `event_post` | POST | `/api/v1/events` |
| `monitor_get` | GET | `/api/v1/monitor/{monitor_id}` |
| `monitor_list` | GET | `/api/v1/monitor` |
| `monitor_mute` | POST | `/api/v1/monitor/{monitor_id}/mute` |
| `downtime_schedule` | POST | `/api/v2/downtime` |
| `incident_create` | POST | `/api/v2/incidents` |
| `incident_get` | GET | `/api/v2/incidents/{incident_id}` |
| `log_search` | POST | `/api/v2/logs/events/search` |
| `listen` | ingress | — |

## Configuration

`operation`, `api_key` (password), `app_key` (password), `site` (default `datadoghq.com`), `timeout_seconds`.

## Legal

https://www.datadoghq.com/legal/terms/ Official API only. Never log keys.
