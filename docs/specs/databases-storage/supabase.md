# Supabase

- **Status:** planned
- **Catalog:** `docs/catalog/databases-storage.md`
- **Folder when built:** `nodes/supabase/`
- **Researched:** 2026-08-20
- **Official API:** PostgREST via Supabase Data REST API `https://<project_ref>.supabase.co/rest/v1/` (PostgREST 12 table verbs). Auth/API keys: publishable `sb_publishable_…` / secret `sb_secret_…` plus legacy JWT `anon` / `service_role` (legacy JWT keys **deprecated by end of 2026**).
- **Docs:** https://supabase.com/docs/guides/api — keys: https://supabase.com/docs/guides/api/api-keys — PostgREST tables: https://postgrest.org/en/v12/references/api/tables_views.html — vectors: https://supabase.com/docs/guides/ai/vector-columns
- **Auth:** `anonKey` \| `serviceRole` — secret binding: `anonKey`, `serviceRoleKey`. Prefer a **user JWT** on the request over `service_role`.

## Scope

Calls the customer’s Supabase project **HTTP APIs** (PostgREST), not Postgres wire. Keep this node **separate** from [`postgresql.md`](postgresql.md). The customer owns the project, RLS policies, and data.

Keys (send as `apikey` header):

- `anonKey`: publishable or legacy `anon` — subject to RLS.
- `serviceRole`: secret or legacy `service_role` — **bypasses RLS**. Prefer `input.userJwt` + anon key so the request runs as `authenticated`.

Do not implement Storage file CRUD in v1 (catalog ops are row/rpc/vector only). Do not use the Management API (project tokens).

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `row.create` | POST | `/rest/v1/{table}` | Insert row(s) |
| `row.get` | GET | `/rest/v1/{table}?{pk}=eq.{id}` | Single row by filters |
| `row.list` | GET | `/rest/v1/{table}?…` | Filtered list |
| `row.update` | PATCH | `/rest/v1/{table}?…` | Update matching rows |
| `row.delete` | DELETE | `/rest/v1/{table}?…` | Delete matching rows |
| `rpc.call` | POST | `/rest/v1/rpc/{fn}` | Postgres function |
| `vector.query` | POST | `/rest/v1/rpc/{fn}` | Similarity search via operator-defined SQL function (PostgREST cannot express `<=>` directly) |

Headers (all REST calls):

- `apikey: <anon or service key>`
- `Authorization: Bearer <userJwt | anon | service>` — **Prefer `userJwt` when provided**
- `Content-Type: application/json`
- `Prefer: return=representation` on write (and `resolution=merge-duplicates` only if upsert is added later)
- Schema: `Accept-Profile` / `Content-Profile` or `Accept: application/vnd.pgrst.object+json` as needed; config `schema` → `Accept-Profile` / `Content-Profile`

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `row.list` | no | Chooses the row above |
| `auth` | select | no | `anonKey` | no | `anonKey` \| `serviceRole` |
| `url` | string | yes | | no | Project URL `https://<ref>.supabase.co` (no trailing `/rest/v1`) |
| `anonKey` | password | yes when `auth=anonKey` | | yes | Publishable or legacy anon |
| `serviceRoleKey` | password | yes when `auth=serviceRole` | | yes | Secret or legacy service_role. Bypasses RLS — discourage in workflows |
| `schema` | string | no | `public` | no | Exposed schema (`Accept-Profile`) |
| `connectTimeoutMs` | number | no | `10000` | no | Connect |
| `queryTimeoutMs` | number | no | `30000` | no | Read timeout |
| `maxRows` | number | no | `1000` | no | Also send `Range` / `limit` |
| `maxBytes` | number | no | `1048576` | no | Response cap |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | `userJwt` is a secret-like token: treat as secret (do not log) even if on input |
| `output` | OUTPUT | `{ status, … }` or `{ error }` | No keys, no JWTs |

FUNCTION ports: none.

## Input fields (by operation)

### Shared

| Field | Type | Required | Purpose |
|---|---|---|---|
| `userJwt` | password | no | User access token from Supabase Auth. When set, `Authorization: Bearer <userJwt>` and `apikey` stays the anon/publishable key. **Prefer this over service_role** |

### `operation = row.create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `table` | string | yes | Validated identifier |
| `row` | object | yes | JSON body (single object or array per PostgREST) |

### `operation = row.get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `table` | string | yes | Table |
| `filters` | object | yes | PostgREST query params, e.g. `{ "id": "eq.1" }` values already in `op.value` form |

### `operation = row.list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `table` | string | yes | Table |
| `filters` | object | no | Same as get |
| `select` | string | no | `select=` column list |
| `order` | string | no | `order=` |
| `limit` | number | no | Capped by `maxRows` |
| `offset` | number | no | Offset |

### `operation = row.update`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `table` | string | yes | Table |
| `filters` | object | yes | Must not be empty (no unfiltered PATCH) |
| `row` | object | yes | PATCH JSON |

### `operation = row.delete`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `table` | string | yes | Table |
| `filters` | object | yes | Must not be empty |

### `operation = rpc.call`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `function` | string | yes | RPC name |
| `args` | object | no | JSON body |

### `operation = vector.query`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `function` | string | yes | Postgres function wrapping pgvector (e.g. `match_documents`) |
| `queryEmbedding` | array of number | yes | Passed as `query_embedding` (or `args` override) |
| `matchThreshold` | number | no | `match_threshold` |
| `matchCount` | number | no | `match_count`, capped by `maxRows` |
| `args` | object | no | Extra RPC arguments |

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | success | `ok` |
| `row` | object | get/create (single) | Representation |
| `rows` | array | list/rpc/vector | Rows |
| `truncated` | boolean | list | Cap / `Content-Range` |
| `error` | string | failure | PostgREST `message` / `hint` only — no API keys |

## Legal

- Supabase Terms: https://supabase.com/terms
- Privacy: https://supabase.com/privacy
- Rate limits: project plan (HTTP 429). Retry 429 once with jitter.
- PII: RLS is the operator’s duty. `service_role` bypasses RLS — treat as highly privileged. Prefer user JWT.
- Official-API-first: PostgREST + documented keys only. No dashboard scrape. No Postgres wire on this node.

## Implementation notes

- JDK HTTP only.
- Do not put keys on query strings.
- Timeouts and size caps as configured. Honor `Content-Range`.
- Retry: 429/503 once. Never retry POST create after 201.
- HTTP 4xx/5xx → `{ error }` on OUTPUT. Never throw past dispatch.
- New `sb_secret_` keys reject browser User-Agents; daemon HTTP client is fine. Do not send secret keys in `Authorization` unless identical to `apikey` (platform restriction).
