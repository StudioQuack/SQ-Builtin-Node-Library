# eBay

Handler: `OfficialApiNode`. Docs: https://developer.ebay.com/api-docs/sell/inventory/resources/inventory_item/methods/createOrReplaceInventoryItem

- **Auth:** Bearer `access_token`. Header `X-EBAY-C-MARKETPLACE-ID` from config `marketplace_id`.
- `inventory_create` PUT `/sell/inventory/v1/inventory_item/{sku}` with official `product`, `availability`, `condition` (+ Content-Language).
- `fulfillment` POST shipping_fulfillment with `lineItems`, `shippedDate`, `shippingCarrierCode`, `trackingNumber`.

Errors raise a BPMN error for the error boundary event (INVALID_INPUT, AUTH_FAILED, RATE_LIMITED, HTTP_<status>, REQUEST_FAILED); secrets are redacted.
