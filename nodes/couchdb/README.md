# CouchDB

Official CouchDB HTTP API (default `http://localhost:5984`). Docs: [Documents](https://docs.couchdb.org/en/stable/api/document/common.html) · [Find](https://docs.couchdb.org/en/stable/api/database/find.html)

Auth: HTTP Basic (`username` + `password`).

`doc.put` merges INPUT `document` at the JSON root; named `_id` / `_rev` win on conflict. `doc.find` takes official Mango fields: `selector`, `fields`, `sort`, `bookmark`, `use_index`, `execution_stats`.

## Operations

| operation | HTTP | Path |
|---|---|---|
| `doc.get` | GET | `/{db}/{docid}` |
| `doc.put` | PUT | `/{db}/{docid}` |
| `doc.delete` | DELETE | `/{db}/{docid}` |
| `doc.find` | POST | `/{db}/_find` |
| `db.allDocs` | GET | `/{db}/_all_docs` |
| `trigger.listen` | ingress | — |

## Ports

`input` / `output`. Errors raise a BPMN error for the error boundary event (INVALID_INPUT, AUTH_FAILED, RATE_LIMITED, HTTP_<status>, REQUEST_FAILED); secrets are redacted.

## Config

`operation`, `username`, `password` (password), `timeout_seconds`.
