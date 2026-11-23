# AWS Comprehend

- **Status:** planned
- **Catalog:** `docs/catalog/ai-ml.md`
- **Folder when built:** `nodes/aws-comprehend/`
- **Researched:** 2026-08-20
- **Official API:** Amazon Comprehend API version **2017-11-27** (JSON 1.1)
- **Docs:** https://docs.aws.amazon.com/comprehend/latest/APIReference/Welcome.html · sync real-time: https://docs.aws.amazon.com/comprehend/latest/dg/using-api-sync.html
- **Auth:** AWS access key + secret — secret bindings: `access_key_id`, `secret_access_key`; optional `session_token`. Config: `region`.

## Scope

One node for Amazon Comprehend **real-time (synchronous) NLP**. `operation` selects DetectSentiment, DetectEntities, DetectKeyPhrases, DetectDominantLanguage, DetectSyntax, or DetectPiiEntities. Catalog verb `detect_language` maps to **DetectDominantLanguage**. Syntax and PII detect are current official actions as of 2026-08-20 and are included.

Do not train custom classifiers or entity recognizers. Do not call Comprehend Medical (separate service). Do not scrape the AWS console. JDK HTTP + SigV4 only — **no AWS SDK**.

## Wire protocol

- URL: `POST https://comprehend.{region}.amazonaws.com/` (path `/`)
- `Content-Type: application/x-amz-json-1.1`
- `X-Amz-Target: Comprehend_20171127.{Action}` (see table)
- Body: compact JSON
- Auth: [Signature Version 4](https://docs.aws.amazon.com/IAM/latest/UserGuide/reference_aws-signing.html) — signing service **`comprehend`**
- Optional STS: header `X-Amz-Security-Token` when `session_token` is bound

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `detect_sentiment` | POST | `X-Amz-Target: Comprehend_20171127.DetectSentiment` | Overall POSITIVE / NEGATIVE / NEUTRAL / MIXED |
| `detect_entities` | POST | `…DetectEntities` | Pretrained named entities (PERSON, LOCATION, …) |
| `detect_key_phrases` | POST | `…DetectKeyPhrases` | Noun phrases |
| `detect_language` | POST | `…DetectDominantLanguage` | RFC 5646 language codes + scores (catalog name) |
| `detect_syntax` | POST | `…DetectSyntax` | Tokens + part-of-speech tags |
| `detect_pii` | POST | `…DetectPiiEntities` | Locate PII spans (en/es). Offsets only — handler does not return raw PII text |

**Dropped (current API exists, out of catalog intent):** `BatchDetect*` (25-doc batches), async `Start*` jobs (topic modeling, document classification, events, PII redaction jobs), custom model `EndpointArn`, `ContainsPiiEntities`, `DetectTargetedSentiment`, Prompt Safety Classification (restricted for new customers as of 2026-04-30). Do not list as “later”.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `detect_sentiment` | no | Row above |
| `access_key_id` | password | yes | | **yes** | IAM access key. Never on INPUT. Never logged. |
| `secret_access_key` | password | yes | | **yes** | IAM secret. SigV4 only. |
| `session_token` | password | no | empty | **yes** | STS session token when using temporary creds |
| `region` | string | yes | | no | AWS region (`us-east-1`, `eu-west-1`, …). Host = `comprehend.{region}.amazonaws.com` |
| `default_language_code` | select | no | `en` | no | Default `LanguageCode` when input omits it (not used by `detect_language`) |
| `timeout_seconds` | number | no | `25` | no | Connect + read timeout. Floor 1. |

IAM least privilege: `comprehend:DetectSentiment`, `DetectEntities`, `DetectKeyPhrases`, `DetectDominantLanguage`, `DetectSyntax`, `DetectPiiEntities` only. No account sharing.

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | No AWS keys |
| `output` | OUTPUT | object — `{ status, … }` or `{ error }` | Failures never include keys or session tokens |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = detect_sentiment` | `detect_entities` | `detect_key_phrases`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `text` | string | yes | UTF-8 body. Trim. Empty → `{ error }` no HTTP. Sentiment / key phrases: **max 5 KB**. Entities: **max 100 KB**. |
| `language_code` | string | yes if config blank | `en` \| `es` \| `fr` \| `de` \| `it` \| `pt` \| `ar` \| `hi` \| `ja` \| `ko` \| `zh` \| `zh-TW`. Else config `default_language_code`. |

### `operation = detect_syntax`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `text` | string | yes | UTF-8, **max 5 KB** |
| `language_code` | string | yes if config blank | Syntax languages only: `en` \| `es` \| `fr` \| `de` \| `it` \| `pt` |

### `operation = detect_language`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `text` | string | yes | UTF-8, **max 100 KB**. AWS recommends ≥ 20 characters for a reliable dominant language. |

### `operation = detect_pii`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `text` | string | yes | UTF-8, **max 100 KB** |
| `language_code` | string | yes if config blank | PII real-time: **`en` or `es`** only (docs). Other codes → `{ error }` no HTTP. |

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `sentiment` | string | `detect_sentiment` | `POSITIVE` \| `NEGATIVE` \| `NEUTRAL` \| `MIXED` |
| `sentiment_score` | object | `detect_sentiment` | `{ positive, negative, neutral, mixed }` from `SentimentScore` |
| `entities` | array | `detect_entities` | `{ text, type, score, begin_offset, end_offset }` |
| `key_phrases` | array | `detect_key_phrases` | `{ text, score, begin_offset, end_offset }` |
| `languages` | array | `detect_language` | `{ language_code, score }` sorted dominant-first |
| `syntax_tokens` | array | `detect_syntax` | `{ token_id, text, begin_offset, end_offset, part_of_speech: { tag, score } }` |
| `pii_entities` | array | `detect_pii` | `{ type, score, begin_offset, end_offset }` — **omit entity `text`**. Types include NAME, ADDRESS, SSN, CREDIT_DEBIT_NUMBER, EMAIL, PHONE, PASSPORT_NUMBER, … (see PII guide) |
| `error` | string | failure | Actionable; keys redacted to `***` |
| `http_status` | number | HTTP error | AWS status |
| `retry_after_seconds` | number | 429 / 5xx / timeout | 60 on throttle; 30 on 5xx/timeout |

## Legal

- API: https://docs.aws.amazon.com/comprehend/latest/APIReference/Welcome.html
- DetectSentiment: https://docs.aws.amazon.com/comprehend/latest/APIReference/API_DetectSentiment.html
- DetectEntities: https://docs.aws.amazon.com/comprehend/latest/APIReference/API_DetectEntities.html
- DetectKeyPhrases: https://docs.aws.amazon.com/comprehend/latest/APIReference/API_DetectKeyPhrases.html
- DetectDominantLanguage: https://docs.aws.amazon.com/comprehend/latest/APIReference/API_DetectDominantLanguage.html
- DetectSyntax: https://docs.aws.amazon.com/comprehend/latest/APIReference/API_DetectSyntax.html
- DetectPiiEntities: https://docs.aws.amazon.com/comprehend/latest/APIReference/API_DetectPiiEntities.html
- PII types: https://docs.aws.amazon.com/comprehend/latest/dg/how-pii.html
- Pricing (100-character units, 3-unit minimum): https://aws.amazon.com/comprehend/pricing/
- [AWS Customer Agreement](https://aws.amazon.com/agreement/) and [AWS Service Terms](https://aws.amazon.com/service-terms/) §50 (AI Services). Comprehend is an **AI Service**: AWS may use some AI Content to improve the service unless a documented exception applies. **Detect PII is listed as excluded** from that improvement clause — still treat all input as leaving the customer host.
- Text may be personal data. Customer is controller; AWS is processor per the customer’s AWS account. Minimize; do not send government IDs / health data unless the customer’s BAA / DPA covers that account.
- Logs: operation, region, status, sizes, entity **counts** — never `text`, never PII values, never keys.

## Implementation notes

- JDK `HttpClient` + JDK `Mac` HMAC-SHA256 SigV4. TLS verified. `Redirect.NEVER`.
- Canonical request signs `host`, `content-type`, `x-amz-date`, `x-amz-target`, `x-amz-content-sha256` (hex SHA-256 of the exact body). Temporary creds: also sign `x-amz-security-token`.
- Empty keys → `{ error: "Missing AWS credentials. Bind access_key_id and secret_access_key." }` — no HTTP.
- Cap request UTF-8 bytes per operation (5_120 for 5 KB ops; 102_400 for 100 KB). Oversize → `{ error }` no HTTP.
- `detect_pii`: map AWS `Entities` to offsets + type + score only. Do not copy the source substring onto OUTPUT.
- AWS errors: JSON `__type` / `Message`. Map `TextSizeLimitExceededException` / `UnsupportedLanguageException` / `InvalidRequestException` → 4xx `{ error }`. `ThrottlingException` / `ProvisionedThroughputExceededException` → `retry_after_seconds: 60`. Do not auto-retry inside the handler.
- Response body cap **1_000_000** bytes.
- Redact `access_key_id`, `secret_access_key`, `session_token`, `Authorization`, `X-Amz-Security-Token` to `***`.
