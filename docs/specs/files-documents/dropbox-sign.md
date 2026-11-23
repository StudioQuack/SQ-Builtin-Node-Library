# Dropbox Sign

- **Status:** planned
- **Catalog:** `docs/catalog/files-documents.md`
- **Folder when built:** `nodes/dropbox-sign/`
- **Researched:** 2026-08-20
- **Official API:** Dropbox Sign API **v3** (`https://api.hellosign.com/v3`). HelloSign **product brand retired**; legal/ToS are **Dropbox Sign**. Developer reference still hosted at developers.hellosign.com.
- **Docs:** https://developers.hellosign.com/api/reference/overview/
- **Index:** https://developers.hellosign.com/llms.txt
- **OpenAPI (canonical):** https://raw.githubusercontent.com/hellosign/hellosign-openapi/main/openapi.yaml
- **Auth:** HTTP Basic via `api_key` (username, empty password). Engine does not run OAuth.

## Scope

One node for the customer’s Dropbox Sign account. The operator is the **e-sign sender**. StudioQuack is not the sender.

`signature_request_send` uploads documents as type `file` (multipart `files[]` or JSON `files` as documented). Template ids are strings (`template_ids`). `signature_request_files` **downloads** signed/current PDFs as type `file` on OUTPUT.

**Do not build:** HelloSign-branded ToS copy, SOAP, HTML scrape, `files_as_data_uri` (base64 as stored type), or vendor SDK.

## Operations (`operation` config values)

| operation | HTTP | Path | When to use |
|---|---|---|---|
| `signature_request_send` | POST | `/v3/signature_request/send` | Send files for signature. Requires `files` **or** `file_urls`, not both. Catalog file path uses type `file`. |
| `signature_request_get` | GET | `/v3/signature_request/{signature_request_id}` | Status / metadata. |
| `signature_request_list` | GET | `/v3/signature_request/list` | List/search. |
| `signature_request_cancel` | POST | `/v3/signature_request/cancel/{signature_request_id}` | Cancel incomplete request (irreversible). |
| `signature_request_files` | GET | `/v3/signature_request/files/{signature_request_id}` | PDF or ZIP. **OUTPUT `file`.** HTTP 409 while files are preparing. |
| `template_get` | GET | `/v3/template/{template_id}` | Template metadata (id, not bytes). |
| `template_list` | GET | `/v3/template/list` | List templates. |
| `template_delete` | POST | `/v3/template/delete/{template_id}` | Delete template. |
| `listen` | PUT | `/v3/api_app/{client_id}` | Set official **API App** `callback_url` to StudioQuack ingress (Dropbox Sign Events). |

Send-with-template is **not** a catalog verb; if a later catalog add is needed, it is `POST /v3/signature_request/send_with_template` with `template_ids` strings — do not invent it in the handler until the catalog row lists it.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `signature_request_send` | no | Verb above. |
| `api_key` | password | yes | | **yes** | API key from Dropbox Sign API settings (HTTP Basic username). |
| `timeout_seconds` | number | no | `60` | no | JDK timeouts. |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object | Per-operation fields. No secrets. |
| `output` | OUTPUT | object | `signature_request_files` → `file`. |

FUNCTION ports: **none**.

## Input fields (by operation)

### `operation = signature_request_send`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `files` | **file**[] | yes* | Platform ref-ids. Stream as `files[]`. *Required unless `file_urls` is used. |
| `file_urls` | string[] | yes* | HTTPS URLs Dropbox Sign fetches. Do not use for daemon-local bytes — prefer `files`. Mutually exclusive with `files`. |
| `signers` | object[] | yes* | `{ name, email_address, order, pin, … }`. *Or `grouped_signers`, not both. |
| `grouped_signers` | object[] | no | Group signing. |
| `title` | string | no | Request title. |
| `subject` | string | no | Email subject. |
| `message` | string | no | Email message. |
| `cc_email_addresses` | string[] | no | CC list. |
| `test_mode` | boolean | no | Overrides config for this call. |
| `form_fields_per_document` | object[] | no | Programmatic fields. If omitted, a signature page is affixed. |
| `metadata` | object | no | Non-secret metadata. |
| `client_id` | string | no | API App id (branding + app callback). |

`pin` and SMS numbers are signer secrets in transit to Dropbox Sign — never log; do not persist on OUTPUT.

### `operation = signature_request_get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `signature_request_id` | string | yes | Request id. |

### `operation = signature_request_list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `page` | number | no | Page. |
| `page_size` | number | no | Page size. |
| `query` | string | no | Search syntax per official search docs. |

### `operation = signature_request_cancel`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `signature_request_id` | string | yes | Incomplete request only. |

### `operation = signature_request_files`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `signature_request_id` | string | yes | Request id. |
| `file_type` | string | no | `pdf` (default) or `zip`. |

### `operation = template_get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `template_id` | string | yes | Template id (string). |

### `operation = template_list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `page` | number | no | Page. |
| `page_size` | number | no | Page size. |
| `query` | string | no | Search. |

### `operation = template_delete`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `template_id` | string | yes | Template to delete. |

### `operation = listen`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `callback_url` | string | yes | HTTPS StudioQuack ingress. Written to API App `callback_url`. |
| `client_id` | string | no | API App client id for the listen callback update. |

Account-level callbacks (dashboard) are out of band; this operation uses the official API App update.

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | success | `ok` or provider request status. |
| `signature_request_id` | string | send / get / cancel | Id. |
| `signature_request` | object | send / get | Metadata JSON (no file bytes). |
| `signature_requests` | object[] | list | Page of requests. |
| `template` | object | template_get | Template JSON. |
| `templates` | object[] | template_list | List page. |
| `file` | **file** | signature_request_files | PDF/ZIP ref-id. **Not** data-URI. |
| `filename` | string | signature_request_files | Suggested name. |
| `content_type` | string | signature_request_files | `application/pdf` or `application/zip`. |
| `callback_url` | string | listen | Echo of registered URL (no secrets). |
| `error` | string | failure | Non-secret. Map 409 on files to a retryable “preparing” error. |

## Legal

- Terms: https://sign.dropbox.com/about/terms (also https://www.dropboxsign.com/about/terms)
- API / service-specific terms: https://sign.dropbox.com/about/terms/service-specific-terms
- Privacy: https://sign.dropbox.com/about/privacy
- **Customer is the sender.** API key or OAuth user sends the signature request. StudioQuack is not the Dropbox Sign customer of record.
- **ESIGN / eIDAS / eID:** operator + Dropbox Sign. SMS add-on requires signer consent (provider ToS).
- **PHI / DPA:** documents go to Dropbox Sign. Require the customer’s DPA/BAA with Dropbox before PHI. StudioQuack is not that BA.
- **Demo vs prod:** `test_mode=true` is non-binding test signatures. Embedded/OAuth apps may need [app approval](https://developers.hellosign.com/docs/guides/app-approval/overview.md) before production.
- **Rate limits** (service-specific terms): up to **100** standard API requests/minute, **25**/minute higher-tier endpoints, **10**/minute in test mode. Dropbox may suspend for abuse. Honor 429.
- Events: respond `200` with body exactly `Hello API Event Received` (text/plain) from **ingress**; verify HMAC-SHA256 `event_hash` with the **primary** API key. 6 retries / 10 consecutive-failure backoff (https://developers.hellosign.com/docs/guides/events-and-callbacks/walkthrough.md).
- Official-API-first: no scrape.

## Implementation notes

- JDK `HttpClient` only. No Dropbox Sign / HelloSign SDK.
- API key: `Authorization: Basic base64(api_key + ":")`. Never put the key in the URL.
- Uploads: multipart from `file` refs. Downloads: stream to file store; OUTPUT type `file` only (do not implement `files_as_data_uri` as a stored type).
- Timeouts: 60s default; files download up to 120s; cap buffer (verify current max file size in OpenAPI — **UNVERIFIED** numeric cap at write time; fail closed if Content-Length exceeds the node cap).
- Retry: 429 and 409-preparing with backoff. Do not retry cancel/delete.
- Errors: 4xx/5xx → `{ error }`. Never log API keys or PDF bytes.
- `listen` only updates `callback_url`. Event handling is ingress, not a FUNCTION port.
