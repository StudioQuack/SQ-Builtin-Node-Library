# Wolfram Alpha

Official-API node driven by `OfficialApiNode` + `api_spec.json`.

| | |
|---|---|
| Folder | `nodes/wolfram-alpha` |
| `templateId` | `46b7eedb-2e6c-5459-ba19-13477c5fccae` |
| Env override | `SQ_TEMPLATE_ID_WOLFRAM_ALPHA` |
| Category | News & Reference |
| Official docs | https://products.wolframalpha.com/api/documentation (Full Results); https://products.wolframalpha.com/spoken-results-api/documentation (Spoken) |
| Auth | `query` — config `appid` |

## Operations

| operation | HTTP | Path |
|---|---|---|
| `query` | http GET | `/v2/query?output=json` |
| `spoken` | http GET | `/v1/spoken` |
| `short_answer` | http GET | `/v1/result` |

## Ports

One INPUT `input`, one OUTPUT `output`. Output is `{ status, http_status, data }` or `{ error }`.

## Legal

Wolfram Alpha Full Results API v2 plus Spoken Results v1 and Short Answers v1. AppID query. Results licensed — no unlimited republish.

Operator accepts the provider terms. Official HTTP only — no HTML scrape. Secrets are StudioQuack bindings; never logged.
