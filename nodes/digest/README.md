# Digest

- **Status:** done (package)
- **Catalog:** `docs/catalog/utilities-core.md`
- **Folder:** `nodes/digest/`
- **Researched:** 2026-08-22
- **Official API:** StudioQuack per-environment buffer (platform — not a third-party API). `window` is an ISO-8601 duration; `timezone` is IANA TZ.
- **Docs:** https://www.iana.org/time-zones
- **Auth:** none — secret binding: `(none)`
- **Handler:** `OfficialApiNode` via `api_spec.json` (`SQ_TEMPLATE_ID_DIGEST`)

## Scope

Accumulates items for a time window. `flush` is invoked by the workflow (often a BPMN timer). This node has no cron. Store is per-env and bounded; Redis when multiple backend replicas exist.


## Operations (`operation` config values)

| operation | Kind | Path / RPC | When to use |
|---|---|---|---|
| `buffer` | platform store | append | Add item |
| `flush` | platform store | emit | Drain (timer may call) |

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | buffer | no | Verb |
| `window` | string | no | | no | Duration (ISO-8601 or minutes; max 30 days) |
| `timezone` | string | no | UTC | no | IANA TZ |
| `max_items` | number | no | 1000 | no | Cap |
| `timeout_seconds` | number | no | 25 | no | Budget |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | Failures never include secrets |
| `output` | OUTPUT | object — `{ status, … }` or `{ error }` | JSON string from the handler |

FUNCTION ports: none.

## Legal

- Platform buffer. Items may be personal data — bound max_items and TTL.
- Not a Wait/Schedule node.

## Implementation notes

- No in-JVM store for production.
- max_items cap. window uses timezone; window capped at 30 days.
- buffer `item` must be a JSON object (plain strings and scalars are rejected).
- Timeouts from `timeout_seconds` (default 25). Size-cap payloads.
- Errors map to `{ error }` on OUTPUT; never throw past dispatch; never echo secrets.
- Do not write a new Java class; `OfficialApiNode` loads this folder.
