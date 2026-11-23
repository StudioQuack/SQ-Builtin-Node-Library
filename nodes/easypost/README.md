# EasyPost

Handler: `OfficialApiNode` via `api_spec.json`.

- **Docs:** https://docs.easypost.com/docs/shipments
- **Base:** `https://api.easypost.com/v2`
- **Auth:** HTTP Basic, API key as username (empty password).

Write operations send the official wrappers (`address`, `parcel`, `shipment`, `tracker`). Flattened fields nest via `bodyMap`.

| operation | HTTP | Required |
|---|---|---|
| `address_create` | POST /addresses | street1, city, state, zip, country |
| `parcel_create` | POST /parcels | weight (ounces) |
| `shipment_create` | POST /shipments | to_address, from_address, parcel |
| `buy` | POST /shipments/{id}/buy | id, rate |
| `tracker_create` | POST /trackers | tracking_code |
| `trigger` | ingress | |

Errors raise a BPMN error for the error boundary event (INVALID_INPUT, AUTH_FAILED, RATE_LIMITED, HTTP_<status>, REQUEST_FAILED); secrets are redacted.
