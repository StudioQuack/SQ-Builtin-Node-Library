# ERPNext

Handler: `OfficialApiNode`. Docs: https://docs.frappe.io/framework/user/en/api/rest

- **Auth:** `Authorization: token <api_key>:<api_secret>` — put `key:secret` in config `api_key`.
- **Base:** config `base_url`.

`resource_create` / `resource_update` merge INPUT `doc` at the JSON root (Frappe document fields). Path uses `{doctype}` / `{name}`. List uses official query `fields`, `filters`, `or_filters`, `limit_page_length`, `limit_start`, `order_by`.

Errors raise a BPMN error for the error boundary event (INVALID_INPUT, AUTH_FAILED, RATE_LIMITED, HTTP_<status>, REQUEST_FAILED); secrets are redacted.
