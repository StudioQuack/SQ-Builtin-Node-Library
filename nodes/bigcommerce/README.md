# BigCommerce

Catalog v3, Orders v2, Customers v3 via `OfficialApiNode`. Auth header `X-Auth-Token`. Host `https://api.bigcommerce.com/stores/{store_hash}/…`.

Docs: [Catalog products](https://developer.bigcommerce.com/docs/rest-catalog/products) · [Update an order](https://developer.bigcommerce.com/docs/rest-management/orders#update-an-order)

`product_create` takes official product fields (`name`, `type`, `weight`, `price`, `sku`, `categories`, …). `order_update` takes `status_id`, `staff_notes`, `customer_message`, `billing_address`, `shipping_addresses`, `is_deleted`.

## Operations

| operation | HTTP | Path |
|---|---|---|
| `product_list` | GET | `/{store_hash}/v3/catalog/products` |
| `product_get` | GET | `/{store_hash}/v3/catalog/products/{product_id}` |
| `product_create` | POST | `/{store_hash}/v3/catalog/products` |
| `order_list` | GET | `/{store_hash}/v2/orders` |
| `order_get` | GET | `/{store_hash}/v2/orders/{id}` |
| `order_update` | PUT | `/{store_hash}/v2/orders/{id}` |
| `customer_get` | GET | `/{store_hash}/v3/customers` |
| `trigger` | ingress | — |

## Ports

`input` / `output`. Errors raise a BPMN error for the error boundary event (INVALID_INPUT, AUTH_FAILED, RATE_LIMITED, HTTP_<status>, REQUEST_FAILED); secrets are redacted.

## Config

`operation`, `access_token` (password), `store_hash`, `timeout_seconds`.
