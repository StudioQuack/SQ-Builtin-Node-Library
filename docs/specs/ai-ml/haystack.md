# Haystack

- **Status:** planned
- **Catalog:** `docs/catalog/ai-ml.md`
- **Folder when built:** `nodes/haystack/`
- **Researched:** 2026-08-20
- **Official API:** Haystack Enterprise Platform REST **v1** (`https://api.cloud.deepset.ai`) and/or official **Hayhooks** HTTP (`POST /{pipeline_name}/run`)
- **Docs:** [Enterprise Platform](https://docs.cloud.deepset.ai/docs/getting-started), [Search](https://docs.cloud.deepset.ai/docs/api/main/search-api-v-1-workspaces-workspace-name-pipelines-pipeline-name-search-post), [Upload File](https://docs.cloud.deepset.ai/docs/api/main/upload-file-api-v-1-workspaces-workspace-name-files-post), [Hayhooks](https://docs.haystack.deepset.ai/docs/hayhooks), [Hayhooks API](https://deepset-ai.github.io/hayhooks/reference/api-reference/)
- **Auth:** api_key — secret binding: `api_key` (`Authorization: Bearer` on SaaS; optional header on Hayhooks)

## Scope

Call an **already-deployed** Haystack pipeline over official HTTP. Prefer **Agent + Docling + databases vector ops** for new RAG graphs. This node exists because deepset publishes a product REST API (Haystack Enterprise Platform, formerly deepset Cloud) and an official serving layer (Hayhooks).

**Do not** embed the Apache-2.0 Python library in the daemon, author YAML pipelines from StudioQuack, wrap unofficial FastAPI servers, or expose Hayhooks MCP / OpenAI-compat chat (those overlap Agent + model nodes).

Hayhooks has **no built-in auth** by default. Production Hayhooks must sit behind the operator’s auth; bind `api_key` when the server requires a header.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `run_pipeline` | POST | SaaS: `/api/v1/workspaces/{workspace}/pipelines/{pipeline}/search` · Hayhooks: `/{pipeline_name}/run` | Run a deployed query/agent pipeline |
| `query` | POST | Same as `run_pipeline` on SaaS search; Hayhooks `/{pipeline_name}/run` | Alias when the graph is retrieval/QA; same HTTP |
| `index` | POST | SaaS: `/api/v1/workspaces/{workspace}/files` (multipart or text create) · Hayhooks: `/{index_pipeline}/run` with files | Upload/index files into the workspace or an indexing pipeline |

Dropped: `/search-stream`, OpenAI-compat `/v1/chat/completions`, MCP `/mcp`, pipeline CRUD/YAML deploy, OpenSearch `_search` / `_sql` admin dumps, document-query internals.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `run_pipeline` | no | Row above |
| `server` | select | yes | `saas` | no | `saas` (Enterprise Platform) \| `hayhooks` |
| `base_url` | string | `hayhooks`; optional on saas | SaaS: `https://api.cloud.deepset.ai` | no | Origin only, HTTPS except loopback Hayhooks. No path suffix. SSRF: allowlist host. |
| `api_key` | password | `saas`; hayhooks if the server requires it | empty | **yes** | SaaS: `Authorization: Bearer`. Hayhooks: `Authorization: Bearer` when set; omit header if empty. Never on INPUT. |
| `workspace` | string | `saas` | empty | no | Path `{workspace_name}` |
| `pipeline` | string | `run_pipeline` / `query` | empty | no | Deployed pipeline name |
| `index_pipeline` | string | `hayhooks` + `index` | empty | no | Hayhooks pipeline that ingests files |
| `timeout_seconds` | number | no | `120` | no | Connect+read. Floor 1, cap 300. Idle SaaS pipelines may cold-start. |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object | Files are `file` ref-ids. No secrets. |
| `output` | OUTPUT | `{ status, … }` or `{ error }` | Never echo `api_key` |

FUNCTION ports: none. Do not fan out to model/tool nodes from this handler.

## Input fields (by operation)

### `operation = run_pipeline` | `query`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `query` | string | yes on SaaS (or `queries`) | Search string. Alias `queries`: string or `string[]` → SaaS body `queries`. |
| `queries` | string[] | if `query` omitted | SaaS `queries` array |
| `filters` | object | no | SaaS Haystack filters object |
| `params` | object | no | Pipeline runtime params (`params`) |
| `body` | object | Hayhooks | JSON body for `/{name}/run` (pipeline-specific). If set, sent as-is (no secret keys). If omitted, `{ "query": query }` or `{ "queries": queries }`. |
| `pipeline` | string | if config blank | Override pipeline / Hayhooks name |
| `workspace` | string | no | Override config workspace (SaaS) |

SaaS JSON: `{ "queries": [...], "filters"?, "params"? }`. Hayhooks: `Content-Type: application/json` unless files are present (then `multipart/form-data` as Hayhooks documents).

### `operation = index`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `files` | file[] | yes unless `text`+`filename` | Platform files. Max 10, **25_000_000** bytes each. |
| `filename` | string | with `text` | SaaS create-text-file name |
| `text` | string | with `filename` | SaaS inline text file contents. Cap 2_000_000 chars. |
| `metadata` | object | no | Passed if the endpoint documents it; otherwise ignored |
| `pipeline` | string | Hayhooks if config `index_pipeline` blank | Indexing pipeline name |

SaaS: `multipart/form-data` file parts as documented (`POST .../files`). Hayhooks: multipart per Hayhooks file-upload docs to `/{index_pipeline}/run`.

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `result` | object | success | Provider JSON (answers/documents). Cap body; do not log full text. |
| `file_id` | string | SaaS `index` 201 | Uploaded file id if present |
| `error` | string | failure | Actionable; key redacted |
| `http_status` | number | HTTP error | Provider status |

## Legal

- Haystack library / Hayhooks: [Apache License 2.0](https://github.com/deepset-ai/haystack/blob/main/LICENSE), [Hayhooks license](https://deepset-ai.github.io/hayhooks/about/license/)
- Haystack Enterprise Platform: [deepset Terms and Conditions](https://www.deepset.ai/terms-and-conditions) (order form / commercial)
- SaaS: queries and files **leave the host** to deepset. Hayhooks: traffic stays on the operator’s Hayhooks URL (still leaves this container).
- Official-API-first: no unofficial pipeline servers; no scrape of the Builder UI.

## Implementation notes

- JDK `HttpClient` only. TLS verified. `Redirect.NEVER`.
- SaaS host must be `api.cloud.deepset.ai` unless `base_url` is a documented deepset host. Hayhooks `base_url`: HTTPS, or `http://127.0.0.1` / `http://localhost` only.
- Empty SaaS `api_key` → `{ error }` no HTTP.
- Response cap **2_000_000** bytes. 429 → `{ error, http_status: 429, retry_after_seconds }` (honor `Retry-After`). Do not auto-retry.
- Logs: operation, workspace length, pipeline name, status, sizes — never Bearer token, never file bytes, never full query if it may contain PII (log length only).
- Handler never throws past dispatch.
