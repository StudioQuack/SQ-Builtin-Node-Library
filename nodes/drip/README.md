# Drip

Handler: `OfficialApiNode`. Docs: https://developer.drip.com/#create-or-update-a-subscriber

Write ops take purpose fields nested into the first wrapper item: `email`/`first_name`/`last_name` → `subscribers[0].*`, `email`/`tag` → `tags[0].*`, `email`/`action`/`properties` → `events[0].*`. Each wrapper stays a one-object escape hatch.

Errors raise a BPMN error for the error boundary event (INVALID_INPUT, AUTH_FAILED, RATE_LIMITED, HTTP_<status>, REQUEST_FAILED); secrets are redacted.
