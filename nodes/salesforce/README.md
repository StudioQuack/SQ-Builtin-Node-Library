# Salesforce

Official Salesforce REST API **v67.0** (Summer ’26). Handler: `OfficialApiNode` via `api_spec.json`. Customer owns the Salesforce org.

- **Docs:** https://developer.salesforce.com/docs/atlas.en-us.api_rest.meta/api_rest/sforce_rest_api.htm · resources https://developer.salesforce.com/docs/atlas.en-us.api_rest.meta/api_rest/resources_list.htm
- **API (2026-08-20):** REST **v67.0**. Winter ’27 preview is v68.0 — pin 67.0 unless the org advertises a newer version via `GET /services/data`.
- **Base:** `https://{instance_url}/services/data/v67.0` — config `instance_url` is the My Domain host only (e.g. `mycompany.my.salesforce.com`), from Setup → My Domain or the token response host. Do not call `login.salesforce.com` for sObject CRUD after you have a token.
- **Auth:** Customer supplies a current `access_token` as Bearer. Obtain it outside this node via OAuth 2.0 (authorization code + refresh) or JWT bearer on an **External Client App**. This node does not mint JWTs or store refresh tokens.

`trigger.listen` is `kind: ingress` (Streaming API / CDC / platform events). It does **not** open CometD inside the node. Use a BPMN message start / catch / boundary event; StudioQuack webhook ingress correlates onto those events. Do not call Pub/Sub API (gRPC) from this node.

`*.list` and `search.soql` are `GET /query?q=` — pass SOQL in `q` (URL-encoded by the handler). Cap `q` at 16_000 characters. Minimize SELECT lists (CRM records are PII).

Create/update/upsert send named sObject fields (`Name`, `FirstName`, `LastName`, `Email`, `Company`, `StageName`, `CloseDate`, …). `customObject.*` merge INPUT `fields` at the JSON root (API name → value). `document.upload` requires `Title`, `PathOnClient`, `VersionData` (base64). `*.addNote`: `ParentId`, `Title`, `Body`. Campaign members: `ContactId` or `LeadId` plus `CampaignId`. Case comments: `ParentId`, `CommentBody`.

## Operations

CRUD on `/sobjects/Account`, `/sobjects/Contact`, `/sobjects/Lead`, `/sobjects/Opportunity`, `/sobjects/Case`, `/sobjects/Task`, `/sobjects/Attachment`, and `/sobjects/{sobject}` (custom). Notes → `POST /sobjects/Note`. Campaign members → `POST /sobjects/CampaignMember`. Case comments → `POST /sobjects/CaseComment`. Files → `POST /sobjects/ContentVersion`. Flows → `/actions/custom/flow`. Users → `/sobjects/User/{id}`.

| operation | HTTP | Path |
|---|---|---|
| `account.create` | POST | `/sobjects/Account` |
| `account.upsert` | PATCH | `/sobjects/Account/{external_id_field}/{external_id}` |
| `account.get` | GET | `/sobjects/Account/{id}` |
| `account.list` | GET | `/query?q=` |
| `account.update` | PATCH | `/sobjects/Account/{id}` |
| `account.delete` | DELETE | `/sobjects/Account/{id}` |
| `account.addNote` | POST | `/sobjects/Note` |
| `account.metadata` | GET | `/sobjects/Account/describe` |
| `contact.*` / `lead.*` / `opportunity.*` | same pattern | matching sObject |
| `case.addComment` | POST | `/sobjects/CaseComment` |
| `document.upload` | POST | `/sobjects/ContentVersion` |
| `flow.list` / `flow.invoke` | GET / POST | `/actions/custom/flow` |
| `search.soql` | GET | `/query?q=` |
| `user.get` / `user.list` | GET | `/sobjects/User/{id}` · `/query` |
| `trigger.listen` | ingress | Streaming / CDC — BPMN message event |

## Config

| Name | Type | Secret | Purpose |
|---|---|---|---|
| `operation` | select | no | Default verb |
| `access_token` | password | yes | Bearer session token. Never log or place on INPUT |
| `instance_url` | string | no | My Domain host (no scheme) for `{instance_url}` |
| `timeout_seconds` | number | no | Default 30 |

## Ports

`input` / `output` only. Errors raise a BPMN error for the error boundary event (INVALID_INPUT, AUTH_FAILED, RATE_LIMITED, HTTP_<status>, REQUEST_FAILED); secrets are redacted.

## Legal

- Customer is the controller of CRM records. Minimize selected fields in SOQL.
- Master Subscription Agreement + API terms: https://www.salesforce.com/company/legal/agreements/
- Official REST only. Daily API limits are edition- and license-dependent; honor `Sforce-Limit-Info`.
