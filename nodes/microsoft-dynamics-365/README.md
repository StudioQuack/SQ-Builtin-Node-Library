# Microsoft Dynamics 365

Official **Microsoft Dataverse Web API v9.2** (OData 4.0) via `OfficialApiNode` / `api_spec.json`. Dynamics 365 **Sales** tables only (`accounts`, `contacts`, `leads`, `opportunities`, `incidents`, activities) plus `WhoAmI`, FetchXML, and ingress. Not Finance & Operations. **Not** Account Engagement (see `pardot`).

- **Docs:** https://learn.microsoft.com/en-us/power-apps/developer/data-platform/webapi/overview · versions https://learn.microsoft.com/en-us/power-apps/developer/data-platform/webapi/web-api-versions · auth https://learn.microsoft.com/en-us/power-apps/developer/data-platform/webapi/authenticate-web-api
- **Host pattern:** `https://{org_host}/api/data/v9.2` where `org_host` is hostname only, e.g. `contoso.crm.dynamics.com` or `contoso.api.crm.dynamics.com` (plus published sovereign clouds). HTTPS only. No IP literals.
- **Auth:** Bearer `access_token` (scope `{orgUrl}/.default`). Customer owns the Dataverse environment and Dynamics license.

## Operations

| operation | HTTP | Path |
|---|---|---|
| `account.*` / `contact.*` / `lead.*` / `opportunity.*` / `incident.*` | CRUD | `/{entity}({id})` |
| `lead.convert` | POST | `/leads({id})/Microsoft.Dynamics.CRM.QualifyLead` |
| `activity.create` | POST | `/{activity_set}` (`tasks` \| `emails` \| `phonecalls` \| `appointments`) |
| `whoami` | GET | `/WhoAmI` |
| `fetchXml.query` | GET | `/{entity_set}?fetchXml=` |
| `trigger.listen` | ingress | BPMN message event |

Spec `baseUrl` is `https://{org_host}/api/data/{api_version}` (default `v9.2`).

## Config

| Name | Type | Secret | Purpose |
|---|---|---|---|
| `operation` | select | no | Default `whoami` |
| `access_token` | password | yes | Bearer (minted outside StudioQuack) |
| `org_host` | string | no | Hostname only (documented pattern above) |
| `api_version` | string | no | Interpolated into baseUrl (default `v9.2`) |
| `timeout_seconds` | number | no | Default 30 |

## Ports

`input` / `output` only. Failures: `{status,http_status,data}` — never includes Bearer or record payloads.

OData query keys on INPUT: `$select`, `$filter`, `$orderby`, `$top`. FetchXML: send `fetchXml` (the Dataverse query parameter name).

## Legal

- Customer owns the Dataverse environment. CRM records are PII. Minimize `$select`.
- Microsoft APIs Terms + Online Services terms apply to the customer tenant.
- Webhook ingress (BPMN) must reject mismatched auth; env API key is separate.

## OfficialApiNode notes

- Sends `OData-MaxVersion: 4.0`, `OData-Version: 4.0`, `Accept: application/json`, `Prefer: return=representation`.
- Create/update bodyFrom is official logical names at the JSON root (`name`, `emailaddress1`, `firstname`, …). Lookups use the official `@odata.bind` shortCodes (e.g. `uma.s@example.org`). `fields` remains an escape-hatch key (do not combine with purpose fields).
- `trigger.listen` does not POST `serviceendpoints`. Use a BPMN message event.
- `next_link` paging is not followed inside one dispatch.
