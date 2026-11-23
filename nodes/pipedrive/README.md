# Pipedrive

Official **Pipedrive REST v2** (deals, persons, orgs, activities, products, search) **+ v1** (notes, files, leads — no v2) via `OfficialApiNode` / `api_spec.json`. v1 endpoints that have v2 replacements are [out of support since 2026-08-01](https://developers.pipedrive.com/changelog/post/deprecated-apiv1-endpoints-become-out-of-support).

- **Docs:** https://developers.pipedrive.com/docs/api/v1 · v2 overview https://pipedrive.readme.io/docs/pipedrive-api-v2 · OAuth https://pipedrive.readme.io/docs/marketplace-oauth-api · webhooks v2 https://pipedrive.readme.io/docs/guide-for-webhooks-v2 · `webhooks:full` https://developers.pipedrive.com/changelog/post/introducing-new-oauth-scopes-for-webhooks
- **Base:** `https://api.pipedrive.com`
- **Auth:** OAuth Bearer `access_token` minted outside StudioQuack. Customer owns the Pipedrive company.

Webhooks **v2.0 default** since 2025-03-17; v1 webhooks deprecated through 2026. Subscribe **v2.0 only** when using the API; this node’s `trigger.listen` is **ingress**.

## Operations (selected)

v2: `activity.*`, `deal.*`, `dealProduct.*`, `person.*`, `org.*`, `product.list`  
v1: `lead.*`, `note.*`, `file.*`  
`deal.duplicate`: POST `/api/v2/deals/{id}/duplicate`  
`lead.update`: PATCH `/api/v1/leads/{id}`  

`trigger.listen`: **ingress** — OAuth scope **`webhooks:full`** required on the token that will be used when registering webhooks outside this node / on the platform ingress.

## Config

| Name | Type | Secret | Purpose |
|---|---|---|---|
| `operation` | select | no | Default `deal.list` |
| `access_token` | password | yes | Bearer minted outside StudioQuack (needs `webhooks:full` for trigger) |
| `timeout_seconds` | number | no | Default 30 |

## Ports

`input` / `output` only. Named create/update fields match the official v2 (deals/persons/orgs/activities) and v1 (leads/notes) body keys: `title`, `name`, `subject`, `content`, `owner_id`, `person_id`, `org_id`, `emails`, `phones`, `custom_fields`, … `fields` is an escape hatch whose keys merge at the JSON root (bodyMap `$`; named keys win). Official v2 custom fields belong in `custom_fields`. `file` on `file.create` is type `file` (platform ref-id). Failures never include tokens.

Deal create requires `title`. Lead requires `title` plus `person_id` or `organization_id`. Note requires `content` plus one of deal/person/org/lead/project/task. Activity person link uses `participants` (`[{person_id, primary:true}]`), not `person_id`.

## Legal

- Pipedrive Developer / Marketplace terms. Public apps use OAuth.
- CRM records are PII. Minimize fields. Never log tokens or note HTML.
- Webhook payloads go to operator-controlled ingress. Limit: 40 webhooks per user.

## OfficialApiNode notes

- `trigger.listen` is `kind: ingress` (no POST `/api/v1/webhooks`). Use a BPMN message event. Ingress must accept Pipedrive v2 payload `meta.action` / `meta.entity`.
- Bearer only (`access_token` minted outside StudioQuack).
- Host is `https://api.pipedrive.com`.
- Docs: https://developers.pipedrive.com/docs/api/v1
- `file.create` is `multipart/form-data` with part `file`.
- v2 updates are PATCH (wired). Pagination: one page per run (`cursor`).
