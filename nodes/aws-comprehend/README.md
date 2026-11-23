# AWS Comprehend

Official Amazon Comprehend real-time NLP (`templateId` from `node_definition.json` `_id`, override `SQ_TEMPLATE_ID_AWS_COMPREHEND`). Dispatched by `OfficialApiNode` from `api_spec.json`. No custom Java handler.

## Official API

- **Name / version:** Amazon Comprehend API **2017-11-27** (JSON 1.1). Verified 2026-08-22.
- **Host:** `POST https://comprehend.{region}.amazonaws.com/` (path `/`)
- **Headers:** `Content-Type: application/x-amz-json-1.1`, `X-Amz-Target: Comprehend_20171127.{Action}` (per-op `extraHeaders`). OfficialApiNode signs AWS SigV4 (service `comprehend`) and stamps `x-amz-date`, `x-amz-content-sha256`, `Authorization`. Optional STS `session_token` becomes `x-amz-security-token`.
- **Docs:** [Welcome](https://docs.aws.amazon.com/comprehend/latest/APIReference/Welcome.html) · [DetectSentiment](https://docs.aws.amazon.com/comprehend/latest/APIReference/API_DetectSentiment.html) · [DetectEntities](https://docs.aws.amazon.com/comprehend/latest/APIReference/API_DetectEntities.html) · [DetectKeyPhrases](https://docs.aws.amazon.com/comprehend/latest/APIReference/API_DetectKeyPhrases.html) · [DetectDominantLanguage](https://docs.aws.amazon.com/comprehend/latest/APIReference/API_DetectDominantLanguage.html) · [DetectSyntax](https://docs.aws.amazon.com/comprehend/latest/APIReference/API_DetectSyntax.html) · [DetectPiiEntities](https://docs.aws.amazon.com/comprehend/latest/APIReference/API_DetectPiiEntities.html) · [SigV4](https://docs.aws.amazon.com/IAM/latest/UserGuide/reference_aws-signing.html)

## Scope

Synchronous NLP only. Catalog `detect_language` maps to **DetectDominantLanguage**. No Comprehend Medical, no custom classifiers, no batch/async jobs.

## Operations

| operation | HTTP | Path | X-Amz-Target |
|---|---|---|---|
| `detect_sentiment` | POST | `/` | `Comprehend_20171127.DetectSentiment` |
| `detect_entities` | POST | `/` | `Comprehend_20171127.DetectEntities` |
| `detect_key_phrases` | POST | `/` | `Comprehend_20171127.DetectKeyPhrases` |
| `detect_language` | POST | `/` | `Comprehend_20171127.DetectDominantLanguage` |
| `detect_syntax` | POST | `/` | `Comprehend_20171127.DetectSyntax` |
| `detect_pii` | POST | `/` | `Comprehend_20171127.DetectPiiEntities` |

Body uses official JSON names `Text` and `LanguageCode`.

## Ports

`input` / `output` only. FUNCTION ports: none.

## Configuration

| Name | Type | Secret | Purpose |
|---|---|---|---|
| `operation` | select | no | Verb above. Default `detect_sentiment`. |
| `access_key_id` | password | **yes** | IAM access key ID (SigV4 credential) |
| `secret_access_key` | password | **yes** | IAM secret access key (SigV4 signing key) |
| `session_token` | password | **yes** | Optional STS session token (`x-amz-security-token`) |
| `region` | string | no | Host and signing region (`us-east-1`) |
| `timeout_seconds` | number | no | Default 25 |

IAM least privilege: `comprehend:DetectSentiment`, `DetectEntities`, `DetectKeyPhrases`, `DetectDominantLanguage`, `DetectSyntax`, `DetectPiiEntities` only.

## Legal

- [AWS Customer Agreement](https://aws.amazon.com/agreement/) · [AWS Service Terms](https://aws.amazon.com/service-terms/) §50 (AI Services)
- Text may be personal data. Customer is controller of their AWS account. Minimize. Do not send government IDs / health data unless that account’s BAA/DPA covers it.
- Logs: operation, region, status, sizes — never `Text`, never PII values, never keys.
