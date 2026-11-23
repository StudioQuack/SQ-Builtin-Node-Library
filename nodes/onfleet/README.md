# Onfleet

Official Onfleet node (`templateId` from `node_definition.json` `_id`, override `SQ_TEMPLATE_ID_ONFLEET`). Dispatched by `OfficialApiNode` from `api_spec.json`. No custom Java handler.

## Official API

- **Name / version:** Onfleet REST v2. Researched 2026-08-22.
- **Origin:** `https://onfleet.com/api/v2`
- **Docs:** https://docs.onfleet.com/reference/authentication
- **Auth:** HTTP Basic with `api_key` as **username** and empty password (`auth.basicKeyPosition: "username"`). Never on INPUT. Never logged.

## Operations

See `api_spec.json` for tasks, destinations, workers. `trigger` is BPMN ingress (no socket).

## Ports

| Port | Direction | Type | Notes |
|---|---|---|---|
| `input` | INPUT | object | Per-operation fields. No secrets. |
| `output` | OUTPUT | object | `{status, http_status, data}`. |

FUNCTION ports: none.

## Configuration parameters

| Name | Type | Secret | Purpose |
|---|---|---|---|
| `operation` | select | no | Official verb. |
| `api_key` | password | **yes** | Basic username. |
| `timeout_seconds` | number | no | Default `25`. |

## Legal

Onfleet merchant ToS. Driver/recipient PII — do not log. Official API only.

## Implementation notes

- JDK HTTP via `OfficialApiNode`.
- HTTP 4xx/5xx → a BPMN error. Never log `api_key`.
