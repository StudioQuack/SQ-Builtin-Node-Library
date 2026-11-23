# AWS Transcribe

Official Amazon Transcribe batch-job node (`templateId` from `node_definition.json` `_id`, override `SQ_TEMPLATE_ID_AWS_TRANSCRIBE`). Dispatched by `OfficialApiNode` from `api_spec.json`. No custom Java handler.

## Official API

- **Name / version:** Amazon Transcribe Service JSON protocol (API 2017-10-26). Verified 2026-08-22.
- **Host:** `POST https://transcribe.{region}.amazonaws.com/` (path `/`)
- **Headers:** `Content-Type: application/x-amz-json-1.1`, `X-Amz-Target: Transcribe.{Action}` (per-op `extraHeaders`). OfficialApiNode signs AWS SigV4 (service `transcribe`). Optional STS `session_token` becomes `x-amz-security-token`.
- **Docs:** [StartTranscriptionJob](https://docs.aws.amazon.com/transcribe/latest/APIReference/API_StartTranscriptionJob.html) · [GetTranscriptionJob](https://docs.aws.amazon.com/transcribe/latest/APIReference/API_GetTranscriptionJob.html) · [ListTranscriptionJobs](https://docs.aws.amazon.com/transcribe/latest/APIReference/API_ListTranscriptionJobs.html) · [DeleteTranscriptionJob](https://docs.aws.amazon.com/transcribe/latest/APIReference/API_DeleteTranscriptionJob.html) · [HTTP / WebSocket](https://docs.aws.amazon.com/transcribe/latest/dg/getting-started-http-websocket.html) · [Streaming setup](https://docs.aws.amazon.com/transcribe/latest/dg/streaming-setting-up.html)

The getting-started HTTP page still shows `POST /transcribe` and a longer `com.amazonaws.transcribe.Transcribe.{Operation}` target. This node follows the Smithy/JSON protocol used by the AWS SDKs: `POST /` with `X-Amz-Target: Transcribe.{Action}`.

Media must already be in Amazon S3 (`Media.MediaFileUri`). This node does not upload files to S3.

Streaming (`StartStreamTranscription` HTTP/2 or WebSocket) is operation `listen` with `kind: ingress`. Use a BPMN message event; this node does not open a socket.

## Operations

| operation | HTTP | Path | X-Amz-Target |
|---|---|---|---|
| `start_job` | POST | `/` | `Transcribe.StartTranscriptionJob` |
| `get_job` | POST | `/` | `Transcribe.GetTranscriptionJob` |
| `list_jobs` | POST | `/` | `Transcribe.ListTranscriptionJobs` |
| `delete_job` | POST | `/` | `Transcribe.DeleteTranscriptionJob` |
| `listen` | — | ingress | Streaming transcription — not opened by this node |

## Ports

| Port | Direction | Type | Notes |
|---|---|---|---|
| `input` | INPUT | object | Job name, `Media`, filters. S3 URI only. No secrets. |
| `output` | OUTPUT | object | `{status, http_status, data}`. Secrets redacted. |

FUNCTION ports: none.

## Configuration parameters

| Name | Type | Secret | Purpose |
|---|---|---|---|
| `operation` | select | no | Verb above. Default `start_job`. |
| `access_key_id` | password | **yes** | IAM access key ID (SigV4 credential) |
| `secret_access_key` | password | **yes** | IAM secret access key (SigV4 signing key) |
| `session_token` | password | **yes** | Optional STS session token (`x-amz-security-token`) |
| `region` | string | no | Host and signing region (default `us-east-1`) |
| `timeout_seconds` | number | no | Default `30`. Floor 1. |

## Legal

- [AWS Customer Agreement](https://aws.amazon.com/agreement/)
- [AWS Service Terms](https://aws.amazon.com/service-terms/)
- Operator is the AWS account holder. Audio in S3 and transcripts may contain personal data. IAM least-privilege on `transcribe:*` and the media bucket. HIPAA only with an AWS BAA on the account.

## Implementation notes

- JDK HTTP via `OfficialApiNode`. Auth type `aws-sigv4`.
- HTTP 4xx/5xx → `{error}` on OUTPUT. Never log AWS keys or session tokens.
