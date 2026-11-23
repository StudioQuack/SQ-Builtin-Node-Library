# Pardot (Account Engagement)

- **Status:** planned
- **Catalog:** `docs/catalog/crm-marketing.md`
- **Folder when built:** `nodes/pardot/`
- **Researched:** 2026-08-20
- **Official API:** Salesforce **Account Engagement** (Pardot) REST. **Version 5 is preferred** (works with and without AMPSEA). v4 remains for AMPSEA-era path syntax; v3 is non-AMPSEA only. This node **defaults to v5**.
- **Docs:** https://developer.salesforce.com/docs/marketing/pardot/guide/overview.html · getting started https://developer.salesforce.com/docs/marketing/pardot/guide/getting-started.html · v5 transition https://developer.salesforce.com/docs/marketing/pardot/guide/transitioning-v5.html · prospects https://developer.salesforce.com/docs/marketing/pardot/guide/prospect-v5.html · list memberships https://developer.salesforce.com/docs/marketing/pardot/guide/list-membership-v5.html · emails https://developer.salesforce.com/docs/marketing/pardot/guide/email-v5.html · visitors https://developer.salesforce.com/docs/marketing/pardot/guide/visitor-v5.html · forms https://developer.salesforce.com/docs/marketing/pardot/guide/form-v5.html · auth errors https://developer.salesforce.com/docs/marketing/pardot/guide/error-codes
- **Auth:** Salesforce Bearer `access_token` minted outside StudioQuack (`pardot_api` scope) **plus** header `Pardot-Business-Unit-Id`. **Not** the Salesforce CRM node.

## Scope

One node for Account Engagement **prospects, lists, emails, visitors, forms, campaigns**. Separate from `salesforce` CRM. Every request: `Authorization: Bearer {access_token}` and `Pardot-Business-Unit-Id: {business_unit_id}`.

Hosts: production/training `https://pi.pardot.com` · sandbox/demo/dev `https://pi.demo.pardot.com`. Confirm via Setup → Installed Packages → `b2bmaIntegration` namespace (`b2bma` → prod, `pi3` → demo).

Do not call Salesforce REST `/sobjects/Account`. Do not scrape pi.pardot.com UI.

## Operations (`operation` config values)

v5 base: `{host}/api/v5`. Query string `fields=` required on many reads (default `id,email` if omitted on prospects).

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `prospect.create` | POST | `/objects/prospects` | Create prospect (`email` required) |
| `prospect.upsert` | POST | `/objects/prospects/do/upsertLatestByEmail` | Upsert most recently updated prospect by email |
| `prospect.get` | GET | `/objects/prospects/{id}?fields=` | Read by AE id (not email on v5 update/delete) |
| `prospect.update` | PATCH | `/objects/prospects/{id}` | Update by id |
| `prospect.delete` | DELETE | `/objects/prospects/{id}` | Delete by id |
| `list.membership.add` | POST | `/objects/list-memberships` | Add prospect to static list |
| `list.membership.remove` | DELETE | `/objects/list-memberships/{id}` | Remove membership (or PATCH opted-out as documented) |
| `campaign.list` | GET | `/objects/campaigns?fields=` | List AE campaigns (marketing campaigns — **not** Salesforce Campaign) |
| `email.send` | POST | `/objects/emails` | One-to-one email (`Marketing > Emails > Send One-to-one`) |
| `visitor.get` | GET | `/objects/visitors/{id}?fields=` | Read visitor |
| `form.get` | GET | `/objects/forms/{id}?fields=` | Read form |
| `trigger.listen` | — | Form Handler `successLocation` **or** poll | AE has **no** first-class inbound webhook product for prospect CRUD |

v4 fallback (config `pardot_version=4` only when v5 object missing): `{host}/api/prospect/version/4/do/read/id/{id}?format=json`. Prefer documenting a skip with `{ error }` rather than dual-stack unless the operator pins v4.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `prospect.get` | no | Chooses the row above |
| `pardot_version` | select | no | `5` | no | `5` (default) \| `4` |
| `env` | select | no | `prod` | no | `prod` → `pi.pardot.com`; `sandbox` → `pi.demo.pardot.com` |
| `business_unit_id` | string | yes | empty | no | extraHeaders `Pardot-Business-Unit-Id` |
| `access_token` | password | yes | empty | yes | Salesforce access token minted outside StudioQuack (`pardot_api`) |
| `timeout_seconds` | number | no | `30` | no | Floor 1 s |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | Prospect emails are PII |
| `output` | OUTPUT | `{ status, … }` or `{ error }` | Error code 89 = missing BU header |

FUNCTION ports: none.

## Input fields (by operation)

### `prospect.create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `email` | string | yes | Prospect email. AMPSEA: each create is a new prospect |
| `first_name` / `last_name` | string | no | v5 JSON `firstName` / `lastName` |
| `fields` | object | no | Additional v5 fields (`campaignId`, custom fields as documented) |

### `prospect.upsert`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `email` | string | yes | Match latest-by-email |
| `fields` | object | no | Upsert body as `upsertLatestByEmail` docs |
| `secondary_deleted_search` | boolean | no | Search deleted when AMPSEA |

### `prospect.get` | `visitor.get` | `form.get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `id` | string | yes | Account Engagement integer id |
| `fields` | string | no | Comma field list |

### `prospect.update`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `id` | string | yes | AE id (**not** email) |
| `fields` | object | yes | PATCH body |

### `prospect.delete`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `id` | string | yes | AE id |

### `list.membership.add`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `list_id` | number | yes | Static list id |
| `prospect_id` | number | yes | Prospect id |

### `list.membership.remove`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `membership_id` | number | yes | List membership id from create/query |

### `campaign.list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `fields` | string | no | e.g. `id,name` |
| `limit` / `offset` | number | no | As v5 query params |

### `email.send`

POST `/api/v5/objects/emails?fields=id,name,subject,…` — [email-v5](https://developer.salesforce.com/docs/marketing/pardot/guide/email-v5.html). HTTP **201** on success.

| Field | Type | Required | Purpose |
|---|---|---|---|
| `name` | string | yes | JSON `name` |
| `campaign_id` | number | yes | JSON `campaignId` |
| `prospect_id` | number | yes | JSON `prospectId` |
| `subject` | string | no | JSON `subject` |
| `html_message` | string | no* | JSON `htmlMessage` (include `%%unsubscribe%%` as AE requires) |
| `text_message` | string | no* | JSON `textMessage` |
| `sender_options` | array | no | JSON `senderOptions` (`type`, `address`, `name`, `userId`) |
| `reply_to_options` | array | no | JSON `replyToOptions` (`type=general_address`, `address`) |
| `folder_id` | number | no | JSON `folderId` |
| `tracker_domain_id` | number | no | JSON `trackerDomainId` |
| `email_template_id` | number | no | JSON `emailTemplateId` |
| `is_operational` | boolean | no | JSON `isOperational` — only if Operational Email Sending is enabled |

\*At least one of `html_message` / `text_message` / `email_template_id` must be set so the message is not empty.

### `trigger.listen`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `mode` | select | yes | `form_handler` \| `poll_prospects` |
| `form_handler_id` | number | form_handler | PATCH form-handler `successLocation` to ingress URL (`isDataForwarded=true`) |
| `callback_url` | string | form_handler | HTTPS ingress |
| `updated_after` | string | poll | ISO time for `updatedAtGreaterThan` prospect query |

Do **not** subscribe Salesforce Platform Events here (that is the Salesforce CRM node).

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `id` | number | create/get/update | AE object id |
| `prospect` / `visitor` / `form` / `email` / `membership` | object | matching ops | Requested `fields` only |
| `campaigns` | array | `campaign.list` | Page |
| `form_handler` | object | trigger form_handler | Updated handler |
| `error` | string | failure | Include AE numeric code when present (89, 201, …) |
| `http_status` | number | HTTP error | |
| `retry_after_seconds` | number | 429 | Daily AE API limits by edition |

## Legal

- Salesforce Account Engagement / MCAE terms. Connected App + BU id required.
- Prospects are PII. CAN-SPAM/CASL: one-to-one send still needs a lawful basis.
- Customer owns the AE business unit. StudioQuack does not store prospects.
- Do not mix this node’s tokens with the Salesforce CRM node’s object CRUD.

## Implementation notes

- JDK `HttpClient` only. No Pardot Java wrapper.
- Headers: `Authorization: Bearer {access_token}`, `Pardot-Business-Unit-Id: {business_unit_id}`, `Content-Type: application/json`.
- v5 JSON uses camelCase (`firstName`). Map INPUT snake_case.
- Missing BU id → `{ error }` no HTTP (would be AE 89).
- Body cap **500_000** bytes. Timeout 30 s.
- Logs: operation, version, AE ids, HTTP status — never token, email, or HTML body.
