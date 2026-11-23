# Microsoft Graph Security

- **Status:** planned
- **Catalog:** `docs/catalog/security-identity.md`
- **Folder when built:** `nodes/microsoft-graph-security/`
- **Researched:** 2026-08-20
- **Official API:** Microsoft Graph REST **v1.0** — Security `secureScore` / `secureScoreControlProfile`
- **Docs:** https://learn.microsoft.com/en-us/graph/api/resources/security-api-overview?view=graph-rest-1.0
- **Auth:** `oauth2` (delegated) or `entra_app_only` (client credentials) — secret bindings: `client_secret`, `refresh_token` (delegated only)

## Scope

Calls Microsoft Graph **v1.0** Secure Score APIs for the operator’s Entra tenant: list/get scores and list/get/update control profiles. Do **not** call Graph **beta** for these five catalog operations — all exist on v1.0.

This node is **Secure Score only**. Do not add alerts, incidents, hunting, or threat indicators in v1.

**National clouds (verified 2026-08-20):** Microsoft documents **all five** of these methods as unavailable in **China operated by 21Vianet**. If `graph_base_url` is the China Graph root, skip HTTP and return `{ error }` (do not probe). US Government L4/L5 are supported.

**DO NOT BUILD:** Graph `/beta` Secure Score paths; Microsoft Graph change-notification `listen` (not in this catalog row); `runHuntingQuery`; calling `api.security.microsoft.com` (retired hunting host).

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `get-secure-score` | GET | `/v1.0/security/secureScores/{id}` | One score snapshot by id |
| `list-secure-scores` | GET | `/v1.0/security/secureScores` | Tenant score history (`$top` / `$skip` / `$filter`) |
| `get-control-profile` | GET | `/v1.0/security/secureScoreControlProfiles/{id}` | One control profile |
| `list-control-profiles` | GET | `/v1.0/security/secureScoreControlProfiles` | All control profiles (`$top` / `$filter`) |
| `update-control-profile` | PATCH | `/v1.0/security/secureScoreControlProfiles/{id}` | Update tenant note / control state. **Requires** `vendorInformation.provider` + `vendor` |

Canonical method docs:

- List scores: https://learn.microsoft.com/en-us/graph/api/security-list-securescores?view=graph-rest-1.0
- Get score: https://learn.microsoft.com/en-us/graph/api/securescore-get?view=graph-rest-1.0
- List profiles: https://learn.microsoft.com/en-us/graph/api/security-list-securescorecontrolprofiles?view=graph-rest-1.0
- Get profile: https://learn.microsoft.com/en-us/graph/api/securescorecontrolprofile-get?view=graph-rest-1.0
- Update profile: https://learn.microsoft.com/en-us/graph/api/securescorecontrolprofile-update?view=graph-rest-1.0
- National-cloud roots: https://learn.microsoft.com/en-us/graph/deployments

### National-cloud matrix (Microsoft Learn, v1.0)

| Method | Global | US Gov L4 | US Gov L5 (DoD) | China (21Vianet) |
|---|---|---|---|---|
| List/Get `secureScores` | yes | yes | yes | **no** |
| List/Get/Update `secureScoreControlProfiles` | yes | yes | yes | **no** |

Graph service roots:

| Cloud | `graph_base_url` | Token host |
|---|---|---|
| Global (and M365 GCC) | `https://graph.microsoft.com` | `https://login.microsoftonline.com` |
| US Gov L4 (GCC High) | `https://graph.microsoft.us` | `https://login.microsoftonline.us` |
| US Gov L5 (DoD) | `https://dod-graph.microsoft.us` | `https://login.microsoftonline.us` |
| China 21Vianet | `https://microsoftgraph.chinacloudapi.cn` | `https://login.chinacloudapi.cn` |

Tokens are **not** interchangeable across clouds. `.default` resource URI must match the Graph host (e.g. `https://graph.microsoft.com/.default`).

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `list-secure-scores` | no | Chooses the row above |
| `auth_type` | select | yes | `entra_app_only` | no | `oauth2` (delegated refresh) or `entra_app_only` (application) |
| `tenant_id` | string | yes | empty | no | Entra tenant GUID or domain |
| `client_id` | string | yes | empty | no | App registration id |
| `client_secret` | password | yes | empty | yes | App secret. Never on INPUT, never logged |
| `refresh_token` | password | if `oauth2` | empty | yes | Delegated refresh token. Never on INPUT, never logged |
| `graph_base_url` | string | no | `https://graph.microsoft.com` | no | Graph origin **without** `/v1.0`. No trailing slash |
| `timeout_seconds` | number | no | `25` | no | Connect + request timeout. Floor 1 s |

Permissions (admin consent). Reads: `SecurityEvents.Read.All`. `update-control-profile`: `SecurityEvents.ReadWrite.All`. Personal Microsoft accounts are **not** supported.

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | No tokens |
| `output` | OUTPUT | `{ status, … }` or `{ error }` | Failures never include secrets |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = get-secure-score`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `id` | string | yes | Score id (example shape `…c_2019-03-19`). Empty → `{ error }`, no HTTP |

### `operation = list-secure-scores`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `top` | number | no | `$top` (Microsoft: aggregated top from each provider). Clamp 1–999 |
| `skip` | number | no | `$skip` ≥ 0 |
| `filter` | string | no | OData `$filter` as documented. Do not URL-decode twice |

### `operation = get-control-profile`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `id` | string | yes | Control profile id (e.g. `DLPEnabled`) |

### `operation = list-control-profiles`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `top` | number | no | `$top`. Clamp 1–999 |
| `skip` | number | no | `$skip` ≥ 0 |
| `filter` | string | no | OData `$filter` |

### `operation = update-control-profile`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `id` | string | yes | Control profile id |
| `vendor_information` | object | yes | Must include `provider` and `vendor` (Microsoft examples: `SecureScore` / `Microsoft`) |
| `assigned_to` | string | no | Control assignment |
| `comment` | string | no | Tenant comment / note |
| `state` | string | no | Control state as documented (`Ignored`, `ThirdParty`, `Reviewed`, …) |
| `return_representation` | boolean | no | If true, send `Prefer: return=representation` (200 + body). Else expect **204** |

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx or 206 | `ok` (treat 206 as success with `warning`) |
| `secure_score` | object | get-secure-score 2xx/206 | `id`, `azureTenantId`, `createdDateTime`, `currentScore`, `maxScore`, `activeUserCount`, `licensedUserCount`, `enabledServices`, `averageComparativeScores`, `controlScores`, `vendorInformation` |
| `secure_scores` | array | list-secure-scores 2xx | Graph `value` array (same object shape) |
| `control_profile` | object | get/update (200) | Profile including `controlStateUpdates`, `maxScore`, `title`, `deprecated`, `threats` |
| `control_profiles` | array | list-control-profiles 2xx | Graph `value` array |
| `@odata.nextLink` | string | list + paging | Opaque next page URL — do not rewrite host |
| `warning` | string | 206 | Provider warning header / partial content (Graph Security error model) |
| `error` | string | failure | No token, no secret |
| `http_status` | number | HTTP error | Provider status |
| `retry_after_seconds` | number | 429 / 5xx / timeout | From `Retry-After` if present, else 30 |

## Legal

- Graph Security overview: https://learn.microsoft.com/en-us/graph/api/resources/security-api-overview?view=graph-rest-1.0
- Microsoft APIs Terms of Use: https://learn.microsoft.com/legal/microsoft-apis/terms-of-use
- Permissions: `SecurityEvents.Read.All` / `SecurityEvents.ReadWrite.All` — https://learn.microsoft.com/en-us/graph/permissions-reference
- Throttling: https://learn.microsoft.com/en-us/graph/throttling-limits — honor `Retry-After`; no inner retry loop
- Scores and control profiles are **tenant security posture**. Treat as confidential. Do not log bodies.
- China (21Vianet): these APIs are **unsupported** — fail closed.

## Implementation notes

- JDK HTTP only. No Microsoft Graph SDK.
- Token: POST `{login_host}/{tenant_id}/oauth2/v2.0/token`. App-only: `grant_type=client_credentials`, `scope={graph_host}/.default`. Delegated: `grant_type=refresh_token` + `refresh_token`. **Never log** the token response or `client_secret`.
- Requests: `Authorization: Bearer {access_token}`, `Accept: application/json`. PATCH: `Content-Type: application/json`.
- TLS verified. `Redirect.NEVER`. Timeout 25 s. Response body cap 2 MiB.
- China `graph_base_url` → `{ error: "Secure Score APIs are not available in Microsoft Graph China (21Vianet)." }` without calling Graph.
- 401 after token: `{ error }` with `http_status`. 429: surface `retry_after_seconds`. 4xx/5xx → OUTPUT `{ error }`, never throw past dispatch.
- 206: `status=ok` plus `warning`; still never log the body.
