# GNews

Official-API node driven by `OfficialApiNode` + `api_spec.json`.

| | |
|---|---|
| Folder | `nodes/gnews` |
| `templateId` | `8c41472f-661b-5708-adc5-8f6e1c33b92e` |
| Env override | `SQ_TEMPLATE_ID_GNEWS` |
| Category | News & Reference |
| Official docs | https://docs.gnews.io/ |
| Auth | `header` `X-Api-Key` — config `api_key` (provider also accepts query `apikey`) |

## Operations

| operation | HTTP | Path |
|---|---|---|
| `search` | http GET | `/search` |
| `top_headlines` | http GET | `/top-headlines` |

## Ports

One INPUT `input`, one OUTPUT `output`. Output is `{ status, http_status, data }` or `{ error }`.

## Legal

GNews.io terms. Auth is `apikey` query or `X-Api-Key` header (this node uses the header). Article copyright remains with publishers. Snippets/links only.

Operator accepts the provider terms. Official HTTP only — no HTML scrape. Secrets are StudioQuack bindings; never logged.
