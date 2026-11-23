# Google Vertex AI

- **Status:** planned
- **Catalog:** `docs/catalog/ai-ml.md`
- **Folder when built:** `nodes/google-vertex-ai/`
- **Researched:** 2026-08-20
- **Official API:** Vertex AI / Gemini Enterprise Agent Platform REST **v1** on `aiplatform.googleapis.com` (regional `{LOCATION}-aiplatform.googleapis.com`)
- **Docs:** [Generative AI on Agent Platform](https://cloud.google.com/vertex-ai/generative-ai/docs) · [publishers.models generateContent](https://cloud.google.com/vertex-ai/docs/reference/rest/v1/projects.locations.publishers.models/generateContent) · [Tuning API](https://docs.cloud.google.com/vertex-ai/generative-ai/docs/model-reference/tuning) · [Vertex AI REST](https://cloud.google.com/vertex-ai/docs)
- **Auth:** service account JSON (OAuth 2.0 JWT bearer) — secret binding: `service_account_json`

## Scope

Enterprise Gemini (and Model Garden publishers) on **Google Cloud**, distinct from AI Studio Gemini (`google-gemini`). Auth is GCP OAuth access tokens from a service-account key, plus `project` and `location`. Ports are **IN `llm_input` / OUT `llm_output`** only (so the node can sit on the Agent `model` port). JDK HTTP + JDK JWT (RS256) like Google Sheets — **no** Google Cloud client libraries.

Do not call `generativelanguage.googleapis.com` from this node. Do not implement Agent Builder / Reasoning Engines / RAG corpora in v1.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `chat` | POST | `/v1/projects/{project}/locations/{location}/publishers/{publisher}/models/{model}:generateContent` (stream: `:streamGenerateContent`) | Gemini (and compatible publisher) chat / multimodal / tools. Default Agent model op. |
| `embeddings` | POST | `/v1/projects/{project}/locations/{location}/publishers/{publisher}/models/{model}:embedContent` | Gemini embedding models on Vertex. |
| `predict` | POST | `/v1/projects/{project}/locations/{location}/publishers/{publisher}/models/{model}:predict` | Model Garden / partner / older embedding & image models that still document **Predict**, not generateContent. |
| `tune_job` | POST | `/v1/projects/{project}/locations/{location}/tuningJobs` | Create a supervised tuning job (`tuningJobs.create`). |

Retired / unofficial verbs are omitted, not listed as "later". Catalog `tune_job` maps to **create** only (get/list/cancel are not v1 operations).

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `chat` | no | Row above |
| `service_account_json` | password | yes at runtime | | **yes** | Full SA key JSON (`client_email` + PKCS8 `private_key`). Same shape as Sheets. Never on INPUT. |
| `project` | string | yes | | no | GCP project id |
| `location` | string | yes | `us-central1` | no | Region (must match model availability) |
| `publisher` | string | no | `google` | no | Path segment `publishers/{publisher}` |
| `model` | string | yes for chat/embed/predict | `gemini-2.5-flash` | no | Model id **without** `publishers/…` prefix |
| `temperature` | — | — | — | — | Removed from node config: nest under INPUT `generationConfig` (OfficialApiNode cannot nest a flat config into generationConfig). |
| `max_output_tokens` | number | no | omit | no | `generationConfig.maxOutputTokens` |
| `top_p` | number | no | omit | no | |
| `safety_settings` | string | no | omit | no | JSON array for GenerateContentRequest |
| `stream` | select | no | `true` | no | `chat` only |
| `timeout_seconds` | number | no | `300` | no | Token POST **and** AI call |

File payloads use type `file` (platform ref-id), never inline bytes, on both this table and the ports below.

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `llm_input` | INPUT | v2 LLM object + per-operation fields | |
| `llm_output` | OUTPUT | `{ result }` | `{ error }` inside `result.error`. Token/JWT never in output. |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = chat`

Host: `https://{location}-aiplatform.googleapis.com`  
**POST** `/v1/projects/{project}/locations/{location}/publishers/{publisher}/models/{model}:generateContent`  
Header: `Authorization: Bearer {access_token}`.

Request body is Vertex **GenerateContentRequest** (same shape as Gemini API `contents` / `systemInstruction` / `tools` / `generationConfig` / `safetySettings`).

| Field | Type | Required | Purpose |
|---|---|---|---|
| `messages` | list | yes | Map `assistant`→`model`. Multimodal parts from platform `file` (GCS `gs://` URI allowed as `fileData.fileUri` when the operator passes it in `options.gcs_uri` — still no inline bytes in graph JSON). |
| `tools` | list | no | Function declarations |
| `tool_config` | object | no | Function-calling mode |
| `system_instruction` | string | no | |
| `generation_config` | object | no | Merged with instance sampling knobs |
| `safety_settings` | list | no | |
| `labels` | object | no | Request labels (not secrets) |
| `stream` | boolean | no | `:streamGenerateContent` |

### `operation = embeddings`

**POST** `.../models/{model}:embedContent`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `messages` or `text` | list / string | one required | `content` for EmbedContentRequest |
| `task_type` | string | no | Task type enum as documented for the embedding model |
| `title` | string | no | |
| `output_dimensionality` | number | no | When the model supports it |

If a specific embedding model **only** documents `:predict` (`instances[].content` / `parameters`), the handler MAY route that model id to `predict` automatically; otherwise the operator uses `operation=predict`.

### `operation = predict`

**POST** `.../models/{model}:predict`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `instances` | list | yes | Publisher-specific instance objects (text prompt, embedding content, Imagen instances, …). **No raw file bytes** — use `gs://` URIs or text. |
| `parameters` | object | no | Publisher-specific (temperature, sampleCount, …) |

This is the generic Model Garden RPC. Do not invent a second schema per publisher in v1; pass through JSON.

### `operation = tune_job`

**POST** `/v1/projects/{project}/locations/{location}/tuningJobs`

Documented create fields (supervised Gemini tuning):

| Field | Type | Required | Purpose |
|---|---|---|---|
| `base_model` | string | yes | Foundation model resource / id to tune |
| `training_dataset_uri` | string | yes | `gs://` JSONL URI ([supervised tuning datasets](https://docs.cloud.google.com/vertex-ai/generative-ai/docs/model-reference/tuning)) |
| `validation_dataset_uri` | string | no | `gs://` JSONL |
| `tuned_model_display_name` | string | no | Display name |
| `epoch_count` | number | no | Hyperparameter |
| `learning_rate_multiplier` | number | no | |
| `adapter_size` | string | no | When documented for the model |
| `encryption_spec.kms_key_name` | string | no | CMEK |
| `service_account` | string | no | SA email the job runs as (must already exist in the project) |

Do not upload training files through this node; data stays in the customer’s GCS.

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `result.role` | string | chat | `assistant` |
| `result.content` | list | chat / predict text | |
| `result.tool_calls` | list | functionCall | |
| `result.thoughts` | list | thinking | |
| `result.output_files` | list | image parts | Platform `file` refs |
| `result.finish_reason` | string | chat | Same mapping as Gemini node |
| `result.usage` | object | `usageMetadata` | Token counts |
| `result.embedding` | list | embeddings | `embedding.values` |
| `result.predictions` | list | predict | Raw PredictResponse `predictions` |
| `result.name` | string | tune_job | Tuning job resource name |
| `result.state` | string | tune_job | Job state as returned |
| `result.error` | string | failure | Redacted; never JWT / `private_key` / access_token |

## Legal

- ToS: [Google Cloud Terms](https://cloud.google.com/terms) and [Service Specific Terms](https://cloud.google.com/terms/service-terms) — **Generative AI Services** (section numbering moves; currently §20 in the fetched SST). Also [Google APIs ToS](https://developers.google.com/terms) where applicable.
- Competitive-use and generated-output rules in the SST apply. Grounding with Google Search has extra SST clauses.
- Quotas: Cloud Console → Vertex AI / Generative AI quotas (regional RPM, concurrent tuning jobs). 429 → `result.error`.
- PII / residency: data is processed in the configured **location** (plus Google’s documented multi-region behavior for some models). Customer is GCP project owner and controller. PHI requires a BAA covering that project. CMEK optional via `encryption_spec` on jobs.
- Official-API-first: no scrape of Cloud Console.

## Implementation notes

- **Auth (JDK only):** RS256 JWT `iss=client_email`, `scope=https://www.googleapis.com/auth/cloud-platform`, `aud=https://oauth2.googleapis.com/token`, `exp=now+3600`. POST `grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer`. Do **not** cache tokens in static fields (per-invoke, stack-local). Same pattern as Sheets.
- Host: `{location}-aiplatform.googleapis.com` (global `aiplatform.googleapis.com` only when the RPC docs say so — generateContent for publishers is regional).
- Timeouts: connect 15s; token + RPC share `timeout_seconds`.
- Stream: SSE / JSON stream of GenerateContentResponse; `emitPartial`.
- Retry: no 429 loop; one retry on 503.
- Size: bound `instances` JSON (e.g. 10 MB). Files via `file` refs or `gs://` only.
- Error mapping: Google JSON `error.code` / `error.status` / `error.message` → `result.error`. Redact `private_key`, assertion, `access_token`.
- Egress: `oauth2.googleapis.com:443` and `{location}-aiplatform.googleapis.com:443`.
- IAM: caller SA needs Vertex User / Generative AI appropriate roles (`aiplatform.endpoints.predict`, tuning job create, etc.). Least privilege is the customer’s duty.
