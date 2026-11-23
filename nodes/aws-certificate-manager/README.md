# AWS Certificate Manager

StudioQuack builtin (`templateId` from `node_definition.json` `_id`, override `SQ_TEMPLATE_ID_AWS_CERTIFICATE_MANAGER`). Handler: `OfficialApiNode` + `api_spec.json`. Calls the official [AWS Certificate Manager API 2015-12-08](https://docs.aws.amazon.com/acm/latest/APIReference/Welcome.html) at `https://acm.{region}.amazonaws.com/`.

Inbound provider webhooks are **not** handled by this node. `listen` is `kind: ingress`: use a BPMN message start, intermediate catch, or boundary message event.

## Ports

| Port | Direction | Type | Required | Schema / flow |
|---|---|---|---|---|
| `input` | INPUT | object | yes | Per-operation fields. Never put `access_key_id` / `secret_access_key` / `session_token` here. |
| `output` | OUTPUT | object | yes | Success: `status=ok`, `http_status`, `data`. Failure: `{error}` only. |

FUNCTION ports: none. `jobType` is `custom-script-executor`. Outbound HTTPS only.

## Operations

| operation | HTTP | X-Amz-Target |
|---|---|---|
| `get-certificate` | POST `/` | `CertificateManager.GetCertificate` |
| `list-certificates` | POST `/` | `CertificateManager.ListCertificates` |
| `get-metadata` | POST `/` | `CertificateManager.DescribeCertificate` |
| `renew-certificate` | POST `/` | `CertificateManager.RenewCertificate` |
| `delete-certificate` | POST `/` | `CertificateManager.DeleteCertificate` |
| `listen` | ingress | — |

Sample request (AWS docs, 2026-08-22): [GetCertificate](https://docs.aws.amazon.com/acm/latest/APIReference/API_GetCertificate.html) uses `X-Amz-Target: CertificateManager.GetCertificate`, `Content-Type: application/x-amz-json-1.1`, SigV4 service `acm`.

## Configuration parameters

| Name | Type | Required | Secret | Purpose |
|---|---|---|---|---|
| `operation` | select | yes | no | Chooses the row above |
| `access_key_id` | password | yes | yes | IAM access key ID (SigV4 credential) |
| `secret_access_key` | password | yes | yes | IAM secret access key (SigV4 signing key) |
| `session_token` | password | no | yes | Optional STS session token (`x-amz-security-token`) |
| `region` | string | yes | no | ACM region for host and signing. Certificates are regional. |
| `timeout_seconds` | number | no | no | Default 25 |

## Secret values

GetCertificate returns PEM certificate and chain only — never a private key. Private keys never leave ACM. Bind OUTPUT `data` into a secret; do not print it, persist it in engine variables, or leave it on a logged edge.

## Security & legal

- Secrets only from password `configurationParameters`. Never logged, never returned on purpose.
- [AWS Certificate Manager API 2015-12-08](https://docs.aws.amazon.com/acm/latest/APIReference/Welcome.html). Terms: https://aws.amazon.com/agreement/.
- No RequestCertificate, ImportCertificate, ExportCertificate, or RevokeCertificate in this package.
- Official API only. No HTML scrape.
