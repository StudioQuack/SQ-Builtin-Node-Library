# NYTimes

Official-API node driven by `OfficialApiNode` + `api_spec.json`.

| | |
|---|---|
| Folder | `nodes/nytimes` |
| `templateId` | `c58b6bc8-a4ce-5990-95c6-89214e48187c` |
| Env override | `SQ_TEMPLATE_ID_NYTIMES` |
| Category | News & Reference |
| Official docs | https://developer.nytimes.com/docs/articlesearch-product/1/overview |
| Auth | `query` — config `api-key` |

## Operations

| operation | HTTP | Path |
|---|---|---|
| `article_search` | http GET | `/search/v2/articlesearch.json` |
| `top_stories` | http GET | `/topstories/v2/{section}.json` |
| `most_popular` | http GET | `/mostpopular/v2/viewed/{period}.json` |
| `archive` | http GET | `/archive/v1/{year}/{month}.json` |
| `books_list` | http GET | `/books/v3/lists.json` |

## Ports

One INPUT `input`, one OUTPUT `output`. Output is `{ status, http_status, data }` or `{ error }`.

## Legal

NYT Developer Network terms. Query api-key. No full-article scrape. Snippet/fair-use per NYT API ToS.

Operator accepts the provider terms. Official HTTP only — no HTML scrape. Secrets are StudioQuack bindings; never logged.
