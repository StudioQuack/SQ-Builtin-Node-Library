# Dictionary API (Merriam-Webster)

Handler: `OfficialApiNode`. Docs: https://dictionaryapi.com/products/api-collegiate-dictionary

GET `/api/v3/references/{ref}/json/{word}?key=`. Config `key` is the query auth. `ref` defaults to `collegiate`.

Errors raise a BPMN error for the error boundary event (INVALID_INPUT, AUTH_FAILED, RATE_LIMITED, HTTP_<status>, REQUEST_FAILED); secrets are redacted.
