# Brave Search

Official-API node driven by `OfficialApiNode` + `api_spec.json`.

| | |
|---|---|
| Folder | `nodes/brave-search` |
| `templateId` | `846bbff8-7e46-57bd-9ee2-68ac3c47fe7e` |
| Env override | `SQ_TEMPLATE_ID_BRAVE_SEARCH` |
| Category | Data & Analytics |
| Official docs | https://api-dashboard.search.brave.com/documentation/quickstart |
| Auth | `header` — config `api_key` |

## Operations

| operation | HTTP | Path |
|---|---|---|
| `web_search` | http GET | `/res/v1/web/search` |
| `news_search` | http GET | `/res/v1/news/search` |
| `image_search` | http GET | `/res/v1/images/search` |
| `video_search` | http GET | `/res/v1/videos/search` |
| `suggest` | http GET | `/res/v1/suggest/search` |
| `spellcheck` | http GET | `/res/v1/spellcheck/search` |
| `llm_context` | http GET | `/res/v1/llm/context` |

## Ports

One INPUT `input`, one OUTPUT `output`. Output is `{ status, http_status, data }` . Errors raise a BPMN error for the error boundary event (INVALID_INPUT, AUTH_FAILED, RATE_LIMITED, HTTP_<status>, REQUEST_FAILED); secrets are redacted.

## Legal

Brave Search API terms in the dashboard. Header X-Subscription-Token. Independent index. Do not scrape search.brave.com.

Operator accepts the provider terms. Official HTTP only — no HTML scrape. Secrets are StudioQuack bindings; never logged.
