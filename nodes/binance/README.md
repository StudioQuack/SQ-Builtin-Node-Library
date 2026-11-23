# Binance

Binance Spot REST at `https://api.binance.{site}`. Docs: [Spot REST](https://developers.binance.com/docs/binance-spot-api-docs/rest-api)

Auth: header `X-MBX-APIKEY` from config `api_key`. Signed endpoints (`account`, `order_*`) also need query `timestamp`, `signature` (HMAC SHA256 of the query string with the secret key). OfficialApiNode **cannot** compute the signature — the caller must supply it. `signature` is not a StudioQuack secret binding.

`order_create` takes official params: `symbol`, `side`, `type`, `quantity` / `quoteOrderQty`, `price`, `timeInForce`, `newClientOrderId`, `stopPrice`, `recvWindow`.

## Operations

| operation | HTTP | Path |
|---|---|---|
| `ticker` | GET | `/api/v3/ticker/price` |
| `klines` | GET | `/api/v3/klines` |
| `account` | GET | `/api/v3/account` |
| `order_create` | POST | `/api/v3/order` |
| `order_cancel` | DELETE | `/api/v3/order` |
| `order_get` | GET | `/api/v3/order` |
| `listen_key` | POST | `/api/v3/userDataStream` |
| `trigger` | ingress | — |

## Ports

`input` / `output`. Errors raise a BPMN error for the error boundary event (INVALID_INPUT, AUTH_FAILED, RATE_LIMITED, HTTP_<status>, REQUEST_FAILED); secrets are redacted.

## Config

`operation`, `api_key` (password), `site` (e.g. `com`), `timeout_seconds`.
