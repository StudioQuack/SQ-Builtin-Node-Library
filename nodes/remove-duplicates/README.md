# Remove Duplicates

- **Status:** done (package)
- **Catalog:** `docs/catalog/utilities-core.md`
- **Folder:** `nodes/remove-duplicates/`
- **Researched:** 2026-08-20
- **Official API:** RFC 8259 JSON (in-process — no third-party network API)
- **Docs:** https://www.rfc-editor.org/rfc/rfc8259
- **Auth:** none — secret binding: `(none)`
- **Handler:** `RemoveDuplicatesNode` (`TransformHandlers`)

## Scope

Drops duplicate rows. Without `keys`, identity is the whole item (case-sensitive, first wins). With `keys`, identity is the listed field paths on **object** items only; scalars/strings are deduped by value. `unique_history` consults a per-env bounded store (Redis in multi-replica). Not a Filter node.


## Operations (`operation` config values)

| operation | Kind | Path / RPC | When to use |
|---|---|---|---|
| `unique` | in-process | RFC 8259 | Dedupe this payload |
| `unique_history` | in-process + store | RFC 8259 | Dedupe vs env history |

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | unique | no | Verb |
| `keys` | string | no | | no | Identity keys |
| `history_scope` | string | no | | no | History bucket |
| `timeout_seconds` | number | no | 25 | no | Run budget |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | Failures never include secrets |
| `output` | OUTPUT | object — `{ status, … }` or `{ error }` | JSON string from the handler |

FUNCTION ports: none.

## Legal

- History may contain personal data — bound TTL and size. No third-party API.

## Implementation notes

- unique_history must not use process memory (N replicas). Cap history entries.
- Timeouts from `timeout_seconds` (default 25). Size-cap payloads.
- Errors map to `{ error }` on OUTPUT; never throw past dispatch; never echo secrets.
- Do not write a new Java class; `OfficialApiNode` loads this folder.
