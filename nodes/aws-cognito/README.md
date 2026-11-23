# AWS Cognito

StudioQuack builtin (`templateId` from `node_definition.json` `_id`, override `SQ_TEMPLATE_ID_AWS_COGNITO`). Handler: `OfficialApiNode` + `api_spec.json`. Calls the official [Amazon Cognito Identity Provider API 2016-04-18](https://docs.aws.amazon.com/cognito-user-identity-pools/latest/APIReference/Welcome.html) at `https://cognito-idp.{region}.amazonaws.com/`.

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
| `create-user` | POST `/` | `AWSCognitoIdentityProviderService.AdminCreateUser` |
| `get-user` | POST `/` | `AWSCognitoIdentityProviderService.AdminGetUser` |
| `list-users` | POST `/` | `AWSCognitoIdentityProviderService.ListUsers` |
| `update-user` | POST `/` | `AWSCognitoIdentityProviderService.AdminUpdateUserAttributes` |
| `delete-user` | POST `/` | `AWSCognitoIdentityProviderService.AdminDeleteUser` |
| `add-user-to-group` | POST `/` | `AWSCognitoIdentityProviderService.AdminAddUserToGroup` |
| `remove-user-from-group` | POST `/` | `AWSCognitoIdentityProviderService.AdminRemoveUserFromGroup` |
| `create-group` | POST `/` | `AWSCognitoIdentityProviderService.CreateGroup` |
| `get-group` | POST `/` | `AWSCognitoIdentityProviderService.GetGroup` |
| `list-groups` | POST `/` | `AWSCognitoIdentityProviderService.ListGroups` |
| `update-group` | POST `/` | `AWSCognitoIdentityProviderService.UpdateGroup` |
| `delete-group` | POST `/` | `AWSCognitoIdentityProviderService.DeleteGroup` |
| `get-user-pool` | POST `/` | `AWSCognitoIdentityProviderService.DescribeUserPool` |
| `listen` | ingress | — |

SigV4 service name is `cognito-idp`. Sample: [AdminCreateUser](https://docs.aws.amazon.com/cognito-user-identity-pools/latest/APIReference/API_AdminCreateUser.html). User-pool admin APIs only — no InitiateAuth, SignUp, AdminSetUserPassword, or pool CRUD.

Official Admin* APIs are `POST /` with `UserPoolId` in the JSON body ([AdminCreateUser](https://docs.aws.amazon.com/cognito-user-identity-pools/latest/APIReference/API_AdminCreateUser.html)), not a `/user-pools/{id}` path. Blank INPUT `UserPoolId` falls back to config `UserPoolId`.

## Configuration parameters

| Name | Type | Required | Secret | Purpose |
|---|---|---|---|---|
| `operation` | select | yes | no | Chooses the row above |
| `access_key_id` | password | yes | yes | IAM access key ID (SigV4 credential) |
| `secret_access_key` | password | yes | yes | IAM secret access key (SigV4 signing key) |
| `session_token` | password | no | yes | Optional STS session token (`x-amz-security-token`) |
| `region` | string | yes | no | User-pool region for host and signing |
| `UserPoolId` | string | yes | no | Default JSON-body `UserPoolId` (official Admin* field; blank INPUT falls back here) |
| `timeout_seconds` | number | no | no | Default 25 |

## Security & legal

- Secrets only from password `configurationParameters`. Never logged, never returned on purpose.
- [Cognito Identity Provider API](https://docs.aws.amazon.com/cognito-user-identity-pools/latest/APIReference/Welcome.html). Terms: https://aws.amazon.com/agreement/.
- TemporaryPassword and user attributes are personal data / credentials; never log them.
- Official API only. No HTML scrape.
