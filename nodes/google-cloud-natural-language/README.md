# Google Cloud Natural Language

Official Cloud Natural Language REST v2 (`templateId` from `node_definition.json` `_id`, override `SQ_TEMPLATE_ID_GOOGLE_CLOUD_NATURAL_LANGUAGE`). Dispatched by `OfficialApiNode` from `api_spec.json`. No custom Java handler.

## Official API

- **Name / version:** Cloud Natural Language API **v2** at `https://language.googleapis.com`
- **Docs (2026-08-22):** [REST reference](https://docs.cloud.google.com/natural-language/docs/reference/rest) · [analyzeSentiment](https://docs.cloud.google.com/natural-language/docs/reference/rest/v2/documents/analyzeSentiment) · [moderateText](https://docs.cloud.google.com/natural-language/docs/reference/rest/v2/documents/moderateText) · [Moderate text guide](https://docs.cloud.google.com/natural-language/docs/moderating-text)
- **Auth:** Google API key as header `x-goog-api-key` (official API-key transport). OfficialApiNode does not mint service-account JWTs. Enable the Natural Language API on the GCP project that owns the key.

## Scope

Current **v2** methods used here:

| operation | HTTP | Path |
|---|---|---|
| `analyze_sentiment` | POST | `/v2/documents:analyzeSentiment` |
| `analyze_entities` | POST | `/v2/documents:analyzeEntities` |
| `classify` | POST | `/v2/documents:classifyText` |
| `moderate_text` | POST | `/v2/documents:moderateText` |

**classify is not retired in v2.** **analyze_syntax is omitted** — it exists on v1 / v1beta2 only, not on v2.

`analyze_sentiment` / `analyze_entities` / `moderate_text` send the official `document` object plus `encodingType` or `modelVersion`. `classify` takes purpose fields `type`, `content`, `languageCode`, `gcsContentUri` (merged into `document` via `bodyMap`).

## Ports

`input` / `output` only.

## Configuration

| Name | Type | Secret | Purpose |
|---|---|---|---|
| `operation` | select | no | Default `analyze_sentiment` |
| `api_key` | password | **yes** | `x-goog-api-key` |
| `timeout_seconds` | number | no | Default 25 |

## Legal

- [Google Cloud Terms](https://cloud.google.com/terms) and Natural Language data-processing terms on that project
- Text leaves the host to Google. Do not send PHI without a covering DPA/BAA on the GCP project.
- Logs: operation, status, sizes — never document `content`, never `api_key`.
