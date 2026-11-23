# PDF.co

- **Status:** planned
- **Catalog:** `docs/catalog/files-documents.md`
- **Folder when built:** `nodes/pdfco/`
- **Researched:** 2026-08-20
- **Official API:** PDF.co Web API v1 (REST, HTTPS only)
- **Docs:** https://developer.pdf.co/ (index also at https://apidocs.pdf.co/)
- **Auth:** `api_key` — secret binding: `api_key` (header `x-api-key`)

## Scope

One node for PDF.co’s official REST API (`https://api.pdf.co/v1`). Callers pick the verb with `operation`. File bytes are uploaded to PDF.co temporary storage (presigned PUT, up to 2 GB) and processed in PDF.co’s cloud — they leave the daemon. Do not scrape websites, do not wrap unofficial HTML tools, do not persist `base64` as a port or config type (PDF.co’s `outputDataFormat=base64` and `/file/upload/base64` exist on their side; the node must never store or expose them). FUNCTION ports: none.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `pdf_merge` | POST | `/v1/pdf/merge` | Combine PDFs into one PDF. Mixed office/image merge is `/v1/pdf/merge2` via `merge_mode=mixed` only. Combined input size cap **2 GB**. |
| `pdf_split` | POST | `/v1/pdf/split` | Split by page indexes/ranges (`pages`). Split-by-text/barcode is `/v1/pdf/split2` when `search_string` is set. |
| `pdf_convert` | POST | `/v1/pdf/convert/to/{fmt}` or `/v1/pdf/convert/from/{src}` | Convert PDF → csv/json/text/xls/xlsx/xml/html/jpg/png/webp/tiff, or office/csv/image → PDF. Not HTML/URL (those are separate ops). |
| `pdf_add_text` | POST | `/v1/pdf/edit/add` | Overlay text (and optional images/annotations) onto a PDF. |
| `pdf_extract_text` | POST | `/v1/pdf/convert/to/text` or `/v1/pdf/convert/to/text-simple` | Extract text. `simple=true` uses the low-credit non-OCR path. |
| `pdf_barcode` | POST | `/v1/barcode/generate` or `/v1/barcode/read/from/url` | `mode=generate` (image/PDF out) or `mode=read` (decoded values). |
| `html_to_pdf` | POST | `/v1/pdf/convert/from/html` or `/v1/pdf/convert/from/url` | HTML string **or** HTML `file`. String path is JSON `html` (request body ≤ 4 MB). File path: upload then convert from the returned URL. |
| `url_to_pdf` | POST | `/v1/pdf/convert/from/url` | Official HTML-from-URL conversion of an operator-supplied `page_url`. Not a generic web scraper. |

Retired / unofficial verbs are omitted. Internal helpers (presigned upload, `POST /v1/job/check`, `DELETE` temp file) are not catalog operations.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `pdf_merge` | no | Chooses the row above |
| `api_key` | password | yes | | yes | Dashboard API key; sent only as `x-api-key` |
| `async` | boolean | no | `false` | no | `true` for large jobs; poll `POST /v1/job/check` until `success`/`failed`/`aborted` |
| `timeout_seconds` | number | no | `120` | no | Per-request read timeout. Async poll budget is `min(timeout_seconds, 600)` |
| `expiration_minutes` | number | no | `60` | no | Vendor output-link TTL (`expiration`). Temp files auto-delete after ~1 hour by default |

File payloads (upload, download, convert) use type `file` (platform ref-id), never inline bytes, on both this table and the ports below.

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | |
| `output` | OUTPUT | object — `{ status, … }` or `{ error }` | Failures never include secrets or file bytes |

FUNCTION ports: none.

## Input fields (by operation)

### Shared file ingest (all ops that take documents)

Resolve each `file` ref-id, stream bytes to PDF.co via `GET /v1/file/upload/get-presigned-url` then `PUT` to `presignedUrl` (do not use `/file/upload/base64`). Pass the returned `url` as the vendor `url` field. JSON request bodies must stay ≤ **4 MB**. Uploaded files up to **2 GB** require `async=true`.

### `operation = pdf_merge`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `files` | file[] | yes | Source PDFs (ref-ids), merge order preserved |
| `merge_mode` | string | no | `pdf` (default, `/pdf/merge`) or `mixed` (`/pdf/merge2` for PDF+office+images+zip) |
| `name` | string | no | Output filename |

### `operation = pdf_split`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `file` | file | yes | Source PDF |
| `pages` | string | yes* | 1-based ranges, e.g. `1-4,5,6-10`, or `*` for every page. Required unless `search_string` is set |
| `search_string` | string | no | If set, call `/v1/pdf/split2` (split on text or `[[barcode:…]]` macros) |
| `name` | string | no | Output name / zip name |

### `operation = pdf_convert`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `file` | file | yes | Source document |
| `direction` | string | yes | `to` (PDF → other) or `from` (other → PDF) |
| `format` | string | yes | `to`: `csv` `json` `text` `xls` `xlsx` `xml` `html` `jpg` `png` `webp` `tiff`. `from`: `doc` `csv` `image` |
| `pages` | string | no | Page selection where the endpoint supports it |
| `lang` | string | no | OCR language code (e.g. `eng`) when converting scans |
| `inline` | boolean | no | Vendor `inline` for text-like outputs. Convert **OUTPUT is still type `file`** (store text as a file ref, never as port bytes) |

### `operation = pdf_add_text`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `file` | file | yes | Source PDF |
| `annotations` | object[] | yes | Overlay objects per `/v1/pdf/edit/add` (`text`, coordinates, font, pages). Do not log contents |
| `image` | file | no | Optional image overlay (upload first; pass vendor URL inside annotations) |

### `operation = pdf_extract_text`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `file` | file | yes | Source PDF or image |
| `simple` | boolean | no | `true` → `/v1/pdf/convert/to/text-simple` (no OCR). Default `false` → `/v1/pdf/convert/to/text` |
| `pages` | string | no | Page range |
| `lang` | string | no | OCR language when `simple=false` |

### `operation = pdf_barcode`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `mode` | string | yes | `generate` or `read` |
| `file` | file | yes* | Required for `read` (PDF/image) |
| `value` | string | yes* | Payload for `generate` |
| `barcode_type` | string | no | QR Code, Code 128, Code 39, Datamatrix, PDF417, EAN, etc. |
| `name` | string | no | Generated image/PDF filename |

### `operation = html_to_pdf`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `html` | string | yes* | Raw HTML. Required unless `html_file` is set. JSON body ≤ 4 MB |
| `html_file` | file | yes* | HTML as a file ref. Required unless `html` is set. If both set, `html_file` wins |
| `margins` | string | no | `{top} {right} {bottom} {left}` with units |
| `paperSize` | string | no | Default `A4` |
| `orientation` | string | no | `Portrait` (default) or `Landscape` |
| `header` | string | no | HTML header |
| `footer` | string | no | HTML footer |
| `printBackground` | boolean | no | Default `true` |
| `mediaType` | string | no | `print` (default), `screen`, `none` |
| `name` | string | no | Output PDF name |

### `operation = url_to_pdf`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `page_url` | string | yes | Public HTTPS URL the vendor fetches. Daemon SSRF/egress still applies to *our* upload/download hosts only; the vendor fetch is PDF.co-side |
| `margins` / `paperSize` / `orientation` / `header` / `footer` | string | no | Same as `html_to_pdf` |
| `name` | string | no | Output PDF name |

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | success | `ok` |
| `file` | file | convert/merge/add/html/url/generate | Result document (ref-id). **Convert OUTPUT is type `file`** |
| `files` | file[] | `pdf_split` when multiple PDFs | One ref-id per part; if vendor returns a zip, also set `file` to that zip |
| `text` | string | `pdf_extract_text`, `pdf_barcode` read | Extracted text / decoded values. Cap length; do not log |
| `pageCount` | number | when vendor returns it | Page count |
| `jobId` | string | async | Vendor job id |
| `remainingCredits` | number | when present | Account credits left (not a secret) |
| `error` | string | failure | Actionable vendor/HTTP error; never the API key |

## Legal

- Terms of Use: https://www.pdf.co/resources/legal/terms
- Security / retention: https://developer.pdf.co/knowledgebase/security.md — temporary files encrypted at rest, default TTL **1 hour** (configurable via `expiration`, plan max up to days); explicit delete via file-delete API
- Upload overview: https://developer.pdf.co/api/file-upload/overview.md — temp files auto-removed after 1 hour; presigned PUT up to 2 GB
- Request size: JSON body **≤ 4 MB**; TLS 1.2+
- Credits: billed per endpoint/page — https://developer.pdf.co/api/credits-per-api-function.md
- PII / PHI: **file bytes leave the daemon to PDF.co cloud**. Operator must have a lawful basis. PDF.co states DPA / SCCs are available **on request** (AWS-hosted). Do not send PHI without a signed DPA/BAA covering this processor
- Official-API-first: no scrape, no unofficial wrapper

## Implementation notes

- JDK `java.net.http.HttpClient` only (no vendor SDK).
- Auth: `x-api-key` from `configurationParameters.api_key` only. Never on INPUT. Never logged or returned.
- Timeouts: connect 10 s; read `timeout_seconds`. Async: poll `POST /v1/job/check` `{ "jobid" }` with exponential backoff (1s → 8s). Stop on `success` / `failed` / `aborted` / HTTP 404 (`Job not found` is final). Honor overall poll budget.
- Size caps: reject JSON bodies > 4 MB; reject merge sets > 2 GB combined; stream PUT/GET, do not buffer whole files in heap when avoidable.
- Retries: 429 / 503 with jitter; honor `Retry-After` if present. Do not retry 401/403/404. Do not retry a completed vendor job (credits).
- Error mapping: HTTP 4xx/5xx or `error: true` in JSON → `{ error }` on OUTPUT, never throw past dispatch.
- After download, store via platform file service as type `file`. Do not keep vendor URLs as the stored type. Best-effort `file/delete` on temp inputs after success.
- Logs: shapes, ids, sizes, status codes — never HTML, file bytes, or the API key.
