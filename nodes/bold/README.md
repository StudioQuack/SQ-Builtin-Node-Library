# Bold

Bold Subscriptions API at `https://api.boldcommerce.com/subscriptions/v1/shops/{shop}/subscriptions/{subscription_id}`. Docs: [Subscriptions](https://developer.boldcommerce.com/api/subscriptions)

Auth: Bearer `api_token`. Header `Bold-API-Version-Date` from config `api_version_date`.

`subscription_update` PATCH nests `subscription_status`, `next_order_datetime`, `note`, `order_note`, `payment_method_token` under `subscription`. `subscription` remains an escape hatch.

## Operations

| operation | HTTP | Path |
|---|---|---|
| `subscription_get` | GET | `/subscriptions/v1/shops/{shop}/subscriptions/{subscription_id}` |
| `subscription_update` | PATCH | `/subscriptions/v1/shops/{shop}/subscriptions/{subscription_id}` |
| `trigger` | ingress | — |

## Ports

`input` / `output`. Errors raise a BPMN error for the error boundary event (INVALID_INPUT, AUTH_FAILED, RATE_LIMITED, HTTP_<status>, REQUEST_FAILED); secrets are redacted.

## Config

`operation`, `api_token` (password), `shop`, `api_version_date`, `timeout_seconds`.
