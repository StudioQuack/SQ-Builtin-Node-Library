# Haystack

Official deepset **Haystack Enterprise Platform** REST v1 + **Hayhooks** HTTP (`templateId` from `node_definition.json` `_id`, override `SQ_TEMPLATE_ID_HAYSTACK`). Dispatched by `OfficialApiNode`. Spec: `docs/specs/ai-ml/haystack.md`.

## Official API

- **SaaS origin:** `https://api.cloud.deepset.ai`
- **Docs:** [Enterprise Platform](https://docs.cloud.deepset.ai/docs/getting-started) · [Search](https://docs.cloud.deepset.ai/docs/api/main/search-api-v-1-workspaces-workspace-name-pipelines-pipeline-name-search-post) · [Upload File](https://docs.cloud.deepset.ai/docs/api/main/upload-file-api-v-1-workspaces-workspace-name-files-post) · [Hayhooks](https://docs.haystack.deepset.ai/docs/hayhooks)
- **Auth:** StudioQuack secret `api_key` → `Authorization: Bearer` (required on SaaS). Never on INPUT. Never logged.

## Scope

Call an **already-deployed** pipeline. Prefer Agent + Docling + databases vector ops for new RAG graphs. Do not embed the Haystack Python library, deploy YAML from StudioQuack, or expose Hayhooks MCP / OpenAI-compat chat.

SaaS operations use `https://api.cloud.deepset.ai`. `hayhooks_run` / `hayhooks_index` call the official Hayhooks paths (`/{pipeline}/run`) on the self-hosted server set in `hayhooks_url` (default `http://localhost:1416`).

## Operations

| operation | HTTP | Path |
|---|---|---|
| `run_pipeline` | POST | `/api/v1/workspaces/{workspace}/pipelines/{pipeline}/search` |
| `query` | POST | same as `run_pipeline` |
| `index` | POST | `/api/v1/workspaces/{workspace}/files` |
| `hayhooks_run` | POST | `{hayhooks_url}/{pipeline}/run` |
| `hayhooks_index` | POST | `{hayhooks_url}/{index_pipeline}/run` |

SaaS search body: `{ "queries": [...], "filters"?, "params"? }`. Send `queries` as a string array (the generic handler does not alias `query` → `queries`).

## Ports

`input` / `output` only. Files are type **file**. Errors raise a BPMN error for the error boundary event (INVALID_INPUT, AUTH_FAILED, RATE_LIMITED, HTTP_<status>, REQUEST_FAILED); secrets are redacted. 

## Config

| Name | Type | Secret | Purpose |
|---|---|---|---|
| `operation` | select | no | Verb above. Default `run_pipeline`. |
| `api_key` | password | **yes** | Bearer. Always sent; for an unauthenticated Hayhooks server bind a placeholder value. |
| `workspace` | string | no | SaaS `{workspace_name}`. |
| `pipeline` | string | no | Deployed pipeline name. |
| `index_pipeline` | string | no | Hayhooks index pipeline. |
| `hayhooks_url` | string | no | Self-hosted Hayhooks base URL for `hayhooks_*` ops. Default `http://localhost:1416`. |
| `timeout_seconds` | number | no | Default `120`. |

## Legal

- Library / Hayhooks: Apache-2.0
- SaaS: [deepset Terms](https://www.deepset.ai/terms-and-conditions). Queries and files **leave the host** on SaaS.
