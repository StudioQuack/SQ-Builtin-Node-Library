# Microsoft Graph Security

Official **Microsoft Graph REST v1.0** Secure Score APIs. Handler: `OfficialApiNode` via `api_spec.json`. Customer owns the Entra tenant and its Secure Score.

- **Docs:** https://learn.microsoft.com/en-us/graph/api/resources/security-api-overview?view=graph-rest-1.0
- **Scores:** https://learn.microsoft.com/en-us/graph/api/security-list-securescores?view=graph-rest-1.0
- **Control profiles:** https://learn.microsoft.com/en-us/graph/api/security-list-securescorecontrolprofiles?view=graph-rest-1.0
- **Base:** `https://graph.microsoft.com/v1.0`
- **Auth:** `Authorization: Bearer {access_token}`. Never log `access_token`.

Secure Score only. This package does not call alerts, incidents, hunting, or Graph **beta**.

These five methods are **not** available in China operated by 21Vianet. US Government L4/L5 are documented as supported; this package still uses the commercial Graph host.

## Operations

| operation | HTTP | Path |
|---|---|---|
| `get-secure-score` | GET | `/security/secureScores/{id}` |
| `list-secure-scores` | GET | `/security/secureScores` |
| `get-control-profile` | GET | `/security/secureScoreControlProfiles/{id}` |
| `list-control-profiles` | GET | `/security/secureScoreControlProfiles` |
| `update-control-profile` | PATCH | `/security/secureScoreControlProfiles/{id}` |

`update-control-profile` **must** send `vendorInformation` with `provider` and `vendor` (typical: `SecureScore` / `Microsoft`).

Permissions: `SecurityEvents.Read.All` (reads). `SecurityEvents.ReadWrite.All` (update). Personal Microsoft accounts are not supported.

## Config

| Name | Type | Secret | Purpose |
|---|---|---|---|
| `operation` | select | no | Default verb |
| `access_token` | password | yes | Graph Bearer token |
| `timeout_seconds` | number | no | Default 25 |

## Ports

`input` / `output` only. Failures: `{ error }` — never includes `access_token`.

## Legal

- Microsoft APIs Terms of Use: https://learn.microsoft.com/legal/microsoft-apis/terms-of-use
- Online Services terms: https://www.microsoft.com/licensing/terms
- Secure Score and control notes are tenant-sensitive. The customer is the controller. StudioQuack does not accept Microsoft’s terms on the customer’s behalf.
- Official Graph v1.0 only. Do not call `api.security.microsoft.com` (retired hunting host) or scrape portals.

Never log `access_token` or wholesale score payloads in application logs.
