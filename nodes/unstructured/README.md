# Unstructured

Official Unstructured **legacy Partition Endpoint** (`templateId` from `node_definition.json` `_id`, override `SQ_TEMPLATE_ID_UNSTRUCTURED`). Dispatched by `OfficialApiNode`.

## Official API

- **Origin:** `https://api.unstructuredapp.io` (use the URL issued with the Unstructured account when it differs)
- **Path:** `POST /general/v0/general`
- **Docs:** [Partition overview](https://docs.unstructured.io/api-reference/legacy-api/partition/overview) · [Parameters](https://docs.unstructured.io/api-reference/legacy-api/partition/api-parameters) · [Chunking](https://docs.unstructured.io/api-reference/legacy-api/partition/chunking)
- **Auth:** StudioQuack secret `api_key` → header `unstructured-api-key`. Never on INPUT. Never logged.

Both `partition` and `chunk` use `bodyFormat: multipart` (not JSON with a `multipart/form-data` label). INPUT `files` must be a platform **file** ref (ContentBlock), not a bare string id.

Unstructured labels this the **legacy** Partition Endpoint and recommends [Pipeline / local-file jobs](https://docs.unstructured.io/api-reference/overview) for production (`POST /api/v1/jobs/` and related). This node keeps catalog verbs **partition** and **chunk** on the still-published Partition Endpoint (chunking is `chunking_strategy` on the same POST). Prefer **local Docling** when bytes must stay on the daemon.

## Operations

| operation | HTTP | Path | When to use |
|---|---|---|---|
| `partition` | POST | `/general/v0/general` | Extract elements. `files` required. |
| `chunk` | POST | `/general/v0/general` | Same call with `chunking_strategy`: `basic` \| `by_title` \| `by_page` \| `by_similarity` |

Official content-type is **multipart/form-data**. OfficialApiNode JSON-encodes `bodyFrom` — binary `files` may need a later multipart path.

## Ports

`input` / `output` only. `files` is type **file**. Failures: `{ error }` — never includes `api_key`.

## Config

| Name | Type | Secret | Purpose |
|---|---|---|---|
| `operation` | select | no | `partition` \| `chunk` |
| `api_key` | password | **yes** | `unstructured-api-key` |
| `timeout_seconds` | number | no | Default `180` |

## Legal

- SaaS **sends file bytes off-host** to Unstructured. Do not send PHI without a covering DPA/BAA.
- Trust: https://trust.unstructured.io/
- Official API only. Do not scrape platform.unstructured.io.
