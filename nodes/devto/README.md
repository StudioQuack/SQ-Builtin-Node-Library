# DEV.to Search

## Overview

In-process StudioQuack builtin (`templateId` `9f73791e-776d-44ef-8c5e-3e459b776410`, override `SQ_TEMPLATE_ID_DEVTO`). Handler: `DevtoNode`. It lists public DEV.to articles through the official [Forem REST API](https://developers.forem.com/api) at `https://dev.to/api`. Forem v0 is deprecated; v1 is current (`Accept: application/vnd.forem.api-v1+json`). This Java handler still omits that Accept header (no `api_spec.json`; Java is out of this task's scope).

Public list endpoints work with no key. An optional instance `api_key` raises rate limits. Failures are `output.error` on the **output** port (there is no error OUTPUT port). Needs outbound internet egress to `dev.to`.

Provider docs: [Forem API](https://developers.forem.com/api), [GET /api/articles](https://developers.forem.com/api/v0/articles/get-articles), [DEV.to API key](https://dev.to/settings/extensions).

## Ports

| Port | Direction | Type | Required | Schema / flow |
|---|---|---|---|---|
| `input` | INPUT | object | yes | `inputVariables.input` (or `inputList` with `targetPortValue=input`). No field is required: `action` defaults to `articles`. Do not put `api_key` here. |
| `output` | OUTPUT | object | yes | Success: `{status, articles_returned, articles}`. Failure: `{error}` only. |

### Input fields

Incoming data chooses the Forem list and optional filters. Tag/username/top/state/collection filters apply only to `articles`; `latest` sends only `page` and `per_page`.

| Field | Type | Required | Default | Accepted values | How it is used |
|---|---|---|---|---|---|
| `action` | string | no | `articles` | `articles` \| `latest` (lowercase, case-sensitive) | `articles` → `GET /api/articles`. `latest` → `GET /api/articles/latest`. Anything else fails with no HTTP call. |
| `tag` | string | no | — | slug, or several slugs split on commas / whitespace / `+` | Normalized (lowercase, stripped). One slug → Forem `tag` (combines with `top`). Several slugs → Forem `tags`. Ignored when `tags` is also present (`tags` wins). Ignored on `latest`. |
| `tags` | string | no | — | same normalization | When present, sent as Forem `tags` joined with `", "` and `tag` is not sent. Ignored on `latest`. |
| `tags_exclude` | string | no | — | same normalization | Sent as Forem `tags_exclude` when at least one slug remains. Ignored on `latest`. |
| `username` | string | no | — | Forem author username | Query param `username`. Required for `state` to be sent. Ignored on `latest`. |
| `top` | number | no | omitted | positive integer (days) | Forem `top` (most reactions in the last N days). Combinable with `tag`/`tags`, not with `state`. Omitted when missing or 0. Ignored on `latest`. |
| `per_page` | number | no | config `default_per_page` (30) | positive int | `articles` cap 1000; `latest` cap 100. Values above the cap are truncated, not rejected. |
| `page` | number | no | `1` | 1-based integer | Sent on both actions. |
| `state` | string | no | — | `fresh` \| `rising` \| `all` | Forem allows `state` **only** with `username`. Without `username` the node skips `state` (avoids a Forem 404) and logs a helper warning. Do not pair with `top`. Ignored on `latest`. |
| `collection_id` | number | no | omitted | positive integer | Forem series id. Omitted when missing or 0. Ignored on `latest`. |

### Output fields

| Field | Type | When present | Meaning |
|---|---|---|---|
| `status` | string | success | Exact `ok`. Absent on failure. |
| `articles_returned` | number | success | `articles.length` for this page (not a site-wide total). |
| `articles` | array | success | Mapped article objects. Missing strings default to `""`, missing counts to `0`. |
| `error` | string | failure | See error mapping. Never contains `api_key`. |

Each `articles[]` item (`action=articles`): `id`, `title`, `description`, `url`, `canonical_url`, `published_at`, `positive_reactions_count`, `comments_count`, `reading_time_minutes`, `tag_list`, `author_name`, `author_username`.

`action=latest` omits `canonical_url`, `reading_time_minutes`, and `author_username`.

## Configuration parameters

| Name | Type | Required | Default | Accepted values | Purpose |
|---|---|---|---|---|---|
| `api_key` | string | no | `""` | Forem key from [dev.to/settings/extensions](https://dev.to/settings/extensions) | When non-blank, sent as header **`api-key`** (not `Authorization`, not Bearer). Public lists work with this empty; a key raises rate limits. Never logged or returned. |
| `timeout_seconds` | number | no | `25` | positive number; non-positive → 25 | Per-request HTTP timeout. Keep at or below the daemon stateful budget. |
| `default_per_page` | number | no | `30` | positive number | Page size when input `per_page` is omitted. Still clamped to the action cap (1000 / 100). |

## How it works

1. The catalog registers `DevtoNode` under `9f73791e-776d-44ef-8c5e-3e459b776410` (or `SQ_TEMPLATE_ID_DEVTO`).
2. `handle` reads instance config and the `input` port. Blank/`null` `action` becomes `articles`.
3. Tag fields are split on `[,\s+]+`, lowercased, and emptied dropped. `tags` present → Forem `tags`. Else one `tag` slug → Forem `tag`. Else several `tag` slugs → Forem `tags`.
4. `GET https://dev.to/api/articles` or `…/articles/latest` through the shared JDK `HttpClient` (`DataNodeSupport.get`: HTTP/1.1, redirects, request timeout, 8 MiB body cap).
5. Headers: `User-Agent: StudioQuack/1.0` always; `api-key: <key>` only when the instance key is non-blank.
6. Pagination is 1-based `page` + `per_page`. There is no cursor and no `after` token.
7. Failures stay on `output.error`:
   - `Unknown action: {action}. Use articles or latest.`
   - `DEV.to API Error: HTTP {status} for url: {url}`
   - `DEV.to API Error: timed out after {n}s`
   - `DEV.to API Error: {message}` (transport)
   - `Error: {simpleName}: {message}`
   Transport messages that contain the literal key are redacted to `***`.

## Security & legal

- The API key is read only from `configurationParameters.api_key`. It is never logged, never returned, never placed in query strings, and is redacted from error text if a transport message echoes it.
- [DEV Community Terms](https://dev.to/terms) and the [Forem API](https://developers.forem.com/api) terms apply. The customer owns the key.
- Unauthenticated public lists are rate-limited more tightly than authenticated calls. Space retries; this node does not sleep on 429.
- This node uses the official Forem REST API, not HTML scraping.

## Examples

### Popular articles by tag

Input:

```json
{
  "action": "articles",
  "tag": "python",
  "top": 7,
  "per_page": 10,
  "page": 1
}
```

Output (shape):

```json
{
  "status": "ok",
  "articles_returned": 10,
  "articles": [
    {
      "id": 123,
      "title": "…",
      "description": "…",
      "url": "https://dev.to/…",
      "canonical_url": "https://…",
      "published_at": "2026-08-01T12:00:00Z",
      "positive_reactions_count": 42,
      "comments_count": 8,
      "reading_time_minutes": 6,
      "tag_list": ["python", "ai"],
      "author_name": "Ada",
      "author_username": "ada"
    }
  ]
}
```

### Latest feed (no tag filters)

Input: `{"action":"latest","per_page":20,"page":1}` → same success envelope; each article omits `canonical_url`, `reading_time_minutes`, and `author_username`.
