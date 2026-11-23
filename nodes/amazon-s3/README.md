# Amazon S3

- **Status:** OfficialApiNode package with AWS SigV4
- **Folder:** `nodes/amazon-s3/`
- **Official API:** https://docs.aws.amazon.com/AmazonS3/latest/API/Welcome.html
- **Virtual hosting / path-style:** https://docs.aws.amazon.com/AmazonS3/latest/userguide/VirtualHosting.html
- **Auth:** AWS SigV4 (service `s3`) using `access_key_id` + `secret_access_key`. Optional `session_token` becomes `x-amz-security-token`.
- **Verified:** 2026-08-22

This node uses **path-style** URLs:

```
https://s3.{region}.amazonaws.com/{bucket}/{key}
```

Virtual-hosted style (`https://{bucket}.s3.{region}.amazonaws.com/{key}`) is the AWS-recommended form for new work and is required for some Regions launched after 2019 if the client used the legacy global endpoint. Path-style remains supported in all Regions (deprecation delayed; see the Virtual Hosting guide). Buckets whose names contain dots are easier to address with path-style over TLS.

Upload, download, convert, and sign payloads use type `file` on **ports and configuration parameters** (platform file ref-id). Never inline bytes or base64 as the stored type.

## Operations

| operation | HTTP | Path |
|---|---|---|
| `bucket_create` | PUT | `/{bucket}` |
| `bucket_delete` | DELETE | `/{bucket}` |
| `bucket_list` | GET | `/` |
| `bucket_search` | GET | `/{bucket}` |
| `file_copy` | PUT | `/{bucket}/{key}` (`x-amz-copy-source: {from_path}`) |
| `file_delete` | DELETE | `/{bucket}/{key}` |
| `file_download` | GET | `/{bucket}/{key}` |
| `file_list` | GET | `/{bucket}` |
| `file_upload` | PUT | `/{bucket}/{key}` (`bodyFormat: raw`, `bodyParam: file`) |
| `folder_create` | PUT | `/{bucket}/{key}` |
| `folder_delete` | DELETE | `/{bucket}/{key}` |
| `folder_list` | GET | `/{bucket}` |
| `listen` | ingress | BPMN message event — does not open a socket |

## Ports

| Port | Direction | Notes |
|---|---|---|
| `input` | INPUT | Per-operation fields. `file` is type file. No secrets. |
| `output` | OUTPUT | `{ status, http_status, data, file? }`. |

FUNCTION ports: none.

## Configuration

| Name | Type | Required | Secret | Purpose |
|---|---|---|---|---|
| `operation` | select | yes | no | Verb above |
| `access_key_id` | password | yes | yes | IAM access key ID (SigV4 credential) |
| `secret_access_key` | password | yes | yes | IAM secret access key (SigV4 signing key) |
| `session_token` | password | no | yes | Optional STS session token (`x-amz-security-token`) |
| `region` | string | yes | no | Path-style host `s3.{region}.amazonaws.com` and signing region |
| `file` | file | no | no | Default upload source |
| `timeout_seconds` | number | no | no | Default 60 |

## Legal

- Terms: https://aws.amazon.com/service-terms/
- Official-API-first. No scrape. No unofficial web wrapper.
- REST version 2006-03-01. Folders are prefix+delimiter conventions. listen maps to PutBucketNotificationConfiguration plus BPMN message events — S3 cannot long-poll. IAM least-privilege; customer owns bucket policy.
- Logs: shapes, ids, status codes, sizes — never file bytes or secrets.

## Implementation notes

- Engine: OfficialApiNode (JDK HTTP). No vendor SDK.
- `listen` / ingress: use a BPMN message start or catch event.
- Failures map to OUTPUT `{ error }`. Never throw past dispatch.
- `file_upload` sends `file` as raw body bytes. Platform file refs that are still string ids (not resolved bytes) will not be valid object content — prefer a resolved file payload or a dedicated upload path.
