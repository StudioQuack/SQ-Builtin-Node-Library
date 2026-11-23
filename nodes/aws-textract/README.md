# AWS Textract

Official Amazon Textract API (`templateId` from `node_definition.json` `_id`, override `SQ_TEMPLATE_ID_AWS_TEXTRACT`). Dispatched by `OfficialApiNode` from `api_spec.json`. No custom Java handler.

## Official API

- **Name:** Amazon Textract (JSON 1.1). Docs last published 2026-08-20. Verified 2026-08-22.
- **Host:** `POST https://textract.{region}.amazonaws.com/` (path `/`)
- **Headers:** `Content-Type: application/x-amz-json-1.1`, `X-Amz-Target: Textract.{Action}` (per-op `extraHeaders`). OfficialApiNode signs AWS SigV4 (service `textract`). Optional STS `session_token` becomes `x-amz-security-token`.
- **Docs:** [Welcome](https://docs.aws.amazon.com/textract/latest/APIReference/Welcome.html) · [DetectDocumentText](https://docs.aws.amazon.com/textract/latest/APIReference/API_DetectDocumentText.html) · [AnalyzeDocument](https://docs.aws.amazon.com/textract/latest/APIReference/API_AnalyzeDocument.html) · [AnalyzeExpense](https://docs.aws.amazon.com/textract/latest/APIReference/API_AnalyzeExpense.html) · [AnalyzeID](https://docs.aws.amazon.com/textract/latest/APIReference/API_AnalyzeID.html)

## Scope

Synchronous ops only (catalog): `detect_text` = DetectDocumentText, plus AnalyzeDocument, AnalyzeExpense, AnalyzeID. No async `Start*` jobs in this package.

Official JSON is `Document` (`Bytes` or `S3Object`) or AnalyzeID `DocumentPages`. Prefer `S3Object` so OfficialApiNode does not need to inline bytes.

Sync limits (AWS): typically 1 page, 10 MB. JPEG, PNG, PDF, TIFF.

## Operations

| operation | HTTP | X-Amz-Target |
|---|---|---|
| `detect_text` | POST `https://textract.{region}.amazonaws.com/` | `Textract.DetectDocumentText` |
| `analyze_document` | POST same | `Textract.AnalyzeDocument` |
| `analyze_expense` | POST same | `Textract.AnalyzeExpense` |
| `analyze_id` | POST same | `Textract.AnalyzeID` |

## Ports

`input` / `output` only. Document fields: official `Document` / `DocumentPages`.

## Configuration

| Name | Type | Secret | Purpose |
|---|---|---|---|
| `operation` | select | no | Default `detect_text` |
| `access_key_id` | password | **yes** | IAM access key ID (SigV4 credential) |
| `secret_access_key` | password | **yes** | IAM secret access key (SigV4 signing key) |
| `session_token` | password | **yes** | Optional STS session token (`x-amz-security-token`) |
| `region` | string | no | Host and signing region |
| `timeout_seconds` | number | no | Default 60 |

IAM: `textract:DetectDocumentText`, `AnalyzeDocument`, `AnalyzeExpense`, `AnalyzeID` only.

## Legal

- [AWS Customer Agreement](https://aws.amazon.com/agreement/) · [AWS Service Terms](https://aws.amazon.com/service-terms/)
- **ID / PII — minimize retention.** `analyze_id` extracts identity-document fields (name, DOB, document numbers). Expense and form extraction often contain account numbers, addresses, and names. Treat OUTPUT as personal data. Do not copy ID images or extracted identity fields into long-lived stores, logs, or a second system.
- Customer is controller of the AWS account. Government IDs and health forms need a covering BAA/DPA.
- Logs: operation, region, status, sizes, page counts — never document bytes, never ID field values, never keys.
