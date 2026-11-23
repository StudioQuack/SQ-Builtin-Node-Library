# 1Password

- **Status:** planned
- **Catalog:** `docs/catalog/security-identity.md`
- **Folder when built:** `nodes/1password/`
- **Researched:** 2026-08-20
- **Official API:** 1Password Connect Server API 1.8.1 (`/v1/…`)
- **Docs:** https://developer.1password.com/docs/connect/api-reference
- **Auth:** bearerConnectToken — secret binding: `connectToken`

## Scope

One node for a **customer-deployed** 1Password Connect server (Secrets Automation). The handler calls Connect REST on `connectHost`. This is not the 1Password.com website and not a personal vault dump via unofficial clients.

**Secret-value rule:** never log or emit item `fields[].value`, `passwordDetails`, file `content`, or inline file bytes. `get-item` returns metadata + `secretRef` (full item fields bound as one secret JSON **or** per-field refs — implementer: one `secretRef` for the fields map). `get-file-content` returns type `file` (platform ref-id). PUT/create field values and file bytes come from SQ secret bindings / `file` inputs.

Requires Secrets Automation + Connect in customer infra. Token is vault-scoped.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `list-vaults` | GET | `/v1/vaults` | List vaults the token can see |
| `get-vault` | GET | `/v1/vaults/{vaultUUID}` | Vault metadata |
| `list-items` | GET | `/v1/vaults/{vaultUUID}/items` | Item summaries (**no** sections/fields) |
| `get-item` | GET | `/v1/vaults/{vaultUUID}/items/{itemUUID}` | Full item → metadata + `secretRef` |
| `create-item` | POST | `/v1/vaults/{vaultUUID}/items` | Body: FullItem |
| `replace-item` | PUT | `/v1/vaults/{vaultUUID}/items/{itemUUID}` | Full replace |
| `patch-item` | PATCH | `/v1/vaults/{vaultUUID}/items/{itemUUID}` | Partial update |
| `delete-item` | DELETE | `/v1/vaults/{vaultUUID}/items/{itemUUID}` | Delete item |
| `list-files` | GET | `/v1/vaults/{vaultUUID}/items/{itemUUID}/files` | File metadata (`inline_content=false`) |
| `get-file` | GET | `/v1/vaults/{vaultUUID}/items/{itemUUID}/files/{fileUUID}` | File metadata |
| `get-file-content` | GET | `/v1/vaults/{vaultUUID}/items/{itemUUID}/files/{fileUUID}/content` | Download → type `file` |
| `list-activity` | GET | `/v1/activity` | Connect API request log |

Dropped: `/heartbeat`, `/health`, `/metrics` (not catalogued). Do not set `inline_content=true` (returns secret file bytes in JSON).

OpenAPI download (docs): https://i.1password.com/media/1password-connect/1password-connect-api_1.8.1.yaml

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `list-vaults` | no | Chooses the row above |
| `connectHost` | string | yes | empty | no | Connect origin, `https` only. Example: `https://connect.internal:8443` |
| `connectToken` | password | yes | empty | yes | Access token. Header `Authorization: Bearer {token}` |
| `timeout_seconds` | number | no | `25` | no | Connect + request timeout |

Headers: `Authorization: Bearer <access_token>`, `Content-Type: application/json` (JSON ops). File download uses the response `Content-Type` from Connect.

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object | `file` type for uploads and `get-file-content` is OUTPUT `file` |
| `output` | OUTPUT | `{ status, … }` or `{ error }` | Never field values or file bytes inline |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = list-vaults`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `filter` | string | no | SCIM-style. Vaults filter by name, e.g. `name eq "Demo Vault"` |

### `operation = get-vault`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `vaultUUID` | string | yes | Path `vaultUUID` |

### `operation = list-items`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `vaultUUID` | string | yes | |
| `filter` | string | no | SCIM. Items: `title` or `tag`, e.g. `title eq "Example Item"` |

### `operation = get-item` | `delete-item`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `vaultUUID` | string | yes | |
| `itemUUID` | string | yes | |

### `operation = create-item`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `vaultUUID` | string | yes | Path; also FullItem `vault.id` |
| `title` | string | yes | |
| `category` | string | yes | e.g. `LOGIN` |
| `tags` | string[] | no | |
| `urls` | object[] | no | `{ href, label, primary }` |
| `sections` | object[] | no | `{ id, label }` |
| `fields` | object[] | no | FullItem fields. **`value` must be secret-typed / password**. `purpose`, `type`, `label`, `generate`, `recipe` as docs |
| `files` | array | no | Each: `name` + `content` type **`file`** (platform ref-id). Handler reads bytes just-in-time; never log |

### `operation = replace-item`

Same as create-item plus required `itemUUID`. PUT FullItem.

### `operation = patch-item`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `vaultUUID` | string | yes | |
| `itemUUID` | string | yes | |
| `patch` | object | yes | Partial Item/FullItem per Connect PATCH semantics. Any `fields[].value` secret-typed |

### `operation = list-files` | `get-file`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `vaultUUID` | string | yes | |
| `itemUUID` | string | yes | |
| `fileUUID` | string | get-file only | |

Always send `inline_content=false` (omit or false).

### `operation = get-file-content`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `vaultUUID` | string | yes | |
| `itemUUID` | string | yes | |
| `fileUUID` | string | yes | |

### `operation = list-activity`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `limit` | number | no | Query `limit` |
| `offset` | number | no | Query `offset` |

## Output fields (by operation)

Errors: `{ status, message }` from Connect ErrorResponse — map to `{ error, http_status }`. Never include item fields from error bodies.

### `list-vaults` / `get-vault` (2xx)

Vault object: `id`, `name`, `description`, `attributeVersion`, `contentVersion`, `items`, `type` (`EVERYONE` \| `PERSONAL` \| `USER_CREATED`), `createdAt`, `updatedAt`. List: array of those.

### `list-items` (2xx)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `items` | array | 2xx | Summaries: `id`, `title`, `category`, `tags`, `vault.id`, `createdAt`, `updatedAt`, `version` — **no** `fields`, **no** `sections` content |

### `get-item` (2xx)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `id` | string | 2xx | |
| `title` | string | 2xx | |
| `category` | string | 2xx | |
| `tags` | string[] | if present | |
| `vault` | object | 2xx | `{ id }` only |
| `createdAt` | string | if present | |
| `updatedAt` | string | if present | |
| `version` | number | if present | |
| `urls` | array | if present | `href`/`label`/`primary` (URLs are not passwords; still minimize) |
| `sectionLabels` | array | if present | `{ id, label }` only |
| `fieldMeta` | array | if present | `{ id, type, purpose, label }` — **no** `value` |
| `secretRef` | string | 2xx | Handle for the fields map (values). **Do not** emit `fields` |

### `create-item` / `replace-item` / `patch-item` (2xx)

Same metadata as `get-item`. Include `id`. **Do not** echo `fields` values; omit `secretRef` unless the operator needs a round-trip (prefer omit).

### `delete-item` (2xx)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |

### `list-files` / `get-file` (2xx)

File object: `id`, `name`, `size`, `content_path`, `section` — **never** `content`.

### `get-file-content` (2xx)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `file` | file | 2xx | Platform ref-id for the downloaded bytes |
| `name` | string | if known | From Content-Disposition or prior metadata |
| `contentType` | string | if present | Response Content-Type |

Cap download size (default **8 MiB**). Over cap → `{ error }` without buffering the rest.

### `list-activity` (2xx)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `activity` | array | 2xx | APIRequest: `requestID`, `timestamp`, `action` (`READ`\|`CREATE`\|`UPDATE`\|`DELETE`), `result` (`SUCCESS`\|`DENY`), `actor` (`id`, `account`, `jti`, `userAgent`, `ip`), `resource` (`type`, `vault`, `item`, `itemVersion`) |

`ip` / `userAgent` may be personal data — emit as returned; operator is the controller.

## Legal

- API: https://developer.1password.com/docs/connect/api-reference
- Canonical also published at https://www.1password.dev/connect/api-reference
- Terms: https://1password.com/legal/terms-of-service
- API/SDK Terms: https://1password.com/legal/api-sdk-terms-of-service
- Rate limits: Connect is customer-hosted; no public cloud RPM. HTTP 401/403/404/413 (inline file too large).
- PII: item fields, file contents, activity IPs. Vault type `PERSONAL` is the Connect server’s private vault — still not a bulk personal-dump of all users.
- Official-API-first: Connect only; no 1password.com HTML.

## Implementation notes

- JDK HTTP. TLS required on `connectHost`. `Redirect.NEVER`.
- Timeouts: connect 10 s, request `timeout_seconds`. JSON body cap **1 MiB**. File download cap **8 MiB**.
- Retry: no inner loop. 429/5xx/timeout → `{ error, http_status, retry_after_seconds }` (30 default). 413 on inline files should not occur if `inline_content` is false.
- Never log `connectToken`, field values, file bytes.
- File upload: resolve `file` ref to bytes in-process; do not put bytes on OUTPUT.
