# Slite

In-process StudioQuack builtin. Handler: `OfficialApiNode` loading `nodes/slite/api_spec.json`.

## Overview

Official Slite API v1. Header x-slite-api-key. Published rate limit 120 calls/minute. Often plan-gated.

- **Official API:** Slite API v1
- **Docs:** https://developers.slite.com/
- **Researched:** 2026-08-20
- **Auth:** `header` bound to configuration `api_key` (password / secret). Never place credentials on INPUT.
- **Folder:** `nodes/slite/`

## Operations

| operation | HTTP | Path | Notes |
|---|---|---|---|
| `doc_create` | POST | `/notes` | Body: `title` (required), `markdown` / `html` / `sliteml` |
| `doc_get` | GET | `/notes/{noteId}` | Response `content` is markdown/html/sliteml per optional `format` query |
| `doc_update` | PUT | `/notes/{noteId}` | Same body fields as create |
| `doc_delete` | DELETE | `/notes/{noteId}` | Irreversible — delete test notes after writes |
| `doc_search` | GET | `/search-notes` | Query `query` |
| `note_list` | GET | `/notes` | Optional `parentNoteId`, `ownerId`, `orderBy`, `cursor` |
| `channel_list` | GET | `/notes` | **No `GET /channels` in Slite API** — alias of `note_list` (sidebar roots / listing) |
| `account_me` | GET | `/me` | Authenticated user profile |

**AI Agent tools:** INPUT `content` is mapped to wire field `markdown` on create/update. Use `markdown` in DuckTape when possible.

**Provider quirks:** `listPosition` may serialize as a JSON double (scientific notation). Updates to unknown note ids may return HTTP 403 instead of 404.

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — `operation` plus per-op fields | File payloads use type `file` (ref-id) |
| `output` | OUTPUT | `{ status, http_status, data }` . Errors raise a BPMN error for the error boundary event (INVALID_INPUT, AUTH_FAILED, RATE_LIMITED, HTTP_<status>, REQUEST_FAILED); secrets are redacted.| Failures never include secrets |

FUNCTION ports: none.

## Configuration parameters

See `node_definition.json`. Always includes `operation` (select), the auth secret, `timeout_seconds` (default 25), and provider ids listed in the catalog row.

## Legal

- Terms: https://slite.com/terms
- Official-API-first: no scrape, no unofficial wrappers.
- PII may leave the sandbox toward the provider. The operator is the data controller.
- `listen` / `webhook_listen` are not a StudioQuack Webhook node — use a BPMN message event delivered through the StudioQuack engine's message-event ingress.

## Implementation notes

- JDK HTTP via OfficialApiNode (no vendor SDK).
- Errors raise a BPMN error for the error boundary event (INVALID_INPUT, AUTH_FAILED, RATE_LIMITED, HTTP_<status>, REQUEST_FAILED); secrets are redacted.
- Extra provider headers (Notion-Version, Harvest-Account-Id, Basecamp User-Agent) are documented engine limits — do not add Java in this task.
