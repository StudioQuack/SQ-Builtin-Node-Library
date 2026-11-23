# SearXNG

Official-API node driven by `OfficialApiNode` + `api_spec.json`.

| | |
|---|---|
| Folder | `nodes/searxng` |
| `templateId` | `0c56b80b-c5d7-5654-a345-d32a8738b5b4` |
| Env override | `SQ_TEMPLATE_ID_SEARXNG` |
| Category | Data & Analytics |
| Official docs | https://docs.searxng.org/dev/search_api.html |
| Auth | `none` — config `api_key` |

## Operations

| operation | HTTP | Path |
|---|---|---|
| `search` | http GET | `{instance_host}/search?format=json` |

## Ports

One INPUT `input`, one OUTPUT `output`. Output is `{status, http_status, data}`. Errors raise a BPMN error for the error boundary event (INVALID_INPUT, AUTH_FAILED, RATE_LIMITED, HTTP_<status>, REQUEST_FAILED); secrets are redacted.

## Legal

Official instance JSON API. JSON is off by default. You must point at an instance you are allowed to automate — public instances typically forbid bots. No default public instance.

Operator accepts the provider terms. Official HTTP only — no HTML scrape. Secrets are StudioQuack bindings; never logged.
