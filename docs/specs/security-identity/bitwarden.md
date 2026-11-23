# Bitwarden

- **Status:** planned
- **Catalog:** `docs/catalog/security-identity.md`
- **Folder when built:** `nodes/bitwarden/`
- **Researched:** 2026-08-20
- **Official API:** Bitwarden Public API (`/public/…`, OAS3) + Secrets Manager REST (`/secrets/…`)
- **Docs:** https://bitwarden.com/help/public-api/ · SM: https://bitwarden.com/help/secrets-manager-sdk/ and https://bitwarden.com/help/access-tokens/ (catalog URL `https://bitwarden.com/help/secrets-manager-api/` is **404** as of 2026-08-20)
- **Auth:** clientId+clientSecret (org public API) — secret bindings: `clientSecret`; SM `accessToken` for `get-secret` / `list-secrets`

## Scope

One node for **organization administration** (Public API: collections, groups, members, events) plus **Secrets Manager** get/list for application secrets. This is **not** a personal vault dump. Public API docs: “This API does not allow for management of individual vault items.” Do not call the Vault Management API (`/ciphers`).

Public API is Teams/Enterprise org-admin. SM uses a machine-account access token.

**Secret-value rule:** SM `get-secret` / `list-secrets` never emit secret `value` (or encrypted blobs) on OUTPUT — metadata + `secretRef`. Public API responses are org metadata (emails are PII). Never log tokens or SM values.

## Operations (`operation` config values)

Base (cloud US): `https://api.bitwarden.com`. EU: `https://api.bitwarden.eu`. Self-host: `{serverUrl}/api`. Identity: `https://identity.bitwarden.com/connect/token` (EU: `identity.bitwarden.eu`; self-host `{serverUrl}/identity/connect/token`).

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `list-collections` | GET | `/public/collections` | Paginated (`continuationToken`) |
| `get-collection` | GET | `/public/collections/{id}` | One collection |
| `update-collection` | PUT | `/public/collections/{id}` | `externalId`, `groups` |
| `delete-collection` | DELETE | `/public/collections/{id}` | Delete collection |
| `list-events` | GET | `/public/events` | Org event logs |
| `create-group` | POST | `/public/groups` | Create group |
| `get-group` | GET | `/public/groups/{id}` | |
| `list-groups` | GET | `/public/groups` | |
| `update-group` | PUT | `/public/groups/{id}` | Full replacement of collections array |
| `delete-group` | DELETE | `/public/groups/{id}` | |
| `get-group-members` | GET | `/public/groups/{id}/member-ids` | UUID array |
| `update-group-members` | PUT | `/public/groups/{id}/member-ids` | Replace membership |
| `create-member` | POST | `/public/members` | Invite |
| `get-member` | GET | `/public/members/{id}` | Org membership UUID, not global user id |
| `list-members` | GET | `/public/members` | |
| `update-member` | PUT | `/public/members/{id}` | Full replacement — GET first |
| `delete-member` | DELETE | `/public/members/{id}` | Remove from org |
| `get-member-groups` | GET | `/public/members/{id}/group-ids` | |
| `update-member-groups` | PUT | `/public/members/{id}/group-ids` | |
| `get-secret` | GET | `/secrets/{id}` | SM; value → `secretRef` |
| `list-secrets` | GET | `/organizations/{organizationId}/secrets` | SM identifiers; values → `secretRef` or omit |

Dropped: `/public/policies`, personal Vault Management API, SCIM, create-collection (not on Public API). Retired catalog help path `secrets-manager-api` (404).

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `list-collections` | no | Chooses the row above |
| `serverUrl` | string | no | `https://api.bitwarden.com` | no | API origin. Cloud EU `https://api.bitwarden.eu`. Self-host `https://your.domain.com/api` |
| `identityUrl` | string | no | derived | no | Token URL. Default: US `https://identity.bitwarden.com/connect/token`, EU matching, self-host `{origin}/identity/connect/token` |
| `clientId` | string | public-api ops | empty | no | Org API key id (`organization.…`). **Not** personal `user.` keys |
| `clientSecret` | password | public-api ops | empty | yes | Org API key secret |
| `accessToken` | password | SM ops | empty | yes | Machine-account SM access token |
| `organizationId` | string | `list-secrets` | empty | no | Org UUID for SM list |
| `timeout_seconds` | number | no | `25` | no | Timeout |

Public API token: POST identity with `Content-Type: application/x-www-form-urlencoded`, `grant_type=client_credentials`, `scope=api.organization`, `client_id`, `client_secret`. Response `access_token`, `expires_in` (3600). Header `Authorization: Bearer {access_token}`.

SM: **UNVERIFIED** exact identity form for raw REST (SDK wraps the access token). Implementer must follow the current Secrets Manager SDK: exchange access token for an API bearer, then GET the paths above. Do not invent a second protocol. If exchange cannot be confirmed against SDK source at build time, fail the SM ops with `{ error }` rather than sending the raw access token to the wrong host.

Reject `clientId` that starts with `user.` (personal key).

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object | Per-operation |
| `output` | OUTPUT | `{ status, … }` or `{ error }` | No SM `value`, no client secrets |

FUNCTION ports: none.

## Input fields (by operation)

### `list-collections` | `list-groups` | `list-members` | `list-events`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `continuationToken` | string | no | Query `continuationToken` (lists of 50+) |

`list-events` may also take documented date filters if present on OAS — **UNVERIFIED** extra query names; start with `continuationToken` only.

### `get-collection` | `delete-collection`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `id` | string | yes | Collection UUID |

### `update-collection`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `id` | string | yes | |
| `externalId` | string | no | Max 300 |
| `groups` | array | no | `{ id, readOnly, hidePasswords, manage }` |

### `create-group`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `name` | string | yes | |
| `externalId` | string | no | |
| `collections` | array | no | `{ id, readOnly, hidePasswords, manage }` |

### `get-group` | `delete-group` | `get-group-members`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `id` | string | yes | Group UUID |

### `update-group`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `id` | string | yes | |
| `name` | string | yes | |
| `collections` | array | yes | **Complete** list; omitted collections are removed |
| `externalId` | string | no | |

### `update-group-members`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `id` | string | yes | Group UUID |
| `memberIds` | string[] | yes | Complete org-user UUID list |

### `create-member`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `email` | string | yes | Invite email (PII) |
| `type` | number | yes | Org role enum (OAS `OrganizationUserType`) |
| `accessAll` | boolean | no | |
| `collections` | array | no | `{ id, readOnly, hidePasswords, manage }` |
| `groups` | string[] | no | Group UUIDs |
| `externalId` | string | no | |

### `get-member` | `delete-member` | `get-member-groups`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `id` | string | yes | Organization membership UUID |

### `update-member`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `id` | string | yes | |
| `type` | number | yes | Full replacement |
| `accessAll` | boolean | no | |
| `collections` | array | no | |
| `externalId` | string | no | |

Handler should GET then PUT so omitted fields are not wiped.

### `update-member-groups`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `id` | string | yes | Member UUID |
| `groupIds` | string[] | yes | Complete list — **UNVERIFIED** body key (`groupIds` vs raw array); match OAS at build |

### `get-secret`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `id` | string | yes | Secret UUID |

### `list-secrets`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `organizationId` | string | no | Else config `organizationId` |

## Output fields (by operation)

Public list envelope: `{ "object": "list", "data": [ … ], "continuationToken": "…" }`. Pass through `data` and `continuationToken`. HTTP 429 documented.

Member objects include `email` (PII). Event objects include `ipAddress` (PII).

### SM `get-secret` (2xx)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `id` | string | 2xx | |
| `organizationId` | string | if present | |
| `key` | string | if present | Secret name (not the value) |
| `creationDate` | string | if present | Field names **UNVERIFIED** vs SDK (`creationDate` / `createdAt`) — map real JSON keys |
| `revisionDate` | string | if present | |
| `projectIds` | array | if present | |
| `secretRef` | string | 2xx | Handle for `value`. **Do not** emit `value` / encrypted payload |

### SM `list-secrets` (2xx)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `secrets` | array | 2xx | `{ id, key, organizationId, projectIds, … }` plus optional `secretRef` per row if the API returns values. **Never** `value` |

If list returns identifiers only, omit `secretRef`.

## Legal

- Public API: https://bitwarden.com/help/public-api/
- OAS explorer (cloud): https://bitwarden.com/help/api/
- SM CLI: https://bitwarden.com/help/secrets-manager-cli/
- SM SDK: https://bitwarden.com/help/secrets-manager-sdk/
- Access tokens: https://bitwarden.com/help/access-tokens/
- Terms: https://bitwarden.com/terms/
- Rate limits: HTTP **429**; no published numeric quota on the Public API page.
- PII: member emails, event IP addresses, device ids. Org API key is org-admin equivalent — rotate if leaked.
- Official-API-first: no web vault scrape. No personal cipher export.

## Implementation notes

- JDK HTTP. TLS on `serverUrl` / identity. `Redirect.NEVER`.
- Timeouts: connect 10 s, request `timeout_seconds`. Body cap **256 KiB**. Response cap **1 MiB**.
- Retry: no inner loop. 429/5xx/timeout → `{ error, http_status, retry_after_seconds }` (`Retry-After` or 60).
- Never log `clientSecret`, `access_token`, SM `value`.
- `update-member` / `update-group`: full replace; document operator must send complete collections.
