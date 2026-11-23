# Pardot (Account Engagement)

Official **Account Engagement (Pardot) REST v5** via `OfficialApiNode` / `api_spec.json`. **Separate from the StudioQuack `salesforce` node** — no Account/Contact/Opportunity SOQL here.

- **Docs:** https://developer.salesforce.com/docs/marketing/pardot/guide/overview.html · getting started https://developer.salesforce.com/docs/marketing/pardot/guide/getting-started.html · v5 transition https://developer.salesforce.com/docs/marketing/pardot/guide/transitioning-v5.html · prospects https://developer.salesforce.com/docs/marketing/pardot/guide/prospect-v5.html
- **Base (prod):** `https://pi.pardot.com/api/v5`
- **Sandbox/demo/dev:** `https://pi.demo.pardot.com` (not interpolated by OfficialApiNode; confirm via Setup → Installed Packages `b2bmaIntegration`: `b2bma` → prod, `pi3` → demo)
- **Auth:** Salesforce OAuth Bearer `access_token` (`pardot_api` scope) **plus** header `Pardot-Business-Unit-Id`. Customer owns the AE business unit.

## Operations

| operation | HTTP | Path |
|---|---|---|
| `prospect.create` | POST | `/objects/prospects` |
| `prospect.upsert` | POST | `/objects/prospects/do/upsertLatestByEmail` |
| `prospect.get` | GET | `/objects/prospects/{id}?fields=` |
| `prospect.update` | PATCH | `/objects/prospects/{id}` |
| `prospect.delete` | DELETE | `/objects/prospects/{id}` |
| `list.membership.add` | POST | `/objects/list-memberships` |
| `list.membership.remove` | DELETE | `/objects/list-memberships/{membership_id}` |
| `campaign.list` | GET | `/objects/campaigns` (AE campaigns — **not** Salesforce Campaign) |
| `email.send` | POST | `/objects/emails` |
| `visitor.get` | GET | `/objects/visitors/{id}` |
| `form.get` | GET | `/objects/forms/{id}` |
| `trigger.listen` | ingress | BPMN message event (no first-class AE inbound webhook product) |

v5 JSON uses camelCase. Prospect create/upsert/update accept official fields at the JSON root (`email`, `firstName`, `lastName`, `campaignId`, `company`, `jobTitle`, `phone`, `website`, `city`, `state`, `zip`, `country`, `source`, `score`, do-not-contact flags, …). Snake-case aliases (`first_name`, `html_message`) are also accepted at the root. `fields` on GET is the official `?fields=` return list; on write it is an escape hatch for custom `__c` properties. `email.send` takes `campaignId`, `prospectId`, `name`, `subject`, `htmlMessage`, `textMessage`. Docs: https://developer.salesforce.com/docs/marketing/pardot/guide/prospect-v5.html

## Config

| Name | Type | Secret | Purpose |
|---|---|---|---|
| `operation` | select | no | Default `prospect.get` |
| `access_token` | password | yes | Bearer (minted outside StudioQuack) |
| `business_unit_id` | string | no | extraHeaders `Pardot-Business-Unit-Id` |
| `pardot_version` | select | no | `5` (paths are v5) |
| `env` | select | no | `prod` (host is pi.pardot.com) |
| `timeout_seconds` | number | no | Default 30 |

## Ports

`input` / `output` only. Error code 89 = missing BU header. Never log token, email, or HTML body.

## Legal

- Salesforce Account Engagement / MCAE terms. Connected App + BU id required.
- Prospects are PII. CAN-SPAM/CASL: one-to-one send still needs a lawful basis.
- Do not mix this node’s tokens with the `salesforce` node’s object CRUD.

## OfficialApiNode notes

- Extra header `Pardot-Business-Unit-Id: {business_unit_id}` is sent on every call.
- `env=sandbox` does not rewrite the host to `pi.demo.pardot.com`.
- `pardot_version=4` is not dual-stacked.
- `trigger.listen` does not PATCH form-handler `successLocation`. Use a BPMN message event (or poll `prospect.get` in a loop).
- Do not subscribe Salesforce Platform Events here.
