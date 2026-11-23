# AWS Secrets Manager

StudioQuack builtin (`templateId` from `node_definition.json` `_id`, override `SQ_TEMPLATE_ID_AWS_SECRETS_MANAGER`). Handler: `OfficialApiNode` + `api_spec.json`. Calls the official [AWS Secrets Manager API 2017-10-17](https://docs.aws.amazon.com/secretsmanager/latest/apireference/Welcome.html) at `https://secretsmanager.{region}.amazonaws.com/`.

Inbound provider webhooks are **not** handled by this node. `listen` is `kind: ingress`.

## Ports

| Port | Direction | Type | Required | Schema / flow |
|---|---|---|---|---|
| `input` | INPUT | object | yes | Per-operation fields. Never put credentials here. |
| `output` | OUTPUT | object | yes | Success: `status=ok`, `http_status`, `data`. Failure: `{error}` only. |

FUNCTION ports: none.

## Operations

| operation | HTTP | X-Amz-Target |
|---|---|---|
| `get-secret` | POST `/` | `secretsmanager.GetSecretValue` |
| `put-secret` | POST `/` | `secretsmanager.CreateSecret` |
| `update-secret` | POST `/` | `secretsmanager.UpdateSecret` |
| `delete-secret` | POST `/` | `secretsmanager.DeleteSecret` |
| `list-secrets` | POST `/` | `secretsmanager.ListSecrets` |
| `rotate-secret` | POST `/` | `secretsmanager.RotateSecret` |
| `describe-secret` | POST `/` | `secretsmanager.DescribeSecret` |
| `listen` | ingress | — |

SigV4 service name is `secretsmanager`. Sample: [GetSecretValue](https://docs.aws.amazon.com/secretsmanager/latest/apireference/API_GetSecretValue.html) (`X-Amz-Target: secretsmanager.GetSecretValue`, `Content-Type: application/x-amz-json-1.1`).

No BatchGetSecretValue, GetRandomPassword, or RestoreSecret in this package.

## Configuration parameters

| Name | Type | Required | Secret | Purpose |
|---|---|---|---|---|
| `operation` | select | yes | no | Chooses the row above |
| `access_key_id` | password | yes | yes | IAM access key ID (SigV4 credential) |
| `secret_access_key` | password | yes | yes | IAM secret access key (SigV4 signing key) |
| `session_token` | password | no | yes | Optional STS session token (`x-amz-security-token`) |
| `region` | string | yes | no | Host and signing region |
| `timeout_seconds` | number | no | no | Default 25 |

## Secret values

`get-secret` still POSTs GetSecretValue. Retrieved `SecretString` / `SecretBinary` must not be logged and should stay in StudioQuack secret bindings (bind OUTPUT `data` into a secret; do not print it, persist it in engine variables, or leave it on a logged edge).

## Security & legal

- Secrets only from password `configurationParameters`. Never logged, never returned on purpose.
- [Secrets Manager API](https://docs.aws.amazon.com/secretsmanager/latest/apireference/Welcome.html). Terms: https://aws.amazon.com/agreement/.
- Official API only. No HTML scrape.
