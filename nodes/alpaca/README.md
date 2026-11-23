# Alpaca

Official Alpaca Trading API v2 + Market Data node (`templateId` from `node_definition.json` `_id`, override `SQ_TEMPLATE_ID_ALPACA`). Dispatched by `OfficialApiNode` from `api_spec.json`. No custom Java handler.

## Official API

- **Name / version:** Trading API v2 + Market Data. Researched 2026-08-22.
- **Trading origin:** paper default `https://paper-api.alpaca.markets` (live `https://api.alpaca.markets`)
- **Market Data origin:** per-op `baseUrl` `https://data.alpaca.markets` on `bars` (and other market-data ops if added)
- **Docs:** https://docs.alpaca.markets · https://docs.alpaca.markets/us/docs/market-data-faq · https://docs.alpaca.markets/reference/stockbars
- **Auth:** `APCA-API-KEY-ID` (header auth) plus extraHeader `APCA-API-SECRET-KEY: {api_secret}`. Never on INPUT. Never logged.

Broker-dealer. PDT rules. Not unlicensed advisory. `trigger` is BPMN ingress — do not build a trade-updates WebSocket.

## Operations

See `api_spec.json`. Trading ops stay on the trading host. `bars` uses `https://data.alpaca.markets`.

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
| `api_key_id` | password | **yes** | APCA-API-KEY-ID. |
| `api_secret` | password | **yes** | APCA-API-SECRET-KEY extraHeader. |
| `timeout_seconds` | number | no | Default `25`. |

## Legal

Alpaca customer agreement. Official API only. Not investment advice.

## Implementation notes

- JDK HTTP via `OfficialApiNode`.
- HTTP 4xx/5xx → `{error}` on OUTPUT. Never log keys.
