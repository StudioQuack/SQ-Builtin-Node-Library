# Moz

Official **Moz unified API** (JSON-RPC 2.0, currently not versioned) via `OfficialApiNode` / `api_spec.json`.

- **Docs:** https://moz.com/api/docs/guides/getting-started · auth https://moz.com/api/docs/guides/authentication · metrics https://moz.com/api/docs/methods/DataSiteMetricsFetchMultipleAction · links https://moz.com/api/docs/methods/DataSiteLinkListAction
- **Base:** `POST https://api.moz.com/jsonrpc`
- **Auth:** header `x-moz-token: {api_token}`. Customer owns the Moz API SKU (separate paid SKU from Moz Pro).

## HMAC limitation (catalog vs current API)

Catalog row still says `auth=accessId+secretKey` (legacy **Links v1 HMAC-SHA1** / Links v2 `lsapi.seomoz.com`). **OfficialApiNode has no HMAC** and this node **does not** implement HMAC.

Current official path: Moz API token as **`x-moz-token`** (header). Do not call `lsapi.seomoz.com`. Legacy Links HMAC `access_id` / `secret_key` are not stored on this node — mint an API token.

If Moz later documents a simpler Basic `accessId:secretKey` on `api.moz.com`, OfficialApiNode could switch `auth.type` to `basic`; that is **not** the documented unified API as of 2026-08-20.

## Operations

Every call: JSON-RPC envelope `{ "jsonrpc": "2.0", "id": "<uuid ≥24 chars>", "method": "<method>", "params": { "data": { } } }`.

| operation | method |
|---|---|
| `urlMetrics.get` | `data.site.metrics.fetch.multiple` |
| `links.list` | `data.site.link.list` |
| `anchorText.list` | `data.site.anchor-text.list` |
| `topPages.list` | `data.site.top-page.list` |

OfficialApiNode posts `jsonrpc` / `id` / `method` / `params` from INPUT. It does not auto-build the envelope from `targets`.

JSON-RPC error object on HTTP 200 is still `status=ok` with `data` — inspect `data.error`.

## Config

| Name | Type | Secret | Purpose |
|---|---|---|---|
| `operation` | select | no | Default `urlMetrics.get` |
| `api_token` | password | yes | `x-moz-token` |
| `timeout_seconds` | number | no | Default 30 |

## Ports

`input` / `output` only. Never log `api_token`. Log target **host** only, not full URL query strings.

## Legal

- Moz API legal / terms on the Moz API dashboard (separate from Moz Pro).
- Bulk export / redistribution of link indexes is restricted — operator duty.
- Free tier: 50 rows/month (card on file). HTTP 429 must not tight-loop.
