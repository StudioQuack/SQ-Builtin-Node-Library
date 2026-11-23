# Elasticsearch

Handler: `OfficialApiNode`. Docs: https://www.elastic.co/docs/api/doc/elasticsearch/operation/operation-create

- **Auth:** `Authorization: ApiKey` from config `api_key`.
- **Base:** config `base_url` (replaces hardcoded localhost).

`document.create` PUT `/{index}/_doc/{id}` merges INPUT `document` at the JSON root (Index API body). Query: `refresh`, `routing`, `op_type`, `pipeline`.

`document.update` uses official `{doc, doc_as_upsert, script, upsert}`. `document.search` uses `query`, `size`, `from`, `sort`, `_source`, `aggs`.

Errors raise a BPMN error for the error boundary event (INVALID_INPUT, AUTH_FAILED, RATE_LIMITED, HTTP_<status>, REQUEST_FAILED); secrets are redacted.
