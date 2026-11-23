# AWS IAM

StudioQuack builtin (`templateId` from `node_definition.json` `_id`, override `SQ_TEMPLATE_ID_AWS_IAM`). Handler: `OfficialApiNode` + `api_spec.json`. Calls the official [AWS IAM Query API 2010-05-08](https://docs.aws.amazon.com/IAM/latest/APIReference/Welcome.html) at `https://iam.amazonaws.com`.

IAM is a global service. SigV4 **signing region** for the commercial partition is `us-east-1` even though the host is `iam.amazonaws.com` ([programming with the Query API](https://docs.aws.amazon.com/IAM/latest/UserGuide/programming.html), [CreateUser](https://docs.aws.amazon.com/IAM/latest/APIReference/API_CreateUser.html)).

Inbound provider webhooks are **not** handled by this node. `listen` is `kind: ingress`.

## Ports

| Port | Direction | Type | Required | Schema / flow |
|---|---|---|---|---|
| `input` | INPUT | object | yes | Per-operation fields (`UserName`, `GroupName`, …). Never put credentials here. |
| `output` | OUTPUT | object | yes | Success: `status=ok`, `http_status`, `data`. Failure: `{error}` only. |

FUNCTION ports: none.

## Operations

Each operation POSTs `application/x-www-form-urlencoded` (`bodyFormat: form`) with `Action` and `Version=2010-05-08` on the query string.

| operation | HTTP | Query Action |
|---|---|---|
| `create-user` | POST `/?Action=CreateUser&Version=2010-05-08` | CreateUser |
| `get-user` | POST `/?Action=GetUser&Version=2010-05-08` | GetUser |
| `list-users` | POST `/?Action=ListUsers&Version=2010-05-08` | ListUsers |
| `update-user` | POST `/?Action=UpdateUser&Version=2010-05-08` | UpdateUser |
| `delete-user` | POST `/?Action=DeleteUser&Version=2010-05-08` | DeleteUser |
| `add-user-to-group` | POST `/?Action=AddUserToGroup&Version=2010-05-08` | AddUserToGroup |
| `remove-user-from-group` | POST `/?Action=RemoveUserFromGroup&Version=2010-05-08` | RemoveUserFromGroup |
| `create-group` | POST `/?Action=CreateGroup&Version=2010-05-08` | CreateGroup |
| `get-group` | POST `/?Action=GetGroup&Version=2010-05-08` | GetGroup |
| `list-groups` | POST `/?Action=ListGroups&Version=2010-05-08` | ListGroups |
| `update-group` | POST `/?Action=UpdateGroup&Version=2010-05-08` | UpdateGroup |
| `delete-group` | POST `/?Action=DeleteGroup&Version=2010-05-08` | DeleteGroup |
| `listen` | ingress | — |

## Configuration parameters

| Name | Type | Required | Secret | Purpose |
|---|---|---|---|---|
| `operation` | select | yes | no | Chooses the row above |
| `access_key_id` | password | yes | yes | IAM access key ID (SigV4 credential) |
| `secret_access_key` | password | yes | yes | IAM secret access key (SigV4 signing key) |
| `session_token` | password | no | yes | Optional STS session token (`x-amz-security-token`) |
| `region` | string | yes | no | SigV4 signing region. Commercial IAM: `us-east-1`. |
| `timeout_seconds` | number | no | no | Default 25 |

## Security & legal

- Secrets only from password `configurationParameters`. Never logged, never returned.
- [IAM API](https://docs.aws.amazon.com/IAM/latest/APIReference/Welcome.html) · [Query API](https://docs.aws.amazon.com/IAM/latest/UserGuide/programming.html). Terms: https://aws.amazon.com/agreement/.
- Official API only. No HTML scrape.
