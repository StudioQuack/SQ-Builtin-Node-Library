# The Guardian

Official-API node driven by `OfficialApiNode` + `api_spec.json`.

| | |
|---|---|
| Folder | `nodes/the-guardian` |
| `templateId` | `42d4d724-659c-5c6f-a1da-45a61ecb59f4` |
| Env override | `SQ_TEMPLATE_ID_THE_GUARDIAN` |
| Category | News & Reference |
| Official docs | https://open-platform.theguardian.com/documentation/ |
| Auth | `query` — config `api-key` |

## Operations

| operation | HTTP | Path |
|---|---|---|
| `search` | http GET | `/search` |
| `item_get` | http GET | `/{item_id}` |
| `tags` | http GET | `/tags` |
| `sections` | http GET | `/sections` |
| `editions` | http GET | `/editions` |

## Ports

One INPUT `input`, one OUTPUT `output`. Output is `{status, http_status, data}`. Errors raise a BPMN error for the error boundary event (INVALID_INPUT, AUTH_FAILED, RATE_LIMITED, HTTP_<status>, REQUEST_FAILED); secrets are redacted.

## Legal

Guardian Open Platform terms. Query api-key. Attribution required. Do not republish full text beyond the allowance.

Operator accepts the provider terms. Official HTTP only — no HTML scrape. Secrets are StudioQuack bindings; never logged.
