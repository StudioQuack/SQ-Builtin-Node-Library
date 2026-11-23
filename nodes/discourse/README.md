# Discourse

Handler: `OfficialApiNode`. Docs: https://docs.discourse.org/

Config `base_url` is the site origin. Auth: headers `Api-Key` + `Api-Username` from `api_key` / `api_username`.

Errors raise a BPMN error for the error boundary event (INVALID_INPUT, AUTH_FAILED, RATE_LIMITED, HTTP_<status>, REQUEST_FAILED); secrets are redacted.
