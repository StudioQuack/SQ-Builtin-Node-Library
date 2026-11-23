# FreshBooks

Handler: `OfficialApiNode`. Docs: https://www.freshbooks.com/api/clients

Write ops nest official `{client|invoice|payment|expense}` objects. Flattened fields map via `bodyMap`. Keep the wrapper objects as escape hatches.

Errors raise a BPMN error for the error boundary event (INVALID_INPUT, AUTH_FAILED, RATE_LIMITED, HTTP_<status>, REQUEST_FAILED); secrets are redacted.
