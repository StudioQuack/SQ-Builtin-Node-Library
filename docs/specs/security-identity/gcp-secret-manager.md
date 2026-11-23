# GCP Secret Manager

- **Status:** planned
- **Catalog:** `docs/catalog/security-identity.md`
- **Folder when built:** `nodes/gcp-secret-manager/`
- **Researched:** 2026-08-20
- **Official API:** Secret Manager API v1 (`secretmanager.googleapis.com`)
- **Docs:** https://cloud.google.com/secret-manager/docs/reference/rest
- **Auth:** serviceAccountJson | oauth2 — secret bindings: `serviceAccountJson`, `oauthAccessToken` / `oauthRefreshToken`+`oauthClientId`+`oauthClientSecret`

## Scope

One node for Google Cloud Secret Manager. Calls REST v1 on `https://secretmanager.googleapis.com` (optional regional endpoint **UNVERIFIED** for this node: default global only unless `location` is set). Designer picks `operation`.

**Secret-value rule:** `access-secret-version` returns metadata + `secretRef` — never `payload.data` on OUTPUT. `add-secret-version` / `create-secret` payload bytes come from SQ secret bindings. `get-secret` / `list-secrets` are metadata-only (Google does not return secret data on those methods). Never log secret material.

Do not call Secret Manager v1beta1. Do not use a vendor SDK.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `access-secret-version` | GET | `/v1/projects/{projectId}/secrets/{secretId}/versions/{version}:access` | Read version payload → `secretRef`. `latest` allowed |
| `add-secret-version` | POST | `/v1/projects/{projectId}/secrets/{secretId}:addVersion` | Attach a new `SecretVersion` |
| `create-secret` | POST | `/v1/projects/{projectId}/secrets?secretId={secretId}` | Create empty Secret (metadata + replication) |
| `get-secret` | GET | `/v1/projects/{projectId}/secrets/{secretId}` | Secret metadata only |
| `list-secrets` | GET | `/v1/projects/{projectId}/secrets` | List Secret resources (no payloads) |
| `destroy-secret-version` | POST | `/v1/projects/{projectId}/secrets/{secretId}/versions/{version}:destroy` | Irreversible destroy of one version |
| `delete-secret` | DELETE | `/v1/projects/{projectId}/secrets/{secretId}` | Delete the Secret and versions |

Dropped: `disable`/`enable` version, `patch`, IAM (`getIamPolicy`/`setIamPolicy`/`testIamPermissions`), regional `projects.locations.*` unless `location` is implemented as a documented optional.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `access-secret-version` | no | Chooses the row above |
| `auth` | select | yes | `serviceAccountJson` | no | `serviceAccountJson` \| `oauth2` |
| `projectId` | string | yes | empty | no | GCP project id (`projects/{projectId}`) |
| `serviceAccountJson` | password | if `auth=serviceAccountJson` | empty | yes | Full service-account JSON (`client_email`, `private_key`, `token_uri`) |
| `oauthAccessToken` | password | if `auth=oauth2` and no refresh | empty | yes | Bearer access token |
| `oauthClientId` | string | if refresh | empty | no | OAuth client id |
| `oauthClientSecret` | password | if refresh | empty | yes | OAuth client secret |
| `oauthRefreshToken` | password | if using refresh | empty | yes | Refresh token |
| `timeout_seconds` | number | no | `25` | no | Connect + request timeout |

OAuth scope (documented on methods): `https://www.googleapis.com/auth/cloud-platform`. Least-privilege IAM: Secret Manager Secret Accessor for access; Admin for create/delete. Header `Authorization: Bearer {access_token}`.

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object | Per-operation; payload from secret-typed fields |
| `output` | OUTPUT | `{ status, … }` or `{ error }` | No `payload.data`, no SA JSON, no Bearer |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = access-secret-version`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `secretId` | string | yes | Secret id (path segment) |
| `version` | string | no | Version id or `latest`. Default `latest` |

### `operation = add-secret-version`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `secretId` | string | yes | Parent secret |
| `payload` | password / secret-typed | yes | Raw secret bytes/string. Handler base64-encodes as `payload.data`. Max **64 KiB** (Google documented limit) |
| `dataCrc32c` | string | no | Optional CRC32C of the data (decimal string per API) |

Body: `{ "payload": { "data": "<base64>", "dataCrc32c": "…" } }`.

### `operation = create-secret`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `secretId` | string | yes | Query `secretId`. Max 255; `[A-Za-z0-9_-]` |
| `replication` | object | yes | Google `Secret.replication`: `{ "automatic": {} }` or `{ "userManaged": { "replicas": [ { "location": "us-central1" } ] } }` |
| `labels` | object | no | String map |
| `expireTime` | string | no | RFC3339 |
| `ttl` | string | no | Duration string if API accepts on create — **UNVERIFIED** vs `expireTime`; prefer `expireTime` if both exist |

Create does **not** add a version; follow with `add-secret-version`.

### `operation = get-secret`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `secretId` | string | yes | Secret id |

### `operation = list-secrets`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `pageSize` | number | no | Query `pageSize`. Server cap **25000** |
| `pageToken` | string | no | Query `pageToken` |
| `filter` | string | no | Query `filter` (List-operation filtering) |

### `operation = destroy-secret-version`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `secretId` | string | yes | |
| `version` | string | yes | Must be a numeric version, not `latest` if destroy of latest is ambiguous — Google allows specific version ids. **UNVERIFIED** whether `latest` is accepted; require numeric id |
| `etag` | string | no | Body `{ "etag" }` |

### `operation = delete-secret`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `secretId` | string | yes | |
| `etag` | string | no | Query `etag` |

## Output fields (by operation)

Common: `error`, `http_status`, `retry_after_seconds`. Google errors: `{ "error": { "code", "message", "status" } }` — pass `message`/`status` after redaction.

### `access-secret-version` (2xx)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `name` | string | 2xx | Resource name `projects/…/secrets/…/versions/…` |
| `secretRef` | string | 2xx | Handle for decoded `payload.data`. **Do not** emit `payload` |
| `dataCrc32c` | string | if present | Integrity checksum (not secret material) |

### `add-secret-version` (2xx)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `name` | string | 2xx | Version resource name |
| `state` | string | 2xx | e.g. `ENABLED` |
| `createTime` | string | if present | |
| `etag` | string | if present | |

Do not echo payload.

### `create-secret` / `get-secret` (2xx)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `name` | string | 2xx | `projects/…/secrets/…` |
| `replication` | object | if present | Policy metadata |
| `createTime` | string | if present | |
| `labels` | object | if present | |
| `etag` | string | if present | |
| `expireTime` | string | if present | |

### `list-secrets` (2xx)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `secrets` | array | 2xx | Metadata objects (`name`, `createTime`, `labels`, `replication`, `etag`) — no payloads |
| `nextPageToken` | string | if present | |
| `totalSize` | number | if present | 0 when `filter` is set (Google) |

### `destroy-secret-version` (2xx)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `name` | string | 2xx | |
| `state` | string | 2xx | Expect `DESTROYED` |

### `delete-secret` (2xx)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |

## Legal

- REST reference: https://cloud.google.com/secret-manager/docs/reference/rest
- Access: https://cloud.google.com/secret-manager/docs/access-secret-version
- Create: https://cloud.google.com/secret-manager/docs/creating-and-accessing-secrets
- Google Cloud Terms: https://cloud.google.com/terms
- Google APIs Terms: https://developers.google.com/terms
- Rate limits: per-project quotas (console). HTTP **429** / `RESOURCE_EXHAUSTED`. Secret payload max **64 KiB**.
- PII: secret payloads may be credentials or personal data. Replication locations affect residency (operator chooses `automatic` vs `userManaged` replicas).
- Official-API-first: no Cloud Console scrape.

## Implementation notes

- JDK HTTP. Token: JWT bearer grant to `https://oauth2.googleapis.com/token` from SA JSON (`grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer`) or refresh_token grant. Never log `private_key`.
- Timeouts: connect 10 s, request `timeout_seconds`. Body cap **128 KiB** (payload 64 KiB + JSON). Response cap **1 MiB**.
- Retry: no inner loop. 429 / 5xx / timeout → `{ error, http_status, retry_after_seconds }` (`Retry-After` or 30).
- TLS verified. `Redirect.NEVER`.
- IAM: Accessor vs Admin as catalog notes. Fail closed on 403.
