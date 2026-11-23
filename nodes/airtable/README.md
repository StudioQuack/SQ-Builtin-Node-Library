# Airtable

In-process StudioQuack builtin. Handler: `OfficialApiNode` loading `nodes/airtable/api_spec.json`.

## Overview

Official Airtable Web API. Legacy API keys retired — use PAT or OAuth access token. Rate limits apply. ToS bars scraping.

- **Official API:** Airtable Web API
- **Docs:** https://airtable.com/developers/web/api/introduction
- **Researched:** 2026-08-20
- **Auth:** `bearer` bound to configuration `api_key` (password / secret). Never place credentials on INPUT.
- **Folder:** `nodes/airtable/`

## Operations

| operation | HTTP | Path | Notes |
|---|---|---|---|
| `create` | POST | `https://api.airtable.com/v0/{base_id}/{table_id}` | Official |
| `list` | GET | `https://api.airtable.com/v0/{base_id}/{table_id}` | Official |
| `get` | GET | `https://api.airtable.com/v0/{base_id}/{table_id}/{record_id}` | Official |
| `update` | PATCH | `https://api.airtable.com/v0/{base_id}/{table_id}/{record_id}` | Official |
| `upsert` | PATCH | `https://api.airtable.com/v0/{base_id}/{table_id}` | Official |
| `delete` | DELETE | `https://api.airtable.com/v0/{base_id}/{table_id}/{record_id}` | Official |
| `listen` | — | ingress | BPMN message event; this node does not open a socket |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — `operation` plus per-op fields | File payloads use type `file` (ref-id) |
| `output` | OUTPUT | `{ status, http_status, data }` | Failures never include secrets |

FUNCTION ports: none.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | create | no | Default official API call for this instance when input.operation is blank. EXACT accepted values: create, list, get, update, upsert, delete, listen. Default: create. Example: cr... |
| `api_key` | password | yes |  | yes | Provider credential for auth.type=bearer (configKey api_key). REQUIRED secret — StudioQuack secret binding only; never put this on INPUT. Never logged. Example: 'redacted-token'. |
| `base_id` | string | no |  | no | Default Airtable base id (app…) when INPUT base_id is empty. |
| `table_id` | string | no |  | no | Default table id or name when INPUT table_id is empty. |
| `typecast` | boolean | no |  | no | Coerce string values to the field type on create/update. Example: true. |
| `fields` | object | no |  | no | Airtable field name → value map for create and update (POST/PATCH record). Example: {"Name":"Ada","Status":"Active"}. Keep as the official write body; list-column filter is not ... |
| `maxRecords` | number | no |  | no | Max records to return on list. Example: 100. |
| `sort` | list | no |  | no | List sort: [{field, direction}]. direction asc/desc. Example: [{"field":"Name","direction":"asc"}]. |
| `offset` | string | no |  | no | List pagination offset token from the previous response. |
| `filterByFormula` | string | no |  | no | Airtable formula filter for list. Example: {Status}='Active'. |
| `pageSize` | number | no |  | no | List page size 1–100 (official pageSize query). Example: 50. |
| `view` | string | no |  | no | View name or id for list. Example: Grid view. |
| `record_id` | string | no |  | no | Record id path (rec…) for get / update / delete. Example: recXXXXXXXXXXXXXX. |
| `records` | list | no |  | no | Record array for upsert: [{id?, fields}]. Used with performUpsert. Example: [{"fields":{"Email":"ada@example.com","Name":"Ada"}}]. |
| `performUpsert` | object | no |  | no | Upsert options for upsert. Official: {fieldsToMergeOn: ["Email"]}. Example: {"fieldsToMergeOn":["Email"]}. |
| `timeout_seconds` | number | no | 25 | no | Connect+read timeout for the official HTTPS call. Positive integer. Blank/0/negative → 25. Example: 25. |

## Legal

- Terms: https://www.airtable.com/company/tos
- Official-API-first: no scrape, no unofficial wrappers.
- PII may leave the sandbox toward the provider. The operator is the data controller.
- `listen` / `webhook_listen` are not a StudioQuack Webhook node — use a BPMN message event delivered through the StudioQuack engine's message-event ingress.

## Implementation notes

- JDK HTTP via OfficialApiNode (no vendor SDK).
- HTTP 4xx/5xx raise a BPMN error (AUTH_FAILED / RATE_LIMITED / HTTP_<status> / REQUEST_FAILED); secrets are redacted.
- Extra provider headers (Notion-Version, Harvest-Account-Id, Basecamp User-Agent) are documented engine limits — do not add Java in this task.
