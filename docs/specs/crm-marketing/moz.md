# Moz

- **Status:** planned
- **Catalog:** `docs/catalog/crm-marketing.md`
- **Folder when built:** `nodes/moz/`
- **Researched:** 2026-08-20
- **Official API:** Moz **unified API** (JSON-RPC 2.0). Docs: “currently not versioned.” Catalog “Links API + HMAC” is **legacy** (Links v1 HMAC-SHA1 / Links v2 `lsapi.seomoz.com`). **Do not implement HMAC.** Ship JSON-RPC + `x-moz-token`.
- **Docs:** https://moz.com/api/docs/guides/getting-started · auth https://moz.com/api/docs/guides/authentication · metrics https://moz.com/api/docs/methods/DataSiteMetricsFetchMultipleAction · links https://moz.com/api/docs/methods/DataSiteLinkListAction · anchors https://moz.com/api/docs/methods/DataSiteAnchorTextListAction · top pages https://moz.com/api/docs/methods/DataSiteTopPageListAction · v3 overview https://moz.com/help/links-api/v3-overview
- **Auth:** api_key — secret binding: `api_token`. Header `x-moz-token`. (Catalog `accessId`+`secretKey` HMAC is retired for this node.)

## Scope

One node for Moz **link/URL metrics** used in SEO workflows. Every call is `POST https://api.moz.com/jsonrpc` with body `{ jsonrpc: "2.0", id, method, params: { data } }`. `id` is a client JSON-RPC id (**≥ 24 chars**, UUID v4 recommended), **not** the Moz token.

Do not call `lsapi.seomoz.com`. Do not scrape Moz Pro. Do not redistribute bulk link graphs beyond Moz API terms.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `urlMetrics.get` | POST | method `data.site.metrics.fetch.multiple` | Domain/page authority, spam score, link counts for 1+ URLs |
| `links.list` | POST | method `data.site.link.list` | Inbound links to a target |
| `anchorText.list` | POST | method `data.site.anchor-text.list` | Anchor-text aggregates |
| `topPages.list` | POST | method `data.site.top-page.list` | Top pages on a domain |

Optional (not in catalog; omit from v1 UI): `quota.lookup` for remaining rows.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `urlMetrics.get` | no | Chooses the method |
| `api_token` | password | yes | empty | yes | Moz API token (dashboard; up to 5 per account). Header `x-moz-token` |
| `timeout_seconds` | number | no | `30` | no | Floor 1 s |
| `default_scope` | select | no | `url` | no | `domain` \| `subdomain` \| `subfolder` \| `url` |

Moz API is a **separate paid SKU** from Moz Pro. Free tier: 50 rows/month (card on file).

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | Target URLs only |
| `output` | OUTPUT | `{ status, result, … }` or `{ error }` | Never log token |

FUNCTION ports: none.

## Input fields (by operation)

### `urlMetrics.get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `targets` | array | yes | URLs/domains. Mapped to `site_queries[]`. Cap 50 per call |
| `scope` | string | no | Overrides config `default_scope` for all queries unless per-item `scope` is set |

Each `targets[]` item maps to `params.data.site_queries[]`: `{ "query": "<url>", "scope": "domain"|"subdomain"|"subfolder"|"url" }`. Quota: **one row per returned result**.

### `links.list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `target` | string | yes | URL or domain |
| `scope` | string | no | Parse scope |
| `limit` | number | no | Page size as method docs allow; cap 50 |
| `next_token` | string | no | Pagination token from prior `result` |
| `filters` | object | no | Method-specific filters (nofollow, etc.) only if present in current method schema |

### `anchorText.list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `target` | string | yes | Target site |
| `scope` | string | no | Parse scope |
| `limit` | number | no | Result cap |
| `next_token` | string | no | Pagination |

### `topPages.list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `target` | string | yes | Usually a domain |
| `scope` | string | no | Prefer `domain` |
| `limit` | number | no | Cap 50 |
| `sort` | string | no | As docs: page authority / linking root domains / external pages |

## Output fields (by operation)

JSON-RPC success: HTTP 200 + `result`. JSON-RPC error object → `{ error }` even if HTTP 200.

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | success | `ok` |
| `rpc_id` | string | always | Echo of request `id` |
| `result` | object | success | Provider `result` (metrics, `links`, `anchor_texts`, `top_pages`) |
| `quota` | object | if present | Rows consumed / remaining when echoed |
| `next_token` | string | paged methods | Pass back on next run |
| `error` | string | failure | JSON-RPC `error.message` or HTTP |
| `http_status` | number | HTTP error | |
| `retry_after_seconds` | number | 429 | Honor Retry-After; else 60 |

`urlMetrics.get` quota: **one row per returned result**.

## Legal

- Moz API legal / terms on the Moz API dashboard (separate from Moz Pro).
- Beta methods: prototyping only; do not expose beta methods on this node.
- **Bulk export / redistribution** of link indexes is restricted — operator duty.
- Rate limits and monthly **row** quota are plan-specific. HTTP 429 must not tight-loop.
- Catalog drift: replace `auth=accessId+secretKey` with `auth=apiToken` (`x-moz-token`). HMAC Links v1 is not current.

## Implementation notes

- JDK `HttpClient` only. `Redirect.NEVER`.
- Headers: `x-moz-token: {api_token}`, `Content-Type: application/json`.
- Generate `id` with `UUID.randomUUID().toString()` (36 chars).
- Envelope:

```json
{ "jsonrpc": "2.0", "id": "<uuid>", "method": "<method>", "params": { "data": { } } }
```

- Empty token → `{ error }` no HTTP.
- Body cap **1_000_000** bytes. Timeout 30 s.
- Logs: operation, method name, target **host** only (not full URL query strings), HTTP status, row counts — never `x-moz-token`.
