# Cloudflare

- **Status:** planned
- **Catalog:** `docs/catalog/developer-tools.md`
- **Folder when built:** `nodes/cloudflare/`
- **Researched:** 2026-08-20
- **Official API:** Cloudflare REST API v4 (`https://api.cloudflare.com/client/v4`)
- **Docs:** https://developers.cloudflare.com/api/
- **Auth:** api_key (API **token**) — secret binding: `apiToken`

> **Do not implement Global API Key.** Cloudflare documents tokens as the preferred scheme and the Global API key as the previous scheme to avoid (`X-Auth-Email` + `X-Auth-Key`). This node accepts **only** `Authorization: Bearer {apiToken}`. Do not add `email` / `globalApiKey` configuration parameters.

## Scope

One node for the customer’s Cloudflare account: DNS records, zone metadata, cache purge, Workers script list, R2 object **get** (file), custom SSL certificates, and Notifications webhook destinations (`listen`). Customer account only. JDK HTTP. R2 uses the Cloudflare REST object API (not the S3-compatible endpoint) so a single API token covers the catalog surface; object bytes use type `file`.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `dns_list` | GET | `/zones/{zone_id}/dns_records` | List DNS records |
| `dns_create` | POST | `/zones/{zone_id}/dns_records` | Create a record |
| `dns_update` | PATCH | `/zones/{zone_id}/dns_records/{dns_record_id}` | Patch a record |
| `dns_delete` | DELETE | `/zones/{zone_id}/dns_records/{dns_record_id}` | Delete a record |
| `zone_get` | GET | `/zones/{zone_id}` | Zone details |
| `cache_purge` | POST | `/zones/{zone_id}/purge_cache` | Purge by URL/tag/host/prefix or everything |
| `worker_list` | GET | `/accounts/{account_id}/workers/scripts` | List Worker scripts |
| `r2_get` | GET | `/accounts/{account_id}/r2/buckets/{bucket_name}/objects/{object_key}` | Download object → type `file` |
| `cert_get` | GET | `/zones/{zone_id}/custom_certificates/{custom_certificate_id}` | Custom SSL configuration details |
| `cert_upload` | POST | `/zones/{zone_id}/custom_certificates` | Upload custom certificate + private key (`file`) |
| `listen` | POST | `/accounts/{account_id}/alerting/v3/destinations/webhooks` | Create a Notifications webhook destination |

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `dns_list` | no | Chooses the row above |
| `apiToken` | password | yes | — | **yes** | API token (`cfut_…` or legacy format). Bearer only. |
| `accountId` | string | when account-scoped | — | no | Account id for Workers, R2, listen. Input may override. |
| `zoneId` | string | when zone-scoped | — | no | Zone id for DNS, cache, certs, `zone_get`. Input may override. |
| `timeoutSeconds` | number | no | `30` | no | Per-request timeout. Cap `120`. File ops default `60` if unset. |
| `maxResponseBytes` | number | no | `8388608` | no | JSON body cap 8 MiB. `r2_get` / `cert_upload` use `maxFileBytes`. |
| `maxFileBytes` | number | no | `33554432` | no | Object/cert payload cap (32 MiB). Cloudflare REST R2 upload max is 300 MB — still cap here. |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | `r2_get` output file; `cert_upload` input files |
| `output` | OUTPUT | object — `{ status, … }` or `{ error }` | Never echo token, private key, or file bytes |

FUNCTION ports: none.

## Input fields (by operation)

Shared: `accountId` / `zoneId` on input override config when non-blank.

### Shared zone ops (`dns_*`, `zone_get`, `cache_purge`, `cert_*`)

| Field | Type | Required | Purpose |
|---|---|---|---|
| `zoneId` | string | if config blank | Zone identifier |

### `operation = dns_list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `type` | string | no | Record type filter (`A`, `AAAA`, `CNAME`, `MX`, `TXT`, …) |
| `name` | string | no | FQDN filter |
| `page` | number | no | 1-based page |
| `perPage` | number | no | Page size; Cloudflare max 5,000, handler cap **100** |

### `operation = dns_create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `type` | string | yes | Record type |
| `name` | string | yes | DNS name |
| `content` | string | yes | Record content (IP, target, TXT, …) |
| `ttl` | number | no | Seconds; `1` = automatic |
| `proxied` | boolean | no | Orange-cloud proxy |
| `priority` | number | MX/SRV | Priority |
| `comment` | string | no | Dashboard comment |

### `operation = dns_update`

Same fields as create, plus:

| Field | Type | Required | Purpose |
|---|---|---|---|
| `recordId` | string | yes | DNS record id |

Omit null fields; send only provided keys (PATCH).

### `operation = dns_delete`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `recordId` | string | yes | DNS record id |

### `operation = zone_get`

No extra fields.

### `operation = cache_purge`

Exactly one purge mode required:

| Field | Type | Required | Purpose |
|---|---|---|---|
| `purgeEverything` | boolean | one-of | `{ "purge_everything": true }` — all plans |
| `files` | array of string | one-of | URLs to purge |
| `tags` | array of string | one-of | Cache-Tag purge (Enterprise) |
| `hosts` | array of string | one-of | Host purge |
| `prefixes` | array of string | one-of | Prefix purge |

### `operation = worker_list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `accountId` | string | if config blank | Account id |

### `operation = r2_get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `accountId` | string | if config blank | Account id |
| `bucket` | string | yes | R2 bucket name |
| `key` | string | yes | Object key. Slashes **literal**, not `%2F` |
| `jurisdiction` | string | no | Header `cf-r2-jurisdiction`: `default` \| `eu` \| `fedramp` |

### `operation = cert_get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `certificateId` | string | yes | Custom certificate id |

### `operation = cert_upload`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `certificateFile` | file | yes | PEM certificate (and intermediates). Platform ref-id |
| `privateKeyFile` | file | yes | PEM private key. Platform ref-id. Never log |
| `type` | string | no | `sni_custom` (default) or `legacy_custom` |
| `bundleMethod` | string | no | `ubiquitous` \| `optimal` \| `force` |

### `operation = listen`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `accountId` | string | if config blank | Account id |
| `name` | string | yes | Destination name |
| `url` | string | yes | HTTPS callback — StudioQuack message-event ingress |
| `secret` | string | no | Sent by Cloudflare as `cf-webhook-auth`. Store in secret manager if reused; do not log. If omitted, Cloudflare may still generate destination-side auth |

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | success | `ok` |
| `success` | boolean | most REST calls | Cloudflare envelope `success` |
| `result` | object or array | most ops | Cloudflare `result` (redact nothing that is not a secret; never include token) |
| `result_info` | object | `dns_list` | Pagination |
| `file` | file | `r2_get` | Platform ref-id of downloaded object |
| `etag` | string | `r2_get` | Object ETag if present |
| `contentType` | string | `r2_get` | Response Content-Type |
| `webhookId` | string | `listen` | Destination id |
| `error` | string | failure | Cloudflare `errors[].message` or HTTP status. Never token/key PEM |

## Legal

- Terms: https://www.cloudflare.com/website-terms/
- Developer docs: https://developers.cloudflare.com/fundamentals/api/
- Token permissions must match the op (Zone DNS Edit, Cache Purge, Workers Scripts Read, Workers R2 Storage Read, SSL and Certificates Edit, Notifications).
- Rate limits vary by endpoint; honor `Retry-After` and HTTP 429 with jitter.
- Custom certificate private keys and R2 objects may contain secrets/PII — never log bytes.
- Official API only. No dashboard scrape.

## Implementation notes

- JDK HTTP. Header: `Authorization: Bearer {apiToken}`. JSON `Content-Type: application/json` except `r2_get` (empty body) and `cert_upload` (JSON with PEM strings read from `file` refs — do not leave PEMs on OUTPUT).
- TLS verify on. Host allowlist: `api.cloudflare.com` only.
- Global API key: if a caller somehow sends `X-Auth-*` via a future field, reject. Do not document email+key.
- R2 REST is rate-limited; Cloudflare recommends S3 API for high throughput — out of scope for this catalog row.
- Retry 429 with full jitter, max 3. Timeouts + size caps as config.
- HTTP 4xx/5xx → `{ error }` on OUTPUT. Never throw past dispatch.
