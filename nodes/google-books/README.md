# Google Books

Official-API node driven by `OfficialApiNode` + `api_spec.json`.

| | |
|---|---|
| Folder | `nodes/google-books` |
| `templateId` | `9d8070a9-75f4-56a1-84f2-6bf2ebffca20` |
| Env override | `SQ_TEMPLATE_ID_GOOGLE_BOOKS` |
| Category | News & Reference |
| Official docs | https://developers.google.com/books/docs/v1/using |
| Auth | `query` — config `key` |

## Operations

| operation | HTTP | Path |
|---|---|---|
| `volume_search` | http GET | `/volumes` |
| `volume_get` | http GET | `/volumes/{volume_id}` |
| `bookshelf_list` | http GET | `/users/{user_id}/bookshelves` |
| `bookshelf_get` | http GET | `/users/{user_id}/bookshelves/{shelf_id}` |

## Ports

One INPUT `input`, one OUTPUT `output`. Output is `{ status, http_status, data }` or `{ error }`.

## Legal

Google APIs Terms. API key for public volumes. OAuth required for private bookshelves (not in this OfficialApiNode v1).

Operator accepts the provider terms. Official HTTP only — no HTML scrape. Secrets are StudioQuack bindings; never logged.
