# Docparser

Handler: `OfficialApiNode`. Docs: https://docparser.com/api/

- **Auth:** header `api_key`.
- `document_upload` POST `/document/upload/{parser_id}` multipart: `file` or `url`, optional `remote_id`, `filename`.
- `document_get` GET `/document/status/{parser_id}/{document_id}`.
- `parsed_data_get` GET `/results/{parser_id}/{document_id}` with `format`, `include_children`.

Errors raise a BPMN error for the error boundary event (INVALID_INPUT, AUTH_FAILED, RATE_LIMITED, HTTP_<status>, REQUEST_FAILED); secrets are redacted.
