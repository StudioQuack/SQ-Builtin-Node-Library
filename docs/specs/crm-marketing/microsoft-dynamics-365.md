# Microsoft Dynamics 365

- **Status:** planned
- **Catalog:** `docs/catalog/crm-marketing.md`
- **Folder when built:** `nodes/microsoft-dynamics-365/`
- **Researched:** 2026-08-20
- **Official API:** Microsoft Dataverse Web API **v9.2** (OData 4.0). v9.0 / v9.1 / v9.2 share behavior; pin `v9.2`.
- **Docs:** https://learn.microsoft.com/en-us/power-apps/developer/data-platform/webapi/overview · versions https://learn.microsoft.com/en-us/power-apps/developer/data-platform/webapi/web-api-versions · auth https://learn.microsoft.com/en-us/power-apps/developer/data-platform/webapi/authenticate-web-api · OAuth https://learn.microsoft.com/en-us/power-apps/developer/data-platform/authenticate-oauth · webhooks https://learn.microsoft.com/en-us/power-apps/developer/data-platform/register-web-hook · WhoAmI https://learn.microsoft.com/en-us/power-apps/developer/data-platform/webapi/reference/whoami
- **Auth:** Bearer `access_token` minted outside StudioQuack. Resource = `{orgUrl}`.

## Scope

One node for **Dynamics 365 Sales** tables on Dataverse (`accounts`, `contacts`, `leads`, `opportunities`, `incidents`, activities) plus `WhoAmI`, FetchXML, and webhook registration (`serviceendpoints` contract 8). Customer supplies `orgUrl` (e.g. `https://contoso.crm.dynamics.com` or `https://contoso.api.crm.dynamics.com`). Requires an Azure app registration **and** a Dynamics / Dataverse license on the calling identity.

Do not call the Organization SOAP service. Do not scrape the web client. Do not add Finance & Operations or Marketing (outbound) tables here.

## Operations (`operation` config values)

Base: `{orgUrl}/api/data/v9.2` (trim trailing slash). Headers: `Authorization: Bearer`, `OData-MaxVersion: 4.0`, `OData-Version: 4.0`, `Accept: application/json`, `Content-Type: application/json; charset=utf-8`.

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `account.create` | POST | `/accounts` | Create account |
| `account.get` | GET | `/accounts({id})` | Retrieve |
| `account.list` | GET | `/accounts` | `$select`, `$filter`, `$top`, paging |
| `account.update` | PATCH | `/accounts({id})` | Update selected columns |
| `account.delete` | DELETE | `/accounts({id})` | Delete |
| `contact.create` | POST | `/contacts` | Create contact |
| `contact.get` | GET | `/contacts({id})` | Retrieve |
| `contact.list` | GET | `/contacts` | List/filter |
| `contact.update` | PATCH | `/contacts({id})` | Update |
| `contact.delete` | DELETE | `/contacts({id})` | Delete |
| `lead.create` | POST | `/leads` | Create lead |
| `lead.get` | GET | `/leads({id})` | Retrieve |
| `lead.list` | GET | `/leads` | List/filter |
| `lead.update` | PATCH | `/leads({id})` | Update |
| `lead.convert` | POST | `/leads({id})/Microsoft.Dynamics.CRM.QualifyLead` | Qualify lead (account/contact/opportunity). Action listed on [lead entity](https://learn.microsoft.com/en-us/dynamics365/developer/reference/entities/lead); Web API name `QualifyLead` per [QualifyLeadRequest](https://learn.microsoft.com/en-us/dotnet/api/microsoft.crm.sdk.messages.qualifyleadrequest). Dedicated `qualifylead` HTML 404'd 2026-08-20 |
| `opportunity.create` | POST | `/opportunities` | Create |
| `opportunity.get` | GET | `/opportunities({id})` | Retrieve |
| `opportunity.list` | GET | `/opportunities` | List |
| `opportunity.update` | PATCH | `/opportunities({id})` | Update |
| `opportunity.delete` | DELETE | `/opportunities({id})` | Delete |
| `incident.create` | POST | `/incidents` | Case |
| `incident.get` | GET | `/incidents({id})` | Retrieve |
| `incident.list` | GET | `/incidents` | List |
| `incident.update` | PATCH | `/incidents({id})` | Update |
| `activity.create` | POST | `/{set}` | `task` / `email` / `phonecall` / `appointment` via `activity_set` |
| `whoami` | GET | `/WhoAmI` | Calling user / org ids |
| `fetchXml.query` | GET | `/{entitySet}?fetchXml=` | Complex query (URL-encoded FetchXML) |
| `trigger.listen` | POST | `/serviceendpoints` (+ step) | Register webhook (contract 8) to ingress URL |

`Prefer: return=representation` on POST/PATCH to echo the record. List: `Prefer: odata.maxpagesize={n}` (max 5000; default 200 in this node).

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `whoami` | no | Chooses the row above |
| `org_host` | string | yes | empty | no | Hostname only. Interpolated into `https://{org_host}/api/data/{api_version}` |
| `api_version` | string | no | `v9.2` | no | Path segment interpolated into baseUrl |
| `access_token` | password | yes | empty | yes | Bearer minted outside StudioQuack. Scope `{orgUrl}/.default` |
| `timeout_seconds` | number | no | `30` | no | Floor 1 s |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | No secrets |
| `output` | OUTPUT | `{ status, … }` or `{ error }` | OData errors mapped; no token |

FUNCTION ports: none.

## Input fields (by operation)

### `*.create` (account / contact / lead / opportunity / incident)

| Field | Type | Required | Purpose |
|---|---|---|---|
| `fields` | object | yes | Column map (`name`, `emailaddress1`, `subject`, …). Unknown keys passed through if they look like logical names (`^[a-z][a-z0-9_]*$`) |

### `*.get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `id` | string | yes | GUID, braces optional |
| `select` | string | no | `$select` columns |

### `*.list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `select` | string | no | `$select` |
| `filter` | string | no | `$filter` OData. Reject `$expand` loops; cap length 2000 |
| `orderby` | string | no | `$orderby` |
| `top` | number | no | `$top` 1–5000 |
| `next_link` | string | no | Must start with `{orgUrl}/api/data/` — next page only |

### `*.update`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `id` | string | yes | GUID |
| `fields` | object | yes | Patch columns. `If-Match: *` unless `etag` set |
| `etag` | string | no | Optimistic concurrency |

### `*.delete`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `id` | string | yes | GUID |

### `lead.convert`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `id` | string | yes | Lead GUID |
| `create_account` | boolean | no | `CreateAccount` (default false) |
| `create_contact` | boolean | no | `CreateContact` (default true) |
| `create_opportunity` | boolean | no | `CreateOpportunity` (default true) |
| `status` | number | no | Qualify statuscode; default `3` |

### `activity.create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `activity_set` | select | yes | `tasks` \| `emails` \| `phonecalls` \| `appointments` |
| `fields` | object | yes | Activity columns + bindings (`regardingobjectid_lead@odata.bind`) |

### `whoami`

No input fields.

### `fetchXml.query`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `entity_set` | string | yes | Entity-set name (`accounts`, `leads`, …) |
| `fetchXml` | string | yes | FetchXML document. Size cap 100_000 chars. Must start with `<fetch` |

### `trigger.listen`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `name` | string | yes | ServiceEndpoint name |
| `url` | string | yes | HTTPS ingress URL |
| `auth_type` | select | no | `HttpHeader` \| `WebhookKey` \| `HttpQueryString` (Dataverse AuthType) |
| `auth_value` | password | no | Matches AuthType (header pair / code). Secret |
| `message` | string | no | SDK message e.g. `Create` |
| `primary_entity` | string | no | Logical name e.g. `lead` |

Implementation: POST `serviceendpoints` with `contract=8`, then POST `sdkmessageprocessingsteps` bound to that endpoint. If step fields omitted, register endpoint only and return ids for the operator to finish in Plugin Registration.

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `id` | string | create/get/update | Entity GUID (`accountid`, …) |
| `record` | object | get/create(representation) | Selected columns only |
| `records` | array | list / fetchXml | Page of rows |
| `next_link` | string | more pages | Opaque next URL |
| `user_id` / `business_unit_id` / `organization_id` | string | `whoami` | WhoAmIResponse |
| `created_records` | array | `lead.convert` | QualifyLead entity references |
| `service_endpoint_id` | string | `trigger.listen` | Webhook registration |
| `error` | string | failure | OData `error.message`; no token |
| `http_status` | number | HTTP error | |
| `retry_after_seconds` | number | 429 / 5xx | 60 / 30 |

## Legal

- Customer owns the Dataverse environment and Dynamics license. StudioQuack calls as the operator’s Azure app.
- CRM records are PII. Minimize `$select`. Never log emails, names, or FetchXML that embeds PII.
- Microsoft APIs Terms + Online Services terms apply to the customer tenant.
- Webhooks: endpoint must reject mismatched auth. Ingress uses env API key separately.

## Implementation notes

- JDK `HttpClient` only. No Dataverse SDK.
- Validate `org_url` is HTTPS and host matches `*.dynamics.com` / `*.crm*.dynamics.com` / `*.api.crm*.dynamics.com` (allow documented sovereign clouds: `crmN.microsoftdynamics.de`, `.dynamics.cn`, etc. as published). Reject IP literals.
- DELETE/PATCH 204 → `{ status: ok, id }`.
- Duplicate detection: optional header `MSCRM.SuppressDuplicateDetection: true` only when input `suppress_duplicate_detection=true`.
- FetchXML: URL-encode; add `Prefer: odata.include-annotations="Microsoft.Dynamics.CRM.fetchxmlpagingcookie,Microsoft.Dynamics.CRM.morerecords"`; do not follow all pages in one run.
- `lead.convert` body: `{ "CreateAccount": bool, "CreateContact": bool, "CreateOpportunity": bool, "Status": 3 }` (Status 3 = qualified). Use `Prefer: return=representation` if available.
- Body cap **1_000_000** bytes. Timeout 30 s.
- Logs: operation, entity set, HTTP status, record counts — never Bearer or record payloads.
