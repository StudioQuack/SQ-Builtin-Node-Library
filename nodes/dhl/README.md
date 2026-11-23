# DHL

Handler: `OfficialApiNode` via `api_spec.json`.

- **Tracking docs:** https://developer.dhl.com/api-reference/shipment-tracking — GET `https://api-eu.dhl.com/track/shipments` with `DHL-API-Key`.
- **MyDHL docs:** https://developer.dhl.com/api-reference/dhl-express-mydhl-api — POST `/shipments` and `/rates` with Basic `api_key:api_secret` (MyDHL API key and secret from the Developer Portal app). The DHL Express account number is not an auth value; send it in `accounts` (`[{"typeCode":"shipper","number":"123456789"}]`).

`shipment_create` / `rate_get` take official MyDHL top-level objects (`plannedShippingDateAndTime`, `productCode`, `accounts`, `customerDetails`, `content` / `packages`). Nested address lines stay inside `customerDetails` as the API defines them.

Errors raise a BPMN error for the error boundary event (INVALID_INPUT, AUTH_FAILED, RATE_LIMITED, HTTP_<status>, REQUEST_FAILED); secrets are redacted.
