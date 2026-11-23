# AWS Rekognition

Official Amazon Rekognition Image API (`templateId` from `node_definition.json` `_id`, override `SQ_TEMPLATE_ID_AWS_REKOGNITION`). Dispatched by `OfficialApiNode` from `api_spec.json`. No custom Java handler.

## Official API

- **Name:** Amazon Rekognition Image (JSON 1.1). Verified 2026-08-22.
- **Host:** `POST https://rekognition.{region}.amazonaws.com/` (path `/`)
- **Headers:** `Content-Type: application/x-amz-json-1.1`, `X-Amz-Target: RekognitionService.{Action}` (per-op `extraHeaders`). OfficialApiNode signs AWS SigV4 (service `rekognition`). Optional STS `session_token` becomes `x-amz-security-token`.
- **Docs:** [Welcome](https://docs.aws.amazon.com/rekognition/latest/APIReference/Welcome.html) · [DetectLabels](https://docs.aws.amazon.com/rekognition/latest/APIReference/API_DetectLabels.html) · [DetectFaces](https://docs.aws.amazon.com/rekognition/latest/APIReference/API_DetectFaces.html) · [DetectText](https://docs.aws.amazon.com/rekognition/latest/APIReference/API_DetectText.html) · [CompareFaces](https://docs.aws.amazon.com/rekognition/latest/APIReference/API_CompareFaces.html)

## Scope

Sync image ops only: labels, faces, text, compare faces. No collections, no video, no Face Liveness, no celebrity search.

Official JSON is `Image` / `SourceImage` / `TargetImage` with `Bytes` (base64) or `S3Object`. Prefer `S3Object`.

## Operations

| operation | HTTP | X-Amz-Target |
|---|---|---|
| `detect_labels` | POST `https://rekognition.{region}.amazonaws.com/` | `RekognitionService.DetectLabels` |
| `detect_faces` | POST same | `RekognitionService.DetectFaces` |
| `detect_text` | POST same | `RekognitionService.DetectText` |
| `compare_faces` | POST same | `RekognitionService.CompareFaces` |

## Ports

`input` / `output` only. Image fields: official `Image` / `SourceImage` / `TargetImage` objects.

## Configuration

| Name | Type | Secret | Purpose |
|---|---|---|---|
| `operation` | select | no | Default `detect_labels` |
| `access_key_id` | password | **yes** | IAM access key ID (SigV4 credential) |
| `secret_access_key` | password | **yes** | IAM secret access key (SigV4 signing key) |
| `session_token` | password | **yes** | Optional STS session token (`x-amz-security-token`) |
| `region` | string | no | Host and signing region |
| `timeout_seconds` | number | no | Default 60 |

IAM: `rekognition:DetectLabels`, `DetectFaces`, `DetectText`, `CompareFaces` only.

## Legal

- [AWS Customer Agreement](https://aws.amazon.com/agreement/) · [AWS Service Terms](https://aws.amazon.com/service-terms/) including AI Services and Rekognition terms
- **Biometric / face data:** `detect_faces` and `compare_faces` process facial geometry. The customer is the controller. Complete a **DPIA** (GDPR Art. 35 / equivalent) before production. Lawful basis and notice are required where the law demands them.
- **No covert surveillance.** Do not use this node to identify people who have not been told, to track individuals in public without a legal basis, or to build a covert watchlist. StudioQuack does not operate the Rekognition account; the customer’s AWS Acceptable Use Policy still applies.
- Face attributes (age, gender, emotion) are inferences, not identity proof, and must not be used as the sole basis for adverse decisions.
- Images leave the customer host to AWS. PHI/biometric special-category data needs a covering BAA/DPA on that AWS account.
- Logs: operation, region, status, sizes — never image bytes, never face embeddings, never keys.
