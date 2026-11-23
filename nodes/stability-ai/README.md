# Stability AI

Official Stability REST **v2beta** Stable Image node (`templateId` from `node_definition.json` `_id`, override `SQ_TEMPLATE_ID_STABILITY_AI`). Dispatched by `OfficialApiNode` from `api_spec.json`. No custom Java handler.

## Official API

- **Name / version:** Stable Image REST v2beta (researched 2026-08-20). gRPC / REST v1 are legacy — not this node.
- **Origin:** `https://api.stability.ai`
- **Docs:** [API reference](https://platform.stability.ai/docs/api-reference/) · [Stable Image](https://platform.stability.ai/docs/getting-started/stable-image)
- **Auth:** StudioQuack secret `api_key` → `Authorization: Bearer`. Never on INPUT. Never logged.

## Scope

One image provider for first-party Stable Image **generate / upscale / edit / control**. `get_result` polls async jobs (`GET /v2beta/results/{id}`; HTTP 202 = still running). Ports are **`input` / `output`**. **No FUNCTION ports.** Not an Agent model node.

Dropped: REST v1 engines, gRPC, Stable Audio, 3D, video. Do not scrape DreamStudio.

## Operations

| operation | HTTP | Path | When to use |
|---|---|---|---|
| `generate` | POST | `/v2beta/stable-image/generate/{engine}` | Text-to-image. `{engine}`: `core` \| `ultra` \| `sd3` |
| `upscale` | POST | `/v2beta/stable-image/upscale/{upscale_mode}` | `{upscale_mode}`: `conservative` \| `creative` \| `fast`. `creative` is async → `get_result` |
| `edit` | POST | `/v2beta/stable-image/edit/{edit_mode}` | inpaint, outpaint, erase, search-and-replace, search-and-recolor, remove-background, replace-background-and-relight |
| `control` | POST | `/v2beta/stable-image/control/{control_mode}` | sketch, structure, style, style-transfer |
| `get_result` | GET | `/v2beta/results/{id}` | Poll async generation id |

Official content-type is **multipart/form-data** (`bodyFormat: multipart` in `api_spec.json`). `bodyFrom` fields are sent as multipart parts with a generated `boundary` on `Content-Type`. Agent tools should set `operation` explicitly; when only `id` is sent (no `prompt`/`image`), the handler infers `get_result`.

## Ports

| Port | Direction | Type | Notes |
|---|---|---|---|
| `input` | INPUT | object | `prompt`, `image`/`mask` type **file**, `id` for poll. No secrets. |
| `output` | OUTPUT | `{status, http_status, data}` . Errors raise a BPMN error for the error boundary event (INVALID_INPUT, AUTH_FAILED, RATE_LIMITED, HTTP_<status>, REQUEST_FAILED); secrets are redacted. | Secrets redacted. |

## Configuration parameters

| Name | Type | Secret | Purpose |
|---|---|---|---|
| `operation` | select | no | Verb above. Default `generate`. |
| `api_key` | password | **yes** | Bearer token. |
| `engine` | select | no | generate path: `core` \| `ultra` \| `sd3`. |
| `upscale_mode` | select | no | `conservative` \| `creative` \| `fast`. |
| `edit_mode` | select | no | Documented edit path segment. |
| `control_mode` | select | no | Documented control path segment. |
| `timeout_seconds` | number | no | Default `120`. |

## Legal

- [Terms of Service](https://stability.ai/terms-of-service)
- Content moderation: HTTP 403 when flagged. Output license follows the customer’s Stability plan. No CSAM. Prompts and images **leave the host**.
- Official-API-first: no scrape.
