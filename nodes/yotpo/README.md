# Yotpo

Official Yotpo Reviews/UGC node (`templateId` from `node_definition.json` `_id`, override `SQ_TEMPLATE_ID_YOTPO`). Dispatched by `OfficialApiNode` from `api_spec.json`. No custom Java handler.

## Official API

- **Name / version:** Yotpo Core API `api.yotpo.com`. Researched 2026-08-22.
- **Origin:** `https://api.yotpo.com`
- **Docs:** https://apidocs.yotpo.com/reference/retrieve-all-reviews · https://core-api.yotpo.com/v1.0/reference/yotpo-authentication
- **Auth:** Query `utoken` minted outside StudioQuack via `POST /oauth/token`. Config `utoken` is sent as query `utoken`.

`loyalty_adjust` overrides the host to `https://loyalty.yotpo.com` (`POST /api/v2/points/adjust`) and authenticates with the separate Loyalty credentials: headers `X-GUID` (`loyalty_guid`) and `X-API-KEY` (`loyalty_api_key`). Docs: https://docs.yotpo.com/reference/loyalty-authentication

## Operations

See `api_spec.json`. Moderate UGC before republish.

## Ports

| Port | Direction | Type | Notes |
|---|---|---|---|
| `input` | INPUT | object | Per-operation fields. No secrets. |
| `output` | OUTPUT | object | `{status,http_status,data}`. Errors raise a BPMN error (INVALID_INPUT, AUTH_FAILED, RATE_LIMITED, HTTP_<status>, REQUEST_FAILED); secrets are redacted. |

FUNCTION ports: none.

## Configuration parameters

| Name | Type | Secret | Purpose |
|---|---|---|---|
| `operation` | select | no | Official verb. |
| `utoken` | password | **yes** | Query `utoken` on review list. Minted outside StudioQuack. |
| `app_key` | string | no | App key / store id (path). |
| `loyalty_guid` | password | **yes** | Loyalty GUID, header `X-GUID` on `loyalty_adjust`. |
| `loyalty_api_key` | password | **yes** | Loyalty API key, header `X-API-KEY` on `loyalty_adjust`. |
| `timeout_seconds` | number | no | Default `25`. |

## Legal

Yotpo merchant ToS. Official API only. Minimize reviewer PII in logs.

## Implementation notes

- JDK HTTP via `OfficialApiNode`.
- HTTP 4xx/5xx raise a BPMN error (AUTH_FAILED, RATE_LIMITED, HTTP_<status>, REQUEST_FAILED). Never log tokens.

## Review / order bodies

`review_create` takes Yotpo widget fields (`sku`, `email`, `review_content`, `review_score`, `product_title`, `product_url`, …) per https://apidocs.yotpo.com/reference/create-review. Config `app_key` is sent as body `appkey`. `order_send` takes `orders` (Core v3 array). `loyalty_adjust` takes `customer_email`, `customer_id`, `point_adjustment_amount`, `history_title` and is sent to loyalty.yotpo.com.
