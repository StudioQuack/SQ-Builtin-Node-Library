# Qwen Cloud

Official Alibaba Cloud Model Studio (DashScope) Qwen node (`templateId` from `node_definition.json` `_id`). Dispatched by `OfficialApiNode` from `api_spec.json`. No custom Java handler.

## Official API

- **Name / version:** DashScope OpenAI-compatible mode + native AIGC image/video REST
- **Origin (intl / Singapore, default):** `https://dashscope-intl.aliyuncs.com`
- **Origin (PRC / Beijing):** `https://dashscope.aliyuncs.com`
- **Origin (US / Virginia):** `https://dashscope-us.aliyuncs.com`
- **Docs:** [OpenAI compatibility](https://www.alibabacloud.com/help/en/model-studio/compatibility-of-openai-with-dashscope) · [DashScope Qwen API](https://www.alibabacloud.com/help/en/model-studio/qwen-api-via-dashscope) · [Text-to-image](https://www.alibabacloud.com/help/en/model-studio/text-to-image) · [Image HTTP](https://www.alibabacloud.com/help/en/model-studio/first-call-to-image-and-video-api) · [Related agreements](https://www.alibabacloud.com/help/en/model-studio/related-agreements)
- **Auth:** StudioQuack secret `api_key` → `Authorization: Bearer`. Never on INPUT. Never logged.

## Scope

One node for Alibaba Cloud Model Studio Qwen / Wan. Chat and vision share OpenAI-compat `POST /compatible-mode/v1/chat/completions`. Catalog `analyze_image` is **not** a separate RPC — it is the same path with multimodal `image_url` parts. Image and video generation use native DashScope AIGC paths (async). Sits on the AI Agent `model` FUNCTION port via `llm_input` → `llm_output`. **No extra FUNCTION ports.**

Do not scrape tongyi.aliyun.com. Do not call Anthropic-compat DashScope (`/apps/anthropic`) from this node.

## Base URL (intl vs PRC vs US)

API keys are **not interchangeable** across regions. Wrong host → HTTP 401. Set `base_url` to the origin for the key's region (interpolated as `api_spec` `{base_url}`).

| Region | `base_url` (origin only) |
|---|---|
| Singapore (intl, default) | `https://dashscope-intl.aliyuncs.com` |
| Beijing (PRC) | `https://dashscope.aliyuncs.com` |
| US (Virginia) | `https://dashscope-us.aliyuncs.com` |

Alibaba also documents workspace-specific hosts, e.g. `https://{WorkspaceId}.ap-southeast-1.maas.aliyuncs.com` (Singapore) and `https://{WorkspaceId}.cn-beijing.maas.aliyuncs.com` (Beijing). Put that origin in `base_url` when the console shows it.

## Operations

| operation | HTTP | Path | When to use |
|---|---|---|---|
| `chat` | POST | `/compatible-mode/v1/chat/completions` | Agent model / tools |
| `analyze_image` | POST | `/compatible-mode/v1/chat/completions` | VL image/video understanding (same RPC) |
| `generate_image` | POST | `/api/v1/services/aigc/text2image/image-synthesis` | Wan / Qwen text-to-image (async) |
| `generate_video` | POST | `/api/v1/services/aigc/video-generation/video-synthesis` | Wan text/image-to-video (async) |

## Ports

| Port | Direction | Type | Notes |
|---|---|---|---|
| `llm_input` | INPUT | `llm_input` | Chat: `messages`. Image/video: DashScope `{model, input, parameters}`. Media: type `file`. |
| `llm_output` | OUTPUT | `llm_output` | Async image/video returns a task id. |

## Configuration parameters

| Name | Type | Secret | Purpose |
|---|---|---|---|
| `operation` | select | no | Verb above. Default `chat`. |
| `api_key` | password | **yes** | Bearer token for **that** origin. |
| `base_url` | string | no | DashScope origin (HTTPS). Default intl. |
| `model` | string | no | Chat/VL/Wan id from the region catalog. |
| `timeout_seconds` | number | no | Default `300`. |

## Legal

- [Alibaba Cloud Model Studio — related agreements](https://www.alibabacloud.com/help/en/model-studio/related-agreements) (Terms of Service + Model Inference SLA + open-source model licenses).
- PRC data-residency: `prc` / `dashscope.aliyuncs.com` processes in China (Beijing). Intl default is Singapore. Customer is controller. Do not send PHI without a covering DPA.
- Official-API-first: DashScope hosts only.

## Implementation notes

- JDK HTTP via `OfficialApiNode`. Auth type `bearer`.
- Native image/video calls document header `X-DashScope-Async: enable`. **`OfficialApiNode` does not send that header.** Async submit may fail until a later handler revision adds it.
- Task poll `GET /api/v1/tasks/{task_id}` is official but not a catalog verb — not implemented here.
- Image URLs from DashScope are typically short-lived (docs: 24 hours). Never log `api_key`.

## Unverified

- Exact live default chat model id per region (use the Model Studio console list).
- Whether Qwen-Image synchronous image APIs share the Wan `image-synthesis` path or a distinct multimodal-generation path for some model families.
- Workspace-specific `{WorkspaceId}.*.maas.aliyuncs.com` vs shared `dashscope-intl` for every op in every account.
