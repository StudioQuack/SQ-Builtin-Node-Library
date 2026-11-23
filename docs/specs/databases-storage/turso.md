# Turso (libSQL HTTP)

- **Status:** planned
- **Catalog:** `docs/catalog/databases-storage.md`
- **Folder when built:** `nodes/turso/`
- **Researched:** 2026-08-20
- **Official API:** libSQL SQL-over-HTTP (Hrana over HTTP). Turso documents **`POST /v2/pipeline`**. GitHub libSQL also describes `/v3/pipeline` (v3 adds protocol negotiation fields); implement **v2** as documented on Turso’s site unless v3 is later published there.
- **Docs:** https://docs.turso.tech/sdk/http/reference — protocol: https://github.com/tursodatabase/libsql/blob/main/docs/HTTP_V2_SPEC.md
- **Auth:** bearer — secret binding: `authToken`

## Scope

Hosted SQLite dialect over **official HTTP** (JDK HTTP). Replaces local SQLite (see [`sqlite.md`](sqlite.md) — do not build). The customer owns the Turso database. Tokens are per-database (`turso db tokens create`).

Base URL: replace `libsql://` / `turso://` with `https://` — typically `https://[databaseName]-[organizationSlug].turso.io`.

Do not implement local libSQL files, host paths, or the Turso Platform API (org/database CRUD) except SQL on this database URL.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `execute.sql` | POST | `/v2/pipeline` | One SQL statement (`type: execute`) then `close` |
| `execute.batch` | POST | `/v2/pipeline` | Several statements in one pipeline (optionally `type: batch`) |

Also documented (not catalog operations — do not add extra nodes): `GET /version`, `GET /health`. Do not implement `/dump` to a host path. `/beta/listen` is plan-gated — omit unless catalog adds `trigger.listen` later.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `execute.sql` | no | Chooses the row above |
| `url` | string | yes | | no | HTTPS database URL (no `libsql://`). Example: `https://app-org.turso.io` |
| `authToken` | password | yes | | yes | Bearer token. Header `Authorization: Bearer …` |
| `connectTimeoutMs` | number | no | `10000` | no | Connect timeout |
| `queryTimeoutMs` | number | no | `30000` | no | Read timeout (Turso: idle connection ~10s, interactive tx ~5s) |
| `maxRows` | number | no | `10000` | no | Row cap |
| `maxBytes` | number | no | `1048576` | no | Payload cap |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | |
| `output` | OUTPUT | `{ status, … }` or `{ error }` | Never echo `authToken` |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = execute.sql`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `sql` | string | yes | SQL with `?` or named `:name` / `@name` / `$name` placeholders |
| `params` | array or object | no | Positional array → `args`; object → `named_args`. Each value typed as `null` \| `integer` \| `float` \| `text` \| `blob` (blob = base64 in protocol) |

Pipeline body:

```json
{
  "requests": [
    { "type": "execute", "stmt": { "sql": "SELECT * FROM t WHERE id = ?", "args": [{ "type": "integer", "value": "1" }] } },
    { "type": "close" }
  ]
}
```

Always `close` after a one-shot workflow invocation so the server does not keep a baton.

### `operation = execute.batch`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `statements` | array | yes | List of `{ sql, params? }` executed in order on one pipeline. Always end with `close` |

If any step returns `type: error`, map to `{ error }` (include step index). Do not leave a dangling baton.

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | success | `ok` |
| `columns` | array of string | SELECT | From `cols[].name` |
| `rows` | array | SELECT | Unwrap `{type,value}` cells to JSON |
| `truncated` | boolean | SELECT | Cap hit |
| `rowCount` | number | DML | `affected_row_count` |
| `lastInsertRowid` | string | INSERT | `last_insert_rowid` as string (precision) |
| `results` | array | `execute.batch` | Per-statement subset of the above |
| `error` | string | failure | Server error message only |

## Legal

- Turso Cloud terms: https://turso.tech/legal/terms (**Unverified (2026-08-20)** if that URL redirects — confirm live URL before ship: https://docs.turso.tech)
- Rate limits: plan-based; HTTP 429 → `{ error }`, retry once with jitter only on 429/503.
- PII: customer SQLite rows. Do not log SQL params or tokens.
- Official-API-first: documented pipeline HTTP only. No dashboard scrape.

## Implementation notes

- JDK HTTP only. JSON `Content-Type: application/json`.
- Integer `value` is a **string** in JSON (Turso: avoid float precision loss).
- Timeouts: Turso documents 5s transaction / 10s idle connection — keep `queryTimeoutMs` ≥ that for multi-step batches; do not hold batons across workflow steps in v1.
- Size caps: `maxRows` / `maxBytes`.
- Retry: 429/503 max 1; never retry non-idempotent INSERT after HTTP 200 with `ok`.
- Errors: HTTP 4xx/5xx and pipeline `type: error` → `{ error }` on OUTPUT, never throw past dispatch.
