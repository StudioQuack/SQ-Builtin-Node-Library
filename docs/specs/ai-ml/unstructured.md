# Unstructured

- **Status:** planned
- **Catalog:** `docs/catalog/ai-ml.md`
- **Folder when built:** `nodes/unstructured/`
- **Researched:** 2026-08-20
- **Official API:** Unstructured Partition Endpoint (`POST https://api.unstructuredapp.io/general/v0/general`) plus documented `chunking_strategy` on the same path
- **Docs:** [Overview](https://docs.unstructured.io/api-reference/overview) · [Partition](https://docs.unstructured.io/api-reference/legacy-api/partition/overview) · [Parameters](https://docs.unstructured.io/api-reference/legacy-api/partition/api-parameters)
- **Auth:** `api_key` — secret binding: `api_key` (header `unstructured-api-key`)

## Scope

Doc ETL: **partition** (elements) and **chunk** (same HTTP + `chunking_strategy`). Ports **`input` / `output`**. File payloads type **file**. **SaaS sends bytes off-host.** Prefer local **Docling** when the customer must keep bytes on the daemon.

Unstructured labels the Partition Endpoint “legacy” and recommends Pipeline **local-file jobs** (`POST /api/v1/jobs/`) for production. Catalog verbs remain partition/chunk; those jobs are a later operation set, not invented aliases. Do not wrap the open-source Python library in the daemon.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `partition` | POST | `/general/v0/general` | Extract elements. `files` required. |
| `chunk` | POST | `/general/v0/general` | Same call; `chunking_strategy` required: `basic` \| `by_title` \| `by_page` \| `by_similarity` |

Dropped: inventing a second host for “chunk only”; scraping platform.unstructured.io; embedding OSS `partition()`.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `partition` | no | Row above |
| `api_key` | password | yes | empty | **yes** | Header `unstructured-api-key`. Never on INPUT. |
| `timeout_seconds` | number | no | `180` | no | Large PDFs can take minutes. Floor 1. |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object | `files` type **file**. Optional `strategy` (`auto` \| `vlm` \| `hi_res` \| `fast` \| `ocr_only`). |
| `output` | OUTPUT | `{ status, http_status, data }` or `{ error }` | Do not log full text (may be PHI). |

FUNCTION ports: none.

## Legal

- [Trust Portal](https://trust.unstructured.io/) (SOC 2, HIPAA, GDPR claims as published by Unstructured)
- Customer is controller of uploaded documents. Minimize: send only files the workflow needs. Official-API-first.
