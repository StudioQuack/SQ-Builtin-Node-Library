# SerpAPI

Official-API node driven by `OfficialApiNode` + `api_spec.json`.

| | |
|---|---|
| Folder | `nodes/serpapi` |
| `templateId` | `8a44e495-80c1-5ff2-9bfe-81c3f880cd4e` |
| Env override | `SQ_TEMPLATE_ID_SERPAPI` |
| Category | Data & Analytics |
| Official docs | https://serpapi.com/search-api |
| Auth | `query` — config `api_key` |

## Operations

| operation | HTTP | Path |
|---|---|---|
| `google` | http GET | `/search?engine=google&output=json` |
| `google_news` | http GET | `/search?engine=google_news&output=json` |
| `google_images` | http GET | `/search?engine=google_images&output=json` |
| `google_scholar` | http GET | `/search?engine=google_scholar&output=json` |
| `google_maps` | http GET | `/search?engine=google_maps&type=search&output=json` (`type=search` is mandatory for `q` searches) |
| `google_shopping` | http GET | `/search?engine=google_shopping&output=json` |
| `bing` | http GET | `/search?engine=bing&output=json` |

## Ports

One INPUT `input`, one OUTPUT `output`. Output is `{status, http_status, data}`. Errors raise a BPMN error for the error boundary event (INVALID_INPUT, AUTH_FAILED, RATE_LIMITED, HTTP_<status>, REQUEST_FAILED); secrets are redacted.

## Legal

SerpApi terms. Destination search-engine terms remain the operator's. Official HTTP peer is serpapi.com. Do not request output=html.

Operator accepts the provider terms. Official HTTP only — no HTML scrape. Secrets are StudioQuack bindings; never logged.
