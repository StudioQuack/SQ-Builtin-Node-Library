# Drift (Salesloft)

- **Status:** planned — **DO NOT BUILD**
- **Catalog:** `docs/catalog/communication-messaging.md`
- **Folder when built:** `nodes/drift/` — **do not create**
- **Researched:** 2026-08-20
- **Official API:** Drift REST (historical) at `https://driftapi.com` — **product in gradual sunset**
- **Docs:** [devdocs.drift.com](https://devdocs.drift.com/docs/using-drift-apis) (still published 2026-08-20) · [Auth](https://devdocs.drift.com/docs/authentication-and-scopes.md) · [Creating a contact](https://devdocs.drift.com/docs/creating-a-contact.md) · Vendor sunset: [Clari + Salesloft newsroom, 2026-03-05](https://www.salesloft.com/company/newsroom/1-mind-partnership)
- **Auth:** OAuth 2.0 authorization code (documented) — **not to be implemented**

## Scope

**DO NOT BUILD this node.**

On **2026-03-05** Clari + Salesloft published that it is **gradually sunsetting the Drift conversational marketing product** and referring remaining customers to **1mind** under an exclusive agreement ([official newsroom](https://www.salesloft.com/company/newsroom/1-mind-partnership)). As of 2026-08-20 no public hard end-of-life date was found on that page. Developer pages at `devdocs.drift.com` still describe OAuth and REST against `https://driftapi.com`.

That is not a stable builtin surface:

1. The vendor has **officially announced sunset** and a successor that is a different company/API (1mind — **out of scope**, do not invent endpoints).
2. In **August–September 2025** a widely reported OAuth/token theft incident around Salesloft Drift integrations led many customers to **revoke Drift-connected credentials**. Remaining API access is not a programme StudioQuack should ship against.
3. LEGAL.md official-API-first does not require shipping a node whose vendor is winding the product down.

**Do not invent unofficial endpoints.** Do not scrape `app.drift.com`. Do not wrap 1mind unless 1mind publishes a documented public API and a later catalog task is opened.

Seed verbs (`create-contact`, `get-contact`, `update-contact`, `delete-contact`, `get-custom-attributes`, `send-message`, `listen`) are **not** v1 operations. Historical paths from still-published docs are recorded below **for archival research only**.

## Operations (`operation` config values)

None. Implementer must not register a handler, `templateId`, or folder.

| (archival only — do not implement) | HTTP | Path | Source |
|---|---|---|---|
| create contact | POST | `https://driftapi.com/contacts` | [Creating a Contact](https://devdocs.drift.com/docs/creating-a-contact.md) |
| retrieve / update / delete contact | GET / PATCH / DELETE | `https://driftapi.com/contacts/{id}` | [Retrieving](https://devdocs.drift.com/docs/retrieving-contact.md), [Updating](https://devdocs.drift.com/docs/updating-a-contact.md), [Deleting](https://devdocs.drift.com/docs/removing-a-contact.md) |
| list custom attributes | GET | documented under [Listing Custom Attributes](https://devdocs.drift.com/docs/listing-custom-attributes.md) | same |
| create message | POST | `https://driftapi.com/conversations/{conversationId}/messages` | [Creating a Message](https://devdocs.drift.com/docs/creating-a-message.md) |
| webhooks | — | Developer app event subscriptions | [Webhooks](https://devdocs.drift.com/docs/webhook-events-1.md) |
| OAuth token | POST | `https://driftapi.com/oauth2/token` | [Authentication](https://devdocs.drift.com/docs/authentication-and-scopes.md) |

Retired / unofficial verbs are omitted from the implementable table (empty).

## Configuration parameters

None. Do not add `access_token`, OAuth client secrets, or webhook URLs for Drift.

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | — | — | no | **N/A — DO NOT BUILD** |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | — | Do not ship |
| `output` | OUTPUT | — | Do not ship |

FUNCTION ports: none.

## Input fields (by operation)

None. There is no implementable `operation`.

## Output fields (by operation)

None.

## Legal

- Vendor sunset announcement: [salesloft.com/company/newsroom/1-mind-partnership](https://www.salesloft.com/company/newsroom/1-mind-partnership) (2026-03-05).
- Historical developer terms lived on Drift/Salesloft developer and product sites; do not accept them into a shipped node.
- Contacts and chat transcripts are personal data. A sunset product plus the 2025 credential-theft incident makes residual OAuth tokens high-risk. Operators still on Drift should export data via vendor-supported offboarding, not a new StudioQuack integration.
- Official-API-first: unpublished 1mind or Salesloft-internal APIs are **out of scope**.

## Implementation notes

- **Do not** add JDK HTTP calls to `driftapi.com` or `dev.drift.com`.
- **Do not** implement OAuth against `https://dev.drift.com/authorize`.
- If a future public **1mind** (or Clari + Salesloft) messaging API is documented, open a **new** catalog row — do not reuse `drift`.
- Errors: N/A (no handler).

### Unverified as of 2026-08-20

- Whether `https://driftapi.com` still issues tokens for remaining paying tenants (docs live; live probe not performed).
- Hard EOL date (vendor: “gradual” sunset; no date on the newsroom page).
