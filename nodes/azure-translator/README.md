# Azure Translator

Official Azure AI Translator node (`templateId` from `node_definition.json` `_id`, override `SQ_TEMPLATE_ID_AZURE_TRANSLATOR`). Dispatched by `OfficialApiNode` from `api_spec.json`. No custom Java handler.

## Official API

- **Name / version:** Translator REST **api-version=2026-06-06** (GA). Researched 2026-08-22.
- **Origin:** `https://api.cognitive.microsofttranslator.com`
- **Docs:** [migrate to 2026-06-06](https://learn.microsoft.com/en-us/azure/ai-services/translator/text-translation/how-to/migrate-to-2026-06-06) · [translate](https://learn.microsoft.com/en-us/azure/ai-services/translator/text-translation/2026-06-06/translate-api) · [transliterate](https://learn.microsoft.com/en-us/azure/ai-services/translator/text-translation/2026-06-06/transliterate-api) · [overview](https://learn.microsoft.com/en-us/azure/ai-services/translator/text-translation/2026-06-06/overview)
- **Auth:** StudioQuack secret `subscription_key` → `Ocp-Apim-Subscription-Key`. Optional `region` → `Ocp-Apim-Subscription-Region` (omitted when blank). Never on INPUT. Never logged.

## Migration from v3.0

`2026-06-06` is **not backward compatible**. Body uses `{ "inputs": [ { "text", "targets": [ { "language" } ] } ] }` (response key `value`).

**Removed in 2026-06-06 (this node deleted these operations):**

| Former v3.0 op | Replacement |
|---|---|
| `detect` | [Azure AI Language detection](https://learn.microsoft.com/en-us/azure/ai-services/language-service/language-detection/how-to/call-api) |
| `dictionary_lookup` | Adaptive custom translation / application glossary |
| BreakSentence (never packaged) | Local NLP / delimiter |
| Dictionary examples (never packaged) | Adaptive custom translation |

## Operations

| operation | HTTP | Path | When to use |
|---|---|---|---|
| `translate` | POST | `/translate?api-version=2026-06-06` | Body `inputs` required |
| `transliterate` | POST | `/transliterate?api-version=2026-06-06` | Query `language`, `fromScript`, `toScript`; body `inputs` |

## Ports

| Port | Direction | Type | Notes |
|---|---|---|---|
| `input` | INPUT | object | Per-operation fields. No secrets. |
| `output` | OUTPUT | object | `{status, http_status, data}`. Secrets redacted. |

FUNCTION ports: none.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | translate | no | Official Translator 2026-06-06 verb. EXACT: translate / transliterate. Default translate = POST /translate?api-version=2026-06-06. detect and dictionary_lookup were removed in t... |
| `subscription_key` | password | yes |  | yes | Azure Translator / Foundry key. Sent as Ocp-Apim-Subscription-Key. StudioQuack secret only. Never logged. Empty → error 'subscription_key is required'. |
| `region` | string | no |  | no | Resource region for multi-service / regional keys (e.g. westeurope). Sent as Ocp-Apim-Subscription-Region when set; omitted when blank. Global Translator resources can leave thi... |
| `inputs` | object | no |  | no | Official Translator JSON array. translate: [{"text":"Hello","targets":["es"]}]. transliterate: [{"text":"こんにちは","language":"ja","fromScript":"Jpan","toScript":"Latn"}]. |
| `toScript` | string | no |  | no | transliterate target script. Example: Latn. |
| `language` | string | no |  | no | transliterate query language (ISO). Example: ja. |
| `fromScript` | string | no |  | no | transliterate source script. Example: Jpan. |
| `timeout_seconds` | number | no | 25 | no | HTTP timeout. Default 25. Floor 1. |

## Legal

- [Azure legal](https://azure.microsoft.com/support/legal/)
- [Product Terms](https://www.microsoft.com/licensing/terms)
- Operator is the Azure subscriber. Translated text leaves the daemon toward Microsoft. DPIA if the workflow handles identifiable content.

## Implementation notes

- JDK HTTP via `OfficialApiNode`. Pass official `inputs` array on INPUT.
- HTTP 4xx/5xx → `{error}` on OUTPUT. Never log `subscription_key`.
