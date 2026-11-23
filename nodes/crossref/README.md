# Crossref

Official-API node driven by `OfficialApiNode` + `api_spec.json`.

| | |
|---|---|
| Folder | `nodes/crossref` |
| `templateId` | `4a1f4a61-8090-5f65-8007-11b4c57513ef` |
| Env override | `SQ_TEMPLATE_ID_CROSSREF` |
| Category | News & Reference |
| Official docs | https://www.crossref.org/documentation/retrieve-metadata/rest-api/ |
| Auth | `none` — polite `mailto` query; Metadata Plus header is not wired |

## Operations

| operation | HTTP | Path |
|---|---|---|
| `works_search` | http GET | `/works` |
| `work_get` | http GET | `/works/{doi}` |
| `funders` | http GET | `/funders` |

## Ports

One INPUT `input`, one OUTPUT `output`. Output is `{ status, http_status, data }` or `{ error }`.

## Legal

Crossref REST. Polite pool via mailto (https://www.crossref.org/documentation/retrieve-metadata/rest-api/access-and-authentication/). Metadata Plus uses `Crossref-Plus-API-Token: Bearer` — not wired here. Metadata licence; Plus for heavy commercial.

Operator accepts the provider terms. Official HTTP only — no HTML scrape. Secrets are StudioQuack bindings; never logged.
