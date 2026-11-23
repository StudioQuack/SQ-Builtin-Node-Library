# Amazon Cognito Identity Provider

- **Status:** planned
- **Catalog:** `docs/catalog/security-identity.md`
- **Folder when built:** `nodes/aws-cognito/`
- **Researched:** 2026-08-20
- **Official API:** Amazon Cognito user pools Identity Provider API version **2016-04-18** (`cognito-idp`)
- **Docs:** https://docs.aws.amazon.com/cognito-user-identity-pools/latest/APIReference/Welcome.html
- **Auth:** `awsAccessKey` or `iamRole` — secret bindings: `accessKeyId`, `secretAccessKey`, `sessionToken` (optional), `externalId` (optional). Config also: `region`, `UserPoolId` (JSON body default; official Admin* APIs are POST `/`, not a path).

## Scope

Admin user-pool directory node. Calls the Amazon Cognito **user pools** API (`cognito-idp.{region}.amazonaws.com`) with IAM SigV4 credentials. Catalog kebab verbs map onto the current **Admin\*** / group / pool actions listed below. This is not Cognito Identity Pools (`cognito-identity`) and not the public user-auth surface (`InitiateAuth`, `SignUp`, `GetUser` with an access token).

Cognito stores personal data (email, phone, name, `sub`). IAM policies on the caller must be least privilege (`cognito-idp:AdminCreateUser` on one pool ARN, not `*`). Temporary passwords never appear on OUTPUT and never in logs.

**DO NOT BUILD:** `InitiateAuth` / `AdminInitiateAuth`, `SignUp`, token-authenticated `GetUser` / `UpdateUserAttributes` / `DeleteUser`, `AdminSetUserPassword`, `AdminResetUserPassword`, `CreateUserPool` / `UpdateUserPool` / `DeleteUserPool`, `ListUsersInGroup`, `AdminListGroupsForUser`, Identity Pools APIs.

## Wire protocol

Official sample requests (e.g. [AdminGetUser](https://docs.aws.amazon.com/cognito-user-identity-pools/latest/APIReference/API_AdminGetUser.html)) use **HTTPS POST** to the regional host, **not** Query `Action=` form fields:

- URL: `https://cognito-idp.{region}.amazonaws.com/` (path `/`)
- Method: `POST`
- `Content-Type: application/x-amz-json-1.1`
- `X-Amz-Target: AWSCognitoIdentityProviderService.{ActionName}`
- Body: JSON object of that action’s request parameters
- Auth: [Signature Version 4](https://docs.aws.amazon.com/IAM/latest/UserGuide/reference_sigv-create-signed-request.html) — service name `cognito-idp`, signing region = config `region`
- Responses: JSON (`Content-Type` in samples is `application/x-amz-json-1.0`)

IAM Query (`Action` + `Version` over POST) is the pattern for IAM/STS. For this node, implement the JSON target header as published on each action page. JDK HTTP only — **do not** add an AWS SDK. Admin actions do not need SRP.

`iamRole`: first call STS Query `AssumeRole` (`https://sts.{region}.amazonaws.com/`, `Action=AssumeRole&Version=2011-06-15`, SigV4 service `sts`), then sign Cognito with the temporary key/secret/token. STS default quota: 600 RPS per account per Region ([IAM quotas](https://docs.aws.amazon.com/IAM/latest/UserGuide/reference_iam-quotas.html)).

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `create-user` | POST | `X-Amz-Target: AWSCognitoIdentityProviderService.AdminCreateUser` | Create a local user in the pool |
| `get-user` | POST | `…AdminGetUser` | Fetch one user by username/alias |
| `list-users` | POST | `…ListUsers` | Page users; optional server-side `Filter` |
| `update-user` | POST | `…AdminUpdateUserAttributes` | Set/clear attributes (blank value deletes) |
| `delete-user` | POST | `…AdminDeleteUser` | Delete a user profile |
| `add-user-to-group` | POST | `…AdminAddUserToGroup` | Add user to a pool group |
| `remove-user-from-group` | POST | `…AdminRemoveUserFromGroup` | Remove user from a group |
| `create-group` | POST | `…CreateGroup` | Create a group |
| `get-group` | POST | `…GetGroup` | Group metadata (not membership) |
| `list-groups` | POST | `…ListGroups` | Page groups |
| `update-group` | POST | `…UpdateGroup` | Description / precedence / role |
| `delete-group` | POST | `…DeleteGroup` | Delete a group |
| `get-user-pool` | POST | `…DescribeUserPool` | Pool configuration metadata |

Action docs: [AdminCreateUser](https://docs.aws.amazon.com/cognito-user-identity-pools/latest/APIReference/API_AdminCreateUser.html), [AdminGetUser](https://docs.aws.amazon.com/cognito-user-identity-pools/latest/APIReference/API_AdminGetUser.html), [ListUsers](https://docs.aws.amazon.com/cognito-user-identity-pools/latest/APIReference/API_ListUsers.html), [AdminUpdateUserAttributes](https://docs.aws.amazon.com/cognito-user-identity-pools/latest/APIReference/API_AdminUpdateUserAttributes.html), [AdminDeleteUser](https://docs.aws.amazon.com/cognito-user-identity-pools/latest/APIReference/API_AdminDeleteUser.html), [AdminAddUserToGroup](https://docs.aws.amazon.com/cognito-user-identity-pools/latest/APIReference/API_AdminAddUserToGroup.html), [AdminRemoveUserFromGroup](https://docs.aws.amazon.com/cognito-user-identity-pools/latest/APIReference/API_AdminRemoveUserFromGroup.html), [CreateGroup](https://docs.aws.amazon.com/cognito-user-identity-pools/latest/APIReference/API_CreateGroup.html), [GetGroup](https://docs.aws.amazon.com/cognito-user-identity-pools/latest/APIReference/API_GetGroup.html), [ListGroups](https://docs.aws.amazon.com/cognito-user-identity-pools/latest/APIReference/API_ListGroups.html), [UpdateGroup](https://docs.aws.amazon.com/cognito-user-identity-pools/latest/APIReference/API_UpdateGroup.html), [DeleteGroup](https://docs.aws.amazon.com/cognito-user-identity-pools/latest/APIReference/API_DeleteGroup.html), [DescribeUserPool](https://docs.aws.amazon.com/cognito-user-identity-pools/latest/APIReference/API_DescribeUserPool.html).

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `create-user` | no | Row in the table above |
| `auth` | select | yes | `awsAccessKey` | no | `awsAccessKey` or `iamRole` |
| `accessKeyId` | password | if `awsAccessKey` | | yes | IAM access key id |
| `secretAccessKey` | password | if `awsAccessKey` | | yes | IAM secret access key |
| `sessionToken` | password | no | | yes | STS session token when using temporary keys |
| `roleArn` | string | if `iamRole` | | no | Role to assume |
| `roleSessionName` | string | no | `studioquack-cognito` | no | STS session name |
| `externalId` | password | no | | yes | STS `ExternalId` if the trust policy requires it |
| `region` | string | yes | | no | User-pool Region (`us-east-1`, …) |
| `UserPoolId` | string | yes | | no | Default JSON-body `UserPoolId` (`[\w-]+_[0-9a-zA-Z]+`, max 55). Blank INPUT falls back here. |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | No credentials on the edge |
| `output` | OUTPUT | object — `{ status, … }` or `{ error }` | Failures never include secrets or passwords |

FUNCTION ports: none.

## Input fields (by operation)

`UserPoolId` on INPUT overrides config when set. Types match the API JSON names.

### `operation = create-user` → `AdminCreateUser`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `UserPoolId` | string | no | Pool id (else config) |
| `Username` | string | yes | Sign-in username (1–128) |
| `UserAttributes` | array of `{ Name, Value }` | no | Standard/custom attributes; custom names need `custom:` prefix |
| `TemporaryPassword` | password | no | SQ secret binding. Omit for passwordless pools. Never log |
| `ForceAliasCreation` | boolean | no | Migrate email/phone alias if verified flags are true |
| `MessageAction` | string | no | `RESEND` or `SUPPRESS` |
| `DesiredDeliveryMediums` | array of string | no | `SMS` and/or `EMAIL` (default SMS) |
| `ClientMetadata` | object | no | Lambda trigger map; not stored; do not put secrets here |
| `ValidationData` | array of `{ Name, Value }` | no | Pre sign-up Lambda only |

### `operation = get-user` → `AdminGetUser`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `UserPoolId` | string | no | Pool id |
| `Username` | string | yes | Username or alias / `sub` |

### `operation = list-users` → `ListUsers`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `UserPoolId` | string | no | Pool id |
| `Filter` | string | no | `"AttributeName Filter-Type \"value\""` — searchable: `username`, `email`, `phone_number`, `name`, `given_name`, `family_name`, `preferred_username`, `cognito:user_status`, `status`, `sub`. Max 256. Custom attributes are not searchable |
| `Limit` | number | no | 0–60 |
| `PaginationToken` | string | no | Next page |
| `AttributesToGet` | array of string | no | Attribute names to return; omit = all |

### `operation = update-user` → `AdminUpdateUserAttributes`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `UserPoolId` | string | no | Pool id |
| `Username` | string | yes | Username or alias |
| `UserAttributes` | array of `{ Name, Value }` | yes | Blank `Value` deletes the attribute |
| `ClientMetadata` | object | no | Lambda trigger map; not encrypted |

### `operation = delete-user` → `AdminDeleteUser`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `UserPoolId` | string | no | Pool id |
| `Username` | string | yes | Username or alias |

### `operation = add-user-to-group` / `remove-user-from-group`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `UserPoolId` | string | no | Pool id |
| `Username` | string | yes | Username or alias |
| `GroupName` | string | yes | Group name (1–128) |

### `operation = create-group` / `update-group`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `UserPoolId` | string | no | Pool id |
| `GroupName` | string | yes | Unique in the pool |
| `Description` | string | no | Max 2048 |
| `Precedence` | number | no | ≥ 0; lower wins |
| `RoleArn` | string | no | IAM role ARN for `cognito:preferred_role` |

### `operation = get-group` / `delete-group`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `UserPoolId` | string | no | Pool id |
| `GroupName` | string | yes | Group name |

### `operation = list-groups` → `ListGroups`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `UserPoolId` | string | no | Pool id |
| `Limit` | number | no | 0–60 |
| `NextToken` | string | no | Next page |

### `operation = get-user-pool` → `DescribeUserPool`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `UserPoolId` | string | no | Pool id (else config) |

## Output fields (by operation)

Common success envelope: `status` (HTTP 200), `requestId` (`x-amzn-requestid`). Empty AWS bodies still return `{ status: 200 }`.

### `create-user`

| Field | Type | When | Purpose |
|---|---|---|---|
| `User.Username` | string | success | Created username |
| `User.Attributes` | array | success | `{ Name, Value }` (PII) |
| `User.Enabled` | boolean | success | Sign-in enabled |
| `User.UserStatus` | string | success | e.g. `FORCE_CHANGE_PASSWORD` |
| `User.UserCreateDate` | number | success | Unix epoch |
| `User.UserLastModifiedDate` | number | success | Unix epoch |
| `User.MFAOptions` | array | if present | Legacy SMS MFA only |

Do **not** echo `TemporaryPassword`.

### `get-user`

| Field | Type | When | Purpose |
|---|---|---|---|
| `Username` | string | success | Username |
| `Enabled` | boolean | success | Sign-in enabled |
| `UserStatus` | string | success | `UNCONFIRMED` \| `CONFIRMED` \| `ARCHIVED` \| `COMPROMISED` \| `UNKNOWN` \| `RESET_REQUIRED` \| `FORCE_CHANGE_PASSWORD` \| `EXTERNAL_PROVIDER` |
| `UserAttributes` | array | success | `{ Name, Value }` including `sub`, email, phone |
| `UserCreateDate` | number | success | Unix epoch |
| `UserLastModifiedDate` | number | success | Unix epoch |
| `PreferredMfaSetting` | string | if set | Preferred MFA |
| `UserMFASettingList` | array of string | if set | `SMS_MFA`, `EMAIL_OTP`, `SOFTWARE_TOKEN_MFA` |
| `MFAOptions` | array | if present | Deprecated; prefer `UserMFASettingList` |

### `list-users`

| Field | Type | When | Purpose |
|---|---|---|---|
| `Users` | array | success | `UserType`: `Username`, `Attributes`, `Enabled`, `UserStatus`, dates, optional `MFAOptions`. Sort order is not stable |
| `PaginationToken` | string | more pages | Pass back as input |

### `update-user` / `delete-user` / `add-user-to-group` / `remove-user-from-group` / `delete-group`

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | number | HTTP 200 empty body | Success |

### `create-group` / `get-group` / `update-group`

| Field | Type | When | Purpose |
|---|---|---|---|
| `Group.GroupName` | string | success | Name |
| `Group.UserPoolId` | string | success | Pool |
| `Group.Description` | string | if set | Description |
| `Group.Precedence` | number | if set | Precedence |
| `Group.RoleArn` | string | if set | IAM role |
| `Group.CreationDate` | number | success | Unix epoch |
| `Group.LastModifiedDate` | number | success | Unix epoch |

### `list-groups`

| Field | Type | When | Purpose |
|---|---|---|---|
| `Groups` | array | success | `GroupType` objects as above |
| `NextToken` | string | more pages | Pass back as input |

### `get-user-pool`

Return AWS `UserPool` (`UserPoolType`) as a nested object. Top-level keys from the 2026-08-20 response syntax (do not invent others): `AccountRecoverySetting`, `AdminCreateUserConfig`, `AliasAttributes`, `Arn`, `AutoVerifiedAttributes`, `CreationDate`, `CustomDomain`, `DeletionProtection`, `DeviceConfiguration`, `Domain`, `EmailConfiguration`, `EmailConfigurationFailure`, `EmailVerificationMessage`, `EmailVerificationSubject`, `EstimatedNumberOfUsers`, `Id`, `IssuerConfiguration`, `KeyConfiguration`, `LambdaConfig`, `LastModifiedDate`, `MfaConfiguration`, `Name`, `Policies` (contains `PasswordPolicy`, `SignInPolicy`), `SchemaAttributes`, `SmsAuthenticationMessage`, `SmsConfiguration`, `SmsConfigurationFailure`, `SmsVerificationMessage`, `Status`, `UserAttributeUpdateSettings`, `UsernameAttributes`, `UsernameConfiguration`, `UserPoolAddOns`, `UserPoolTags`, `UserPoolTier`, `VerificationMessageTemplate`.

Do not log message templates or `SmsConfiguration.ExternalId` (IAM external id for the SMS role).

## Legal

- [AWS Customer Agreement](https://aws.amazon.com/agreement/)
- [AWS Service Terms](https://aws.amazon.com/service-terms/)
- [AWS Acceptable Use Policy](https://aws.amazon.com/aup/)
- [AWS Privacy Notice](https://aws.amazon.com/privacy/)
- Operator must have rights in the AWS account / user pool. StudioQuack does not accept AWS terms on the operator’s behalf.
- **PII:** user attributes (email, phone, name, `sub`, custom attributes) are personal data (GDPR and similar). Minimize `AttributesToGet`. This node is a processor of customer-configured AWS data; residency follows the pool’s Region.
- **HIPAA:** Cognito is in-scope for AWS BAA when the operator has one; still least-privilege IAM and no PHI in logs.
- **Rate limits** (per account per Region, [user pool quotas](https://docs.aws.amazon.com/cognito/latest/developerguide/quotas.html)): `UserCreation` (includes `AdminCreateUser`) 50 RPS; `UserRead` (`AdminGetUser`) 120 RPS; `UserUpdate` (`AdminUpdateUserAttributes`, `AdminDeleteUser`, add/remove group) 25 RPS (not adjustable); `UserList` (`ListUsers`) 30 RPS; `UserPoolRead` (`DescribeUserPool`) 15 RPS; `UserPoolResourceRead` (`GetGroup`, `ListGroups`) 20 RPS (5 RPS cap per pool per operation); `UserPoolResourceUpdate` (`CreateGroup`, `UpdateGroup`, `DeleteGroup`) 15 RPS (5 RPS per pool per operation). `AdminGetUser` counts toward MAU billing.
- Official-API-first: no scrape of the Cognito console.

## Implementation notes

- JDK HTTP only. Implement SigV4 (HMAC-SHA256) in-process. No AWS SDK.
- Connect timeout 10 s; read timeout 30 s. Cap response body at 6 MiB.
- Retry `TooManyRequestsException` / HTTP 429 / 5xx with exponential backoff + jitter, max 3. Do not retry other 4xx.
- Error mapping: HTTP 4xx/5xx → `{ error: { httpStatus, code, message, requestId } }` on OUTPUT. Parse AWS JSON `__type` / `message`. Never throw past dispatch. Never put passwords or keys in `error`.
- Typical codes: `InvalidParameterException` 400, `NotAuthorizedException` 400, `ResourceNotFoundException` 400, `UserNotFoundException` 400, `UsernameExistsException` 400, `InvalidPasswordException` 400, `AliasExistsException` 400, `GroupExistsException` 400, `LimitExceededException` 400, `TooManyRequestsException` 400, `InternalErrorException` 500.
- Log shapes, ids, status, sizes — not `UserAttributes` values, not `TemporaryPassword`, not `ClientMetadata`.
- Add resolved key material and `TemporaryPassword` to the redaction set.
- `ListUsers` is eventually consistent; use `AdminGetUser` to confirm one user.
- UNVERIFIED APIs: none of the 13 catalog-mapped actions. Dropped (not in catalog, do not add): password reset, membership list, pool CRUD, auth/token APIs.
