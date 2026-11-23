# GoCardless

Handler: `OfficialApiNode`. Docs: https://developer.gocardless.com/api-reference

- **Auth:** Bearer `access_token`. Header `GoCardless-Version: 2015-07-06`.
- Create ops nest official wrappers: `customers`, `mandates`, `payments` via `bodyMap`.

Errors raise a BPMN error for the error boundary event (INVALID_INPUT, AUTH_FAILED, RATE_LIMITED, HTTP_<status>, REQUEST_FAILED); secrets are redacted.
