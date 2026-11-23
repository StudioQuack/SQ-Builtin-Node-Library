# AWS Bedrock

Official Amazon Bedrock node (`templateId` from `node_definition.json` `_id`, override `SQ_TEMPLATE_ID_AWS_BEDROCK`). Dispatched by `OfficialApiNode` from `api_spec.json`. No custom Java handler.

## Official API

- **Name / version:** Amazon Bedrock Runtime (Converse, InvokeModel) + control-plane ListFoundationModels. REST as of 2026-08-20.
- **Origin:** `https://bedrock-runtime.{region}.amazonaws.com` (inference) and `https://bedrock.{region}.amazonaws.com` (list models).
- **Docs:** [Converse](https://docs.aws.amazon.com/bedrock/latest/APIReference/API_runtime_Converse.html) · [InvokeModel](https://docs.aws.amazon.com/bedrock/latest/APIReference/API_runtime_InvokeModel.html) · [ListFoundationModels](https://docs.aws.amazon.com/bedrock/latest/APIReference/API_ListFoundationModels.html) · [Conversation inference](https://docs.aws.amazon.com/bedrock/latest/userguide/conversation-inference.html)
- **Auth:** AWS SigV4 (`AWS4-HMAC-SHA256`, signing name `bedrock`) using StudioQuack secrets `access_key` + `secret_access_key`. Region from config `region`.

## Scope

One LLM provider for Amazon Bedrock on the customer’s AWS account. Catalog `chat` maps to `converse`. `embeddings` is not a separate REST verb — use `invoke_model` with an embedding model body. Ports: **IN `llm_input` / OUT `llm_output`**. **No extra FUNCTION ports.** IAM least-privilege is the customer’s duty (`bedrock:InvokeModel`, `bedrock:ListFoundationModels`).

## Operations

| operation | HTTP | Path | When to use |
|---|---|---|---|
| `converse` | POST | `bedrock-runtime.{region}.amazonaws.com/model/{model_id}/converse` | Agent model (unified messages) |
| `invoke_model` | POST | `bedrock-runtime.{region}.amazonaws.com/model/{model_id}/invoke` | Model-specific JSON body |
| `list_foundation_models` | GET | `bedrock.{region}.amazonaws.com/foundation-models` | Discover model ids |

## Ports

| Port | Direction | Type | Notes |
|---|---|---|---|
| `llm_input` | INPUT | `llm_input` | Converse `{messages, system?, inferenceConfig?}`. Image/document use type `file`. |
| `llm_output` | OUTPUT | `llm_output` | `{status, data}`. Secrets redacted. |

## Configuration parameters

| Name | Type | Secret | Purpose |
|---|---|---|---|
| `operation` | select | no | Verb above. Default `converse`. |
| `access_key` | password | **yes** | SigV4 access key id (`auth.configKey`). |
| `secret_access_key` | password | **yes** | SigV4 signing secret (`auth.secretKeyConfigKey`). |
| `region` | string | no | Hostname region (default `us-east-1`). |
| `model_id` | string | no | Path model id / ARN. Current Claude-in-Bedrock converse ids include `anthropic.claude-sonnet-5` / `anthropic.claude-opus-5` / `anthropic.claude-fable-5`. Docs: https://docs.aws.amazon.com/bedrock/latest/userguide/model-card-anthropic-claude-sonnet-5.html |
| `timeout_seconds` | number | no | Default `300`. |

## Legal

- [AWS Customer Agreement](https://aws.amazon.com/agreement/)
- [AWS Service Terms](https://aws.amazon.com/service-terms/)
- [Serverless third-party model terms](https://aws.amazon.com/legal/bedrock/third-party-models/)
- Prompts leave the host to AWS in the selected region. Third-party foundation models have additional EULAs on first invoke. PHI only with a covering BAA on that AWS account. Official-API-first: no scrape of the AWS console.

## Implementation notes

- JDK HTTP via `OfficialApiNode`. Auth type `aws-sigv4`; signing name `bedrock`; secret from `secret_access_key`.
- `baseUrl` is `https://` so `{region}` can be interpolated into the hostname.
- `{model_id}` is URL-encoded; `:` in model ids becomes `%3A` (unverified whether every regional endpoint accepts that).
- HTTP 4xx/5xx → `{error}` on OUTPUT. Never log `access_key` or `secret_access_key`.
