# Linear

In-process StudioQuack builtin. Handler: `OfficialApiNode` loading `nodes/linear/api_spec.json`.

## Overview

Official Linear GraphQL. POST the same /graphql path with body query + variables per operation. OAuth app review for public apps.

- **Official API:** Linear GraphQL API
- **Docs:** https://linear.app/developers/graphql
- **Researched:** 2026-08-20
- **Auth:** `bearer` bound to configuration `api_key` (password / secret). Never place credentials on INPUT.
- **Folder:** `nodes/linear/`

## Operations

| operation | HTTP | Path | Notes |
|---|---|---|---|
| `issue_create` | POST | `https://api.linear.app/graphql` | Official |
| `issue_get` | POST | `https://api.linear.app/graphql` | Official |
| `issue_list` | POST | `https://api.linear.app/graphql` | Official |
| `issue_update` | POST | `https://api.linear.app/graphql` | Official |
| `issue_delete` | POST | `https://api.linear.app/graphql` | Official |
| `issue_add_link` | POST | `https://api.linear.app/graphql` | Official |
| `comment_add` | POST | `https://api.linear.app/graphql` | Official |
| `listen` | — | ingress | BPMN message event; this node does not open a socket |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — `operation` plus per-op fields | File payloads use type `file` (ref-id) |
| `output` | OUTPUT | `{ status, http_status, data }` or `{status,http_status,data}` | Failures never include secrets |

FUNCTION ports: none.

## Configuration parameters

See `node_definition.json`. Always includes `operation` (select), the auth secret, `timeout_seconds` (default 25), and provider ids listed in the catalog row.

## Legal

- Terms: https://linear.app/terms
- Official-API-first: no scrape, no unofficial wrappers.
- PII may leave the sandbox toward the provider. The operator is the data controller.
- `listen` / `webhook_listen` are not a StudioQuack Webhook node — use a BPMN message event delivered through the StudioQuack engine's message-event ingress.

## Implementation notes

- JDK HTTP via OfficialApiNode (no vendor SDK).
- HTTP 4xx/5xx → `{status,http_status,data}` on OUTPUT; never throw past dispatch.
- Extra provider headers (Notion-Version, Harvest-Account-Id, Basecamp User-Agent) are documented engine limits — do not add Java in this task.
