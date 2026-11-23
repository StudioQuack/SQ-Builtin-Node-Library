# Salesforce

- **Status:** planned
- **Catalog:** `docs/catalog/crm-marketing.md`
- **Folder when built:** `nodes/salesforce/`
- **Researched:** 2026-08-20
- **Official API:** Salesforce REST API **v67.0** (Summer ’26) + Bulk API 2.0 + Streaming API (CometD). Winter ’27 preview is v68.0 — pin **67.0** unless the org advertises a newer version via `GET /services/data`.
- **Docs:** https://developer.salesforce.com/docs/atlas.en-us.api_rest.meta/api_rest/resources_list.htm · https://developer.salesforce.com/docs/atlas.en-us.api_asynch.meta/api_asynch/asynch_api_intro.htm · https://developer.salesforce.com/docs/atlas.en-us.api_streaming.meta/api_streaming/intro_stream.htm · https://help.salesforce.com/s/articleView?id=sf.remoteaccess_oauth_jwt_flow.htm
- **Auth:** OAuth 2.0 (authorization code + refresh) or JWT bearer via **External Client App** — secret bindings: `client_secret`, `private_key_pem`, `refresh_token`

## Scope

One node for Salesforce CRM on the operator’s org (`login.salesforce.com` or `test.salesforce.com`). CRUD uses REST sObjects. Large loads use **Bulk API 2.0** (not Bulk 1.0). SOQL is `search.soql`. Listen uses **Streaming API (Bayeux/CometD over HTTPS)** so the handler stays on JDK HTTP. **Pub/Sub API is gRPC/HTTP2 — do not call it from this node.** New integrations must use **External Client Apps** (Connected App creation is disabled by default as of Spring ’26). Username-password OAuth is not supported on ECAs — do not implement it.

## Operations (`operation` config values)

REST base: `{instanceUrl}/services/data/v{apiVersion}` (default `67.0`).

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `account.create` / `.get` / `.list` / `.update` / `.delete` / `.upsert` | POST/GET/PATCH/DELETE | `/sobjects/Account` (+ `/{id}` or `/{extField}/{extId}`) | Account CRUD |
| `account.addNote` | POST | `/sobjects/Note` | `ParentId` = Account Id |
| `account.metadata` | GET | `/sobjects/Account/describe` | Field describe |
| `contact.*` | same pattern | `/sobjects/Contact` | Contact CRUD + `addNote` + `addToCampaign` (`CampaignMember`) |
| `lead.*` | same pattern | `/sobjects/Lead` | Lead CRUD + note + campaign |
| `opportunity.*` | same pattern | `/sobjects/Opportunity` | Opportunity CRUD + note |
| `case.*` | same pattern | `/sobjects/Case` | Case CRUD; `case.addComment` → `/sobjects/CaseComment` |
| `task.*` | same pattern | `/sobjects/Task` | Task CRUD |
| `attachment.create` / `.get` / `.list` / `.update` / `.delete` | POST/GET/PATCH/DELETE | `/sobjects/Attachment` | **Legacy** Attachment sObject |
| `customObject.*` | same pattern | `/sobjects/{sobject}` | Any custom/standard API name |
| `document.upload` | POST | `/sobjects/ContentVersion` | Files (preferred over Attachment) |
| `flow.list` | GET | `/actions/custom/flow` | Invocable flows |
| `flow.invoke` | POST | `/actions/custom/flow/{apiName}` | Run a flow |
| `search.soql` | GET | `/query?q=` | SOQL; follow `nextRecordsUrl` |
| `user.get` / `user.list` | GET | `/sobjects/User/{id}` · `/query?q=SELECT … FROM User` | Users |
| `bulk.ingest` | POST+PUT+GET | `/jobs/ingest` | Bulk 2.0 when ≥ ~2000 rows (used internally by list/upsert when `use_bulk=true`) |
| `trigger.listen` | POST (CometD) | `{instanceUrl}/cometd/{apiVersion}` | Subscribe to PushTopic / CDC / platform event |

Retired: SOAP `login()`, query-string session tokens, Bulk API 1.0 as the default path.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `account.get` | no | |
| `auth` | select | yes | `jwt` | no | `jwt` \| `oauth2` |
| `env` | select | yes | `prod` | no | `prod` → `login.salesforce.com`; `sandbox` → `test.salesforce.com` |
| `api_version` | string | no | `67.0` | no | Pin; re-read `GET /services/data` on implement day |
| `client_id` | string | yes | empty | no | ECA consumer key |
| `client_secret` | password | no | empty | yes | Needed for web-server OAuth refresh; JWT often does not need it |
| `username` | string | no | empty | no | JWT `sub` (integration user) |
| `private_key_pem` | password | no | empty | yes | JWT RSA private key. Never log |
| `refresh_token` | password | no | empty | yes | OAuth refresh |
| `instance_url` | string | no | empty | no | Override after first token; else from token response |
| `timeout_seconds` | number | no | `30` | no | SOQL/Bulk poll cap 120 |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object | `document.upload` / attachment bodies use type `file` |
| `output` | OUTPUT | `{ status, … }` or `{ error }` | Never echo access tokens or PEM |

FUNCTION ports: none.

## Input fields (by operation)

### Shared sObject CRUD (`*.create` / `*.update` / `*.upsert` / `*.get` / `*.list` / `*.delete`)

Applies to Account, Contact, Lead, Opportunity, Case, Task, Attachment, customObject (set `sobject`).

| Field | Type | Required | Purpose |
|---|---|---|---|
| `id` | string | get/update/delete | Salesforce Id |
| `external_id_field` | string | upsert | External Id field API name |
| `external_id` | string | upsert | Match value |
| `soql_where` | string | list | Optional WHERE (no `SELECT`); handler wraps `SELECT Id, … FROM {sobject}` |
| `sobject` | string | customObject.* | e.g. `MyThing__c` |
| `use_bulk` | boolean | upsert/list | If true or row count ≥ 2000, Bulk 2.0 |

### `*.addNote`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `parent_id` | string | yes | Account/Contact/Lead/Opportunity Id |
| `title` | string | yes | Note title |
| `body` | string | yes | Note body (PII) |

### `contact.addToCampaign` / `lead.addToCampaign`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `id` | string | yes | Contact or Lead Id |
| `campaign_id` | string | yes | Campaign Id |
| `status` | string | no | CampaignMember.Status |

### `case.addComment`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `id` | string | yes | Case Id |
| `comment_body` | string | yes | |

### `document.upload`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `file` | file | yes | Platform ref-id; never inline bytes |
| `title` | string | no | ContentVersion.Title |
| `path_on_client` | string | no | Filename |
| `linked_entity_id` | string | no | After insert, create `ContentDocumentLink` |

### `search.soql`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `q` | string | yes | Full SOQL. Reject DML-looking strings. Cap length **16_000** chars |
| `next_records_url` | string | no | Relative URL from prior page (`/services/data/v67.0/query/…`) |

### `flow.list` / `flow.invoke`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `api_name` | string | invoke | Flow API name |
| `inputs` | array/object | invoke | Official `inputs` body |

### `user.get` / `user.list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `id` | string | get | User Id |

### `trigger.listen`

`trigger.listen` is ingress (Streaming / CDC). It does not take INPUT `channel`, `replay_id`, or `limit`. Use a BPMN message event; StudioQuack webhook ingress correlates onto those events. Do not implement gRPC Pub/Sub.

## Output fields (by operation)

### CRUD / note / campaign / comment / customObject / user

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `id` | string | create/upsert/get | Record Id |
| `success` | boolean | create | REST `success` |
| `record` | object | get | sObject fields (PII — do not log) |
| `records` | array | list/query | |
| `total_size` | number | query | |
| `next_records_url` | string | query | |
| `errors` | array | 400 | Salesforce `errorCode` + `message` (no tokens) |
| `error` | string | failure | |
| `http_status` | number | HTTP error | |
| `retry_after_seconds` | number | 429 | |

### `document.upload` / `attachment.get`

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `id` | string | upload | ContentVersion or Attachment Id |
| `content_document_id` | string | upload | If returned |
| `file` | file | attachment.get | Platform ref-id of downloaded bytes (size-capped) |

### `search.soql` / `flow.*` / `trigger.listen`

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `records` | array | SOQL | |
| `done` | boolean | SOQL | |
| `flow_result` | object | invoke | Action response |
| `flows` | array | list | |
| `events` | array | listen | CometD messages (strip replay secrets) |
| `error` | string | failure | |

## Legal

- Salesforce Master Subscription Agreement + API terms: https://www.salesforce.com/company/legal/agreements/
- Developer docs: https://developer.salesforce.com/docs/atlas.en-us.api_rest.meta/api_rest/intro_rest.htm
- Daily API request limits are **edition- and license-count dependent**. Honor `Sforce-Limit-Info` and HTTP 403 `REQUEST_LIMIT_EXCEEDED`.
- CRM records are **PII**. Customer owns the org. Minimize selected fields in SOQL. Never log tokens, PEM, emails, or record bodies.
- Connected Apps are deprecated for **new** inbound apps; use External Client Apps. JWT bearer: pre-authorize the integration user; ECAs do not support username-password.

## Implementation notes

- JDK HTTP only. No Salesforce SDK / no gRPC.
- Token: `POST https://{login|test}.salesforce.com/services/oauth2/token`. JWT: `grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer` + `assertion`. Cache access token in memory for `expires_in − 60s`. On 401, refresh once then `{ error }`.
- All subsequent calls: `Authorization: Bearer {access_token}` against `instance_url` from the token response (My Domain). Do not follow arbitrary redirects off `*.salesforce.com` / `*.force.com`.
- SOQL: URL-encode `q`. Cap response **5_000_000** bytes. Follow `next_records_url` only if it is a path on the same `instance_url`.
- Bulk 2.0: `POST /jobs/ingest` → upload CSV → `UploadComplete` → poll `GET /jobs/ingest/{id}` until JobComplete/Failed. Timeout poll **120 s** default. Result files as `file` type if the operator asked for failed-row CSV.
- Attachment Body / ContentVersion VersionData: read/write via `file` ref-id. Cap download **20_000_000** bytes.
- Retry: 429/5xx up to 2 times. Do not retry 400 `MALFORMED_QUERY`.
- Timeouts: CRUD 30 s; query 60 s; bulk poll 120 s.
- Errors: map Salesforce JSON `message` to `{ error }` on OUTPUT. Never throw past dispatch.
- **Streaming API:** official intro https://developer.salesforce.com/docs/atlas.en-us.api_streaming.meta/api_streaming/intro_stream.htm (fetch of this URL timed out on 2026-08-20; existence confirmed via Salesforce developers search / Bulk guide sibling). Handshake is Bayeux `POST {instanceUrl}/cometd/{apiVersion}`. Exact subscribe JSON **re-copy from that guide** on implement day. Pub/Sub gRPC remains out of scope.
