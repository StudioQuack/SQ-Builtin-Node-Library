# AWS IAM

- **Status:** planned
- **Catalog:** `docs/catalog/security-identity.md`
- **Folder when built:** `nodes/aws-iam/`
- **Researched:** 2026-08-20
- **Official API:** AWS Identity and Access Management Query API version **2010-05-08**
- **Docs:** https://docs.aws.amazon.com/IAM/latest/APIReference/welcome.html
- **Auth:** `awsAccessKey` or `iamRole` — secret bindings: `accessKeyId`, `secretAccessKey`, `sessionToken` (optional), `externalId` (optional). Config: `region` (signing; commercial IAM is global — use `us-east-1`).

## Scope

Account IAM **users and groups** only: create/get/list/update/delete user and group, add/remove user from group. Calls the IAM Query API over HTTPS. Mutations are high risk (they change who can act in the AWS account). Restrict the caller’s IAM policy to the named users/groups this workflow needs.

**DO NOT BUILD (credential minting / login surface):** `CreateAccessKey`, `DeleteAccessKey`, `ListAccessKeys`, `CreateLoginProfile`, `UpdateLoginProfile`, `DeleteLoginProfile`, `CreateServiceSpecificCredential`, `ResetServiceSpecificCredential`, `ChangePassword`, `CreateVirtualMFADevice`, policy attach/detach, role APIs, SAML/OIDC provider APIs. Catalog does not list them; do not add them in v1.

IAM `UpdateUser` / `UpdateGroup` only rename or change **path** — they are not Cognito-style attribute updates.

## Wire protocol

IAM is a **Query API** ([Calling the IAM API using HTTP query requests](https://docs.aws.amazon.com/IAM/latest/UserGuide/programming.html)):

- Endpoint (commercial): `https://iam.amazonaws.com/` — all commercial Regions share this host. GovCloud: `https://iam.us-gov.amazonaws.com/`. FIPS: `https://iam-fips.amazonaws.com/` ([endpoints](https://docs.aws.amazon.com/general/latest/gr/iam-service.html)).
- Method: **POST** (GET is allowed but URL-size limited; this node always POSTs).
- `Content-Type: application/x-www-form-urlencoded; charset=utf-8`
- Body: `Action={ActionName}&Version=2010-05-08&{params}` plus SigV4 headers (not query `AUTHPARAMS` placeholders).
- Auth: [Signature Version 4](https://docs.aws.amazon.com/IAM/latest/UserGuide/reference_sigv-create-signed-request.html) — service `iam`, **signing region `us-east-1`** for commercial (catalog: global IAM, use `us-east-1`). Config `region` is the signing region; do not send IAM user/group calls to `iam.{region}.amazonaws.com` on commercial.
- Response: XML, namespace `https://iam.amazonaws.com/doc/2010-05-08/`. Parse to JSON for OUTPUT.
- HTTPS required.

JDK HTTP only. **Do not** pull an AWS SDK — Query form bodies and XML parse are straightforward.

`iamRole`: STS Query `AssumeRole` (`Action=AssumeRole&Version=2011-06-15`) to a **regional** STS endpoint `https://sts.{region}.amazonaws.com/` (preferred over global `sts.amazonaws.com`), SigV4 service `sts`. Use the temporary key/secret/token to sign IAM. STS default quota 600 RPS. Never put assumed credentials on OUTPUT.

List serialization: `Tags.member.1.Key`, `Tags.member.1.Value` (max 50 tags).

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `create-user` | POST | `Action=CreateUser&Version=2010-05-08` | Create an IAM user |
| `get-user` | POST | `Action=GetUser` | One user (omit `UserName` → caller identity) |
| `list-users` | POST | `Action=ListUsers` | Page users by `PathPrefix` |
| `update-user` | POST | `Action=UpdateUser` | Rename and/or change path |
| `delete-user` | POST | `Action=DeleteUser` | Delete user (must be empty of keys/policies/groups first) |
| `add-user-to-group` | POST | `Action=AddUserToGroup` | Add user to group |
| `remove-user-from-group` | POST | `Action=RemoveUserFromGroup` | Remove user from group |
| `create-group` | POST | `Action=CreateGroup` | Create a group |
| `get-group` | POST | `Action=GetGroup` | Group metadata **and** member users (paginated) |
| `list-groups` | POST | `Action=ListGroups` | Page groups by `PathPrefix` |
| `update-group` | POST | `Action=UpdateGroup` | Rename and/or change path |
| `delete-group` | POST | `Action=DeleteGroup` | Delete empty group |

Action docs: [CreateUser](https://docs.aws.amazon.com/IAM/latest/APIReference/API_CreateUser.html), [GetUser](https://docs.aws.amazon.com/IAM/latest/APIReference/API_GetUser.html), [ListUsers](https://docs.aws.amazon.com/IAM/latest/APIReference/API_ListUsers.html), [UpdateUser](https://docs.aws.amazon.com/IAM/latest/APIReference/API_UpdateUser.html), [DeleteUser](https://docs.aws.amazon.com/IAM/latest/APIReference/API_DeleteUser.html), [AddUserToGroup](https://docs.aws.amazon.com/IAM/latest/APIReference/API_AddUserToGroup.html), [RemoveUserFromGroup](https://docs.aws.amazon.com/IAM/latest/APIReference/API_RemoveUserFromGroup.html), [CreateGroup](https://docs.aws.amazon.com/IAM/latest/APIReference/API_CreateGroup.html), [GetGroup](https://docs.aws.amazon.com/IAM/latest/APIReference/API_GetGroup.html), [ListGroups](https://docs.aws.amazon.com/IAM/latest/APIReference/API_ListGroups.html), [UpdateGroup](https://docs.aws.amazon.com/IAM/latest/APIReference/API_UpdateGroup.html), [DeleteGroup](https://docs.aws.amazon.com/IAM/latest/APIReference/API_DeleteGroup.html).

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `create-user` | no | Row above |
| `auth` | select | yes | `awsAccessKey` | no | `awsAccessKey` or `iamRole` |
| `accessKeyId` | password | if `awsAccessKey` | | yes | Access key id |
| `secretAccessKey` | password | if `awsAccessKey` | | yes | Secret access key |
| `sessionToken` | password | no | | yes | STS session token |
| `roleArn` | string | if `iamRole` | | no | Role to assume |
| `roleSessionName` | string | no | `studioquack-iam` | no | STS session name |
| `externalId` | password | no | | yes | STS ExternalId |
| `region` | string | yes | `us-east-1` | no | SigV4 region for IAM (`us-east-1` commercial) |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | No credentials on the edge |
| `output` | OUTPUT | object — `{ status, … }` or `{ error }` | Never access keys, secret keys, or passwords |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = create-user` → `CreateUser`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `UserName` | string | yes | 1–64, `[\w+=,.@-]+`, unique in account (case-insensitive) |
| `Path` | string | no | Default `/`; 1–512 |
| `PermissionsBoundary` | string | no | Managed policy ARN (does not grant access) |
| `Tags` | array of `{ Key, Value }` | no | Max 50; invalid tag fails the whole create |

### `operation = get-user` → `GetUser`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `UserName` | string | no | If omitted, IAM infers the caller from the signing key |

### `operation = list-users` → `ListUsers`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `PathPrefix` | string | no | Default `/` (all users) |
| `MaxItems` | number | no | 1–1000; default 100 |
| `Marker` | string | no | Pagination |

`ListUsers` does **not** return `PermissionsBoundary` or `Tags` — use `GetUser`.

### `operation = update-user` → `UpdateUser`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `UserName` | string | yes | Current name |
| `NewUserName` | string | no | New name (1–64) |
| `NewPath` | string | no | New path |

Renaming requires permission on both old and new names. High risk.

### `operation = delete-user` → `DeleteUser`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `UserName` | string | yes | User to delete |

AWS rejects the call (`DeleteConflict`) until login profile, access keys, signing certs, SSH keys, service-specific credentials, MFA, inline/attached policies, and group memberships are removed. This node does **not** cascade those deletes (that would mint a cleanup/credential surface). Surface `DeleteConflict` as `{ error }`.

### `operation = add-user-to-group` / `remove-user-from-group`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `GroupName` | string | yes | 1–128, `[\w+=,.@-]+` |
| `UserName` | string | yes | 1–128 |

Quota: 10 groups per user ([IAM quotas](https://docs.aws.amazon.com/general/latest/gr/iam-service.html)).

### `operation = create-group` → `CreateGroup`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `GroupName` | string | yes | 1–128; do not include path |
| `Path` | string | no | Default `/` |

### `operation = get-group` → `GetGroup`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `GroupName` | string | yes | Group name |
| `MaxItems` | number | no | 1–1000; pages **members** |
| `Marker` | string | no | Pagination of `Users` |

Unlike Cognito `GetGroup`, IAM `GetGroup` returns member users.

### `operation = list-groups` → `ListGroups`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `PathPrefix` | string | no | Default `/` |
| `MaxItems` | number | no | 1–1000 |
| `Marker` | string | no | Pagination |

### `operation = update-group` → `UpdateGroup`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `GroupName` | string | yes | Current name |
| `NewGroupName` | string | no | New name |
| `NewPath` | string | no | New path |

### `operation = delete-group` → `DeleteGroup`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `GroupName` | string | yes | Must have no users and no attached policies |

## Output fields (by operation)

Map XML `*Result` / `ResponseMetadata.RequestId` to JSON. `User` / `Group` shapes:

**User:** `Path`, `UserName`, `UserId`, `Arn`, `CreateDate`; `PasswordLastUsed` when AWS returns it (GetUser/ListUsers); `PermissionsBoundary` `{ PermissionsBoundaryType, PermissionsBoundaryArn }` and `Tags` when AWS returns them (GetUser / CreateUser).

**Group:** `Path`, `GroupName`, `GroupId`, `Arn`; `CreateDate` when AWS returns it.

### `create-user`

| Field | Type | When | Purpose |
|---|---|---|---|
| `User` | object | success | New user (no secret material) |
| `RequestId` | string | success | IAM request id |

### `get-user`

| Field | Type | When | Purpose |
|---|---|---|---|
| `User` | object | success | User detail including `PasswordLastUsed` when present |

### `list-users`

| Field | Type | When | Purpose |
|---|---|---|---|
| `Users` | array | success | User summaries (`Users.member.N`) |
| `IsTruncated` | boolean | success | More pages |
| `Marker` | string | if truncated | Next `Marker` |

### `update-user`

AWS samples return a `User` in the result; treat `User` as optional if the account’s XML omits it. Always return `RequestId` / `status`.

### `delete-user` / `add-user-to-group` / `remove-user-from-group` / `update-group` / `delete-group`

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | number | HTTP 200 | Success |
| `RequestId` | string | success | Request id |

### `create-group`

| Field | Type | When | Purpose |
|---|---|---|---|
| `Group` | object | success | New group |

### `get-group`

| Field | Type | When | Purpose |
|---|---|---|---|
| `Group` | object | success | Group metadata |
| `Users` | array | success | Member users (`Users.member.N`) |
| `IsTruncated` | boolean | success | More members |
| `Marker` | string | if truncated | Next page |

### `list-groups`

| Field | Type | When | Purpose |
|---|---|---|---|
| `Groups` | array | success | `Groups.member.N` |
| `IsTruncated` | boolean | success | More pages |
| `Marker` | string | if truncated | Next `Marker` |

## Legal

- [AWS Customer Agreement](https://aws.amazon.com/agreement/)
- [AWS Service Terms](https://aws.amazon.com/service-terms/)
- [AWS Acceptable Use Policy](https://aws.amazon.com/aup/)
- [IAM User Guide — security best practices](https://docs.aws.amazon.com/IAM/latest/UserGuide/best-practices.html)
- Operator is responsible for the AWS account. Creating/deleting IAM users can lock out operators or grant account-wide access if policies are attached later (policy attach is out of v1).
- **PII:** IAM user names, paths, ARNs, and tags can identify people. Treat as personal data where applicable. No passwords or access keys in this node.
- **Quotas** ([IAM endpoints and quotas](https://docs.aws.amazon.com/general/latest/gr/iam-service.html)): 5,000 users/account (not adjustable); 300 groups/account (adjustable); 10 groups per user; 50 tags per user. AWS does **not** publish a public RPS table for IAM Query actions; on throttle, IAM returns a 4xx/5xx with `Throttling` / similar — map to `{ error }` and retry with jitter. Object limits: [IAM and STS quotas](https://docs.aws.amazon.com/IAM/latest/UserGuide/reference_iam-quotas.html).
- Official-API-first: no console scrape.

## Implementation notes

- JDK HTTP only; SigV4 on the POST body. Parse XML; do not pass raw XML secrets (none on these actions).
- Connect 10 s; read 30 s; cap 1 MiB (IAM list pages are small).
- Retry 429 / 5xx / `Throttling` / `ServiceFailure` max 3 with jitter. Do not retry `DeleteConflict`, `EntityAlreadyExists`, `NoSuchEntity`, `InvalidInput`.
- Error mapping: HTTP 4xx/5xx → `{ error: { httpStatus, code, message, requestId } }`. IAM XML errors use `Error.Code` / `Error.Message` / `RequestId`. Typical: `InvalidInput` 400, `NoSuchEntity` 404, `EntityAlreadyExists` 409, `DeleteConflict` 409, `LimitExceeded` 409, `ConcurrentModification` 409, `EntityTemporarilyUnmodifiable` 409, `ServiceFailure` 500.
- Never log `secretAccessKey`, `sessionToken`, or STS credentials. Add them to the redaction set.
- UNVERIFIED APIs: none of the 12 catalog-mapped actions (all live on 2026-08-20). Catalog ops unchanged. Credential APIs listed in Scope are **DO NOT BUILD**, not “later” rows.
