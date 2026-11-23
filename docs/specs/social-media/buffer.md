# Buffer

- **Status:** done
- **Catalog:** `docs/catalog/social-media.md`
- **Folder when built:** `nodes/buffer/`
- **Researched:** 2026-08-20
- **Official API:** Buffer **GraphQL** API (single endpoint `https://api.buffer.com`)
- **Docs:** https://developers.buffer.com/guides/getting-started.html — Introduction https://developers.buffer.com/guides/introduction.html — Auth https://developers.buffer.com/guides/authentication.html — Data model https://developers.buffer.com/guides/data-model.html — Posts https://developers.buffer.com/guides/posts-and-scheduling.html — REST migration https://developers.buffer.com/guides/rest-migration.html
- **Auth:** api_key **or** oauth2 — secret binding: `access_token` (Bearer). Personal key from Settings → API, **or** OAuth 2.0 Authorization Code + PKCE (`https://auth.buffer.com/auth`, token `https://auth.buffer.com/token`).

## Scope

Current Buffer integrations use **GraphQL at `https://api.buffer.com`**, not the legacy REST host `api.bufferapp.com` (closed to new apps; do not register or spec `/1/updates/create.json`). Model: **Account → Organizations → Channels → Posts**. Catalog verbs `update_create` / `update_list` / `profile_list` are **retired**; replace with `post_create`, `post_list`, `channel_list` (plus `organization_list` to obtain org ids). Catalog `trigger` is omitted — no webhook API on the GraphQL docs fetched 2026-08-20. **No file-upload endpoint**: media is a public HTTPS URL on `createPost` (`assets`). Underlying destination-network ToS still apply.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `organization_list` | POST | `https://api.buffer.com` query `account { organizations { id name } }` | Resolve org ids |
| `channel_list` | POST | query `channels(input: { organizationId })` | List connected profiles |
| `post_create` | POST | mutation `createPost` | Queue, custom-schedule, or share-now |
| `post_list` | POST | query `posts(first, after, input: { organizationId, filter })` | Scheduled / sent / draft posts |

`deletePost` exists officially but is not a catalog row — omit from v1 unless product adds it.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `channel_list` | no | Chooses the row above |
| `access_token` | password | yes | empty | yes | Personal API key **or** OAuth access token. Header `Authorization: Bearer`. Never on INPUT. |
| `client_id` | string | no | empty | no | OAuth app (confidential or public) |
| `client_secret` | password | no | empty | yes | Confidential OAuth clients only |
| `refresh_token` | password | no | empty | yes | Refresh grant; **single-use** — store the new refresh token |
| `organization_id` | string | no | empty | no | Default org for channel/post queries |
| `timeout_seconds` | number | no | `25` | no | Connect + request timeout. Floor 1 s. |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | No secrets |
| `output` | OUTPUT | object — `{ status, … }` or `{ error }` | Failures never include the Bearer |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = organization_list`

No required fields. Empty input is valid.

### `operation = channel_list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `organization_id` | string | no | Else config `organization_id` |

Missing org → `{ error }` no HTTP.

### `operation = post_create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `channel_id` | string | yes | GraphQL `channelId` |
| `text` | string | yes | Post body |
| `mode` | string | yes | `addToQueue` \| `customScheduled` \| `shareNow` |
| `scheduling_type` | string | no | Default `automatic` |
| `due_at` | string | no | ISO-8601 UTC; **required** when `mode=customScheduled` |
| `media_url` | string | no | Public **HTTPS** URL for `assets: [{ image: { url } }]` or video. Buffer **does not accept uploads**. Do not PUT bytes to `api.buffer.com`. URL must stay live until publish (no expiring signed URLs) |
| `media_kind` | string | no | `image` (default) \| `video` |

A StudioQuack `file` ref **cannot** be sent as bytes; if only `file` is provided with no `media_url`, return `{ error }` explaining Buffer requires a public URL.

### `operation = post_list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `organization_id` | string | no | Else config |
| `channel_ids` | array | no | Filter |
| `status` | array | no | e.g. `scheduled`, `sent`, `draft` as schema allows |
| `first` | number | no | Page size; default 20; clamp 1–50 |
| `after` | string | no | Cursor |

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | GraphQL `data` without top-level errors / MutationError | `ok` |
| `organizations` | array | `organization_list` | `{ id, name }` |
| `channels` | array | `channel_list` | `{ id, name, service }` |
| `post` | object | `post_create` | `{ id, text, due_at }` from `PostActionSuccess` |
| `posts` | array | `post_list` | Nodes from `posts.edges` |
| `page_info` | object | `post_list` | `hasNextPage`, `endCursor` |
| `error` | string | failure | Actionable, no token. Include `MutationError.message` when present |
| `http_status` | number | HTTP error | Provider status (GraphQL often **200** with errors in body) |
| `retry_after_seconds` | number | 429 / 5xx / timeout | 60 on 429; 30 on 5xx |

Map HTTP 200 + `errors[]` or `createPost { ... on MutationError }` to `{ error }`.

## Legal

- Product / FAQ (plans, key counts, rate windows): https://buffer.com/api
- GraphQL intro: https://developers.buffer.com/guides/graphql-intro.html
- Hosting media: https://developers.buffer.com/guides/hosting-media.html
- Operator must be an organization **owner** to create API keys. API is on all current Buffer plans (Free included) with plan-specific key counts and quotas.
- **Rate limits (published per API key):** 100 requests / 15 minutes (all plans); 24-hour: 100 Free / 250 Essentials / 500 Team; 30-day: 3,000 / 7,500 / 15,000. Exceeding returns an error — backoff; do not tight-loop.
- OAuth scopes: `posts:read`, `posts:write`, `ideas:read`, `ideas:write`, `account:read`, `account:write`, `offline_access`.
- Personal API keys act on **all** orgs the account can access. Posts and channel names are customer content; destination networks’ terms still apply. No scrape of buffer.com UI.

## Implementation notes

- JDK `HttpClient` only. Always `POST https://api.buffer.com` with `Content-Type: application/json`. Body `{ "query": "...", "variables": { } }`.
- Header `Authorization: Bearer {access_token}`. Empty token → `{ error }` no HTTP.
- OAuth authorize `GET https://auth.buffer.com/auth` (PKCE `S256` required). Token/refresh `POST https://auth.buffer.com/token`. Refresh tokens are **single-use**; persist the replacement.
- GraphQL typically HTTP 200 even on business errors. Inspect top-level `errors[]` and unions (`PostActionSuccess` vs `MutationError`). Do not treat 200 as success blindly.
- `createPost` requires `text`, `channelId`, `schedulingType`, `mode`. One channel per mutation (legacy REST `profile_ids[]` is gone).
- Response body cap **1_000_000** bytes. Timeout default 25 s.
- 401 → `{ error }` prompt re-auth. 429 → `retry_after_seconds: 60`. Do not auto-retry inside the handler.
- Logs: operation, HTTP status, GraphQL error count, post/channel ids — never Bearer, never media URL query-secrets.
- Do **not** call `https://api.bufferapp.com`.
