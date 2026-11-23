# Azure Blob Storage

- **Status:** planned
- **Catalog:** `docs/catalog/files-documents.md`
- **Folder when built:** `nodes/azure-blob/`
- **Researched:** 2026-08-20
- **Official API:** Azure Blob Service REST
- **Docs:** [Blob service REST](https://learn.microsoft.com/en-us/rest/api/storageservices/blob-service-rest-api) · [Put Blob](https://learn.microsoft.com/en-us/rest/api/storageservices/put-blob) · [List Blobs](https://learn.microsoft.com/en-us/rest/api/storageservices/list-blobs) · [Versioning](https://learn.microsoft.com/en-us/rest/api/storageservices/versioning-for-the-azure-storage-services) · [Authorize Entra](https://learn.microsoft.com/en-us/rest/api/storageservices/authorize-with-azure-active-directory) · [Authorize Shared Key](https://learn.microsoft.com/en-us/rest/api/storageservices/authorize-with-shared-key) · [SAS](https://learn.microsoft.com/en-us/rest/api/storageservices/delegate-access-with-shared-access-signature)
- **Auth:** OfficialApiNode uses `sas_token` (Authorization header). Host is `https://{account_name}.blob.core.windows.net`. Engine does not mint Entra tokens or compute SharedKey.

**`x-ms-version`:** default **`2026-06-06`** (GA SDK default; globally enabled except a small set of canary tenants in `useast2euap` / `uscentraleuap` as of 2026-07-20). Latest fully deployed everywhere: `2026-04-06`. `2026-10-06` is **not** universal — do not hard-code it. Config `x_ms_version` overrides the default.

## Scope

One node for Azure Blob Storage (block blobs). Catalog verbs are container CRUD + blob CRUD. `blob_create` is **Put Blob** with `x-ms-blob-type: BlockBlob` (this is the catalog’s upload). Objects larger than **5000 MiB** must use **Put Block** + **Put Block List** (handler-internal; not extra catalog operations). Page blobs and append blobs are out of catalog.

Host: `https://{account}.blob.core.windows.net` (or sovereign suffixes via `blob_endpoint`).

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `container_create` | PUT | `/{container}?restype=container` | Create container |
| `container_delete` | DELETE | `/{container}?restype=container` | Delete container (blobs must be gone unless delete includes blobs per account settings) |
| `container_get` | GET | `/{container}?restype=container` | Get container properties |
| `container_list` | GET | `/?comp=list` | List containers |
| `blob_create` | PUT | `/{container}/{blob}` | Put Blob (BlockBlob) or Put Block(s)+Put Block List |
| `blob_delete` | DELETE | `/{container}/{blob}` | Delete blob |
| `blob_get` | GET | `/{container}/{blob}` | Download bytes → `file` (or metadata-only via HEAD if `head_only`) |
| `blob_list` | GET | `/{container}?restype=container&comp=list` | List blobs (`prefix`, `delimiter`, `marker`) |

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `blob_list` | no | Chooses the row above |
| `account_name` | string | yes | | no | Storage account name in `https://{account_name}.blob.core.windows.net` |
| `blob_endpoint` | string | no | `https://{account_name}.blob.core.windows.net` | no | Override (private endpoint / sovereign cloud). HTTPS only. Not used by the current OfficialApiNode spec. |
| `sas_token` | password | yes | | **yes** | Query string **without** leading `?` (`sv=…&sig=…`). Service or user-delegation SAS. |
| `x_ms_version` | string | no | `2026-06-06` | no | REST version header |
| `container` | string | most blob ops | | no | Default container |
| `timeout_seconds` | number | no | `60` | no | |
| `max_retries` | number | no | `3` | no | |

File payloads use type `file` (platform ref-id), never inline bytes.

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | No secrets |
| `output` | OUTPUT | `{ status, … }` or `{ error }` | No keys, SAS, or bytes |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = container_create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `container` | string | yes | 3–63 chars, DNS-like |
| `public_access` | string | no | `x-ms-blob-public-access`: omit (private), `blob`, or `container`. Prefer private + SAS. |
| `metadata` | object | no | `x-ms-meta-*` |

### `operation = container_delete` / `container_get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `container` | string | yes | |
| `lease_id` | string | no | `x-ms-lease-id` if leased |

### `operation = container_list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `prefix` | string | no | |
| `marker` | string | no | Continuation |
| `max_results` | number | no | |

### `operation = blob_create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `container` | string | yes | |
| `blob` | string | yes | Blob name (may contain `/` as virtual directories) |
| `file` | **file** | yes | Platform ref-id; stream |
| `content_type` | string | no | `x-ms-blob-content-type` |
| `filename` | string | no | Companion; optional `Content-Disposition` |
| `content_md5` | string | no | Base64 MD5 header if computed from stream |
| `overwrite` | boolean | no | If false, `If-None-Match: *` |

Always send `x-ms-blob-type: BlockBlob`. Single Put Blob if size ≤ **5000 MiB** (version 2019-12-12+). Larger: Put Block `/{container}/{blob}?comp=block&blockid=` (block ≤ 4000 MiB, ≤ 50_000 blocks, max ~190.7 TiB) then Put Block List `?comp=blocklist`. Stream from the file store; do not load the whole blob in memory.

### `operation = blob_delete`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `container` | string | yes | |
| `blob` | string | yes | |
| `snapshot` | string | no | `snapshot=` query |
| `delete_snapshots` | string | no | `x-ms-delete-snapshots`: `include` or `only` |

### `operation = blob_get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `container` | string | yes | |
| `blob` | string | yes | |
| `snapshot` | string | no | |
| `range` | string | no | `x-ms-range` / `Range` |
| `head_only` | boolean | no | If true, `HEAD` properties only (no `file`) |

### `operation = blob_list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `container` | string | yes | |
| `prefix` | string | no | |
| `delimiter` | string | no | `/` for virtual directories |
| `marker` | string | no | |
| `max_results` | number | no | |
| `include` | string | no | e.g. `metadata,snapshots` (`include=` query) |

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | success | `ok` |
| `error` | object | failure | `{ code, message, http_status, request_id }` from XML `Error` / `x-ms-request-id` |
| `container` | string | container ops | |
| `etag` | string | create/get | |
| `last_modified` | string | | |
| `containers` | list | `container_list` | `{ name, properties }[]` |
| `blobs` | list | `blob_list` | `{ name, size, content_type, etag, last_modified }[]` |
| `blob_prefixes` | list | list + delimiter | |
| `next_marker` | string | list | |
| `file` | **file** | `blob_get` (not head_only) | Platform ref-id |
| `filename` | string | download | Companion |
| `mime_type` | string | download | |
| `size` | number | download | |
| `content_md5` | string | download | If service returned it |

## Legal

- [Microsoft Customer Agreement](https://www.microsoft.com/licensing/docs/view/Microsoft-Customer-Agreement) / Azure subscription terms the operator already accepted
- [Online Services DPA](https://www.microsoft.com/licensing/docs/view/Microsoft-Products-and-Services-Data-Protection-Addendum)
- [Azure legal](https://azure.microsoft.com/support/legal/)
- Operator owns the storage account and SAS expiry. OfficialApiNode uses `sas_token` only; it does not mint Entra tokens or compute SharedKey.
- **PII / residency:** bytes go to the account’s region / geo-redundant SKU. PHI needs a BAA with Microsoft, not StudioQuack.
- Official-API-first: Blob REST only. No portal scrape. No account-key in logs.
- **Scale (published):** Put Blob ≤ 5000 MiB; Put Block ≤ 4000 MiB; 50_000 blocks; ~190.7 TiB max block blob. Account ingress/egress per [scalability targets](https://learn.microsoft.com/en-us/azure/storage/blobs/scalability-targets). 503/500 → retry.

## Implementation notes

- **JDK HTTP only.** Required headers on every call: `x-ms-version`, `x-ms-date` (RFC 1123 UTC), `x-ms-client-request-id` (UUID, log this not secrets).
- **OfficialApiNode auth:** `sas_token` as the Authorization header. Host `https://{account_name}.blob.core.windows.net`. Does not mint Entra tokens or compute SharedKey.
- **Streaming + size:** default single Put Blob; if file size &gt; 5000 MiB (or `timeout` risk), Put Block (Base64 block IDs, ≤64 bytes decoded) then Put Block List. Uncommitted block cap 100_000.
- **Retries:** 408/429/500/503 with backoff; honor `Retry-After`. Idempotent GET/HEAD/PUT with `If-None-Match` as configured. Never retry a non-replayable stream unless buffered to temp in the file store.
- **Timeouts:** connect ≤ 10s; read = `timeout_seconds` per request/block.
- **Errors:** XML `<Error><Code/><Message/>` → `{ error }`. Never throw past dispatch.
- **SSRF:** `blob_endpoint` HTTPS + egress allowlist only.
- **DO NOT BUILD:** Azure SDK; page/append blob types; putting `account_key` on INPUT; hard-coding `2026-10-06` as default.
