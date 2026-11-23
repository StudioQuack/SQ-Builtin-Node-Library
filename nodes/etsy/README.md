# Etsy

Handler: `OfficialApiNode`. Docs: https://developer.etsy.com/documentation/tutorials/listings

- **Auth:** Bearer `access_token` plus header `x-api-key: {api_key}:{shared_secret}`.
- `listing_create` POST `/v3/application/shops/{shop_id}/listings` as `application/x-www-form-urlencoded` with official required `quantity`, `title`, `description`, `price`, `who_made`, `when_made`, `taxonomy_id`.

Errors raise a BPMN error for the error boundary event (INVALID_INPUT, AUTH_FAILED, RATE_LIMITED, HTTP_<status>, REQUEST_FAILED); secrets are redacted.
