# <Node name>

- **Status:** planned
- **Catalog:** `docs/catalog/<category>.md`
- **Folder when built:** `nodes/<kebab-key>/`
- **Researched:** YYYY-MM-DD
- **Official API:** <name + version>
- **Docs:** <canonical URL>
- **Auth:** <oauth2 | api_key | service_account | none> — secret binding: `<param>`

## Scope

One paragraph: what this node does, and which official surface it calls.
State any "do not build" finding (no public API, ToS forbids, BPMN overlap).

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `…` | GET/POST/… | `/vX/…` | … |

Retired / unofficial verbs are omitted, not listed as "later".

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | first verb | no | Chooses the row above |
| `…` | string/number/boolean/file/password | | | | |

File payloads (upload, download, convert) use type `file` (platform ref-id),
never inline bytes, on both this table and the ports below.

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | |
| `output` | OUTPUT | object — `{ status, … }` | Failures throw `StudioQuackError` / call `helper.fail` (never include secrets) |

FUNCTION ports: none unless this node must invoke another node N times.

## Input fields (by operation)

### `operation = …`

| Field | Type | Required | Purpose |
|---|---|---|---|

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|

## Legal

- ToS / developer policy URL
- Rate limits / quota notes (as published)
- PII / residency / prohibited uses
- Official-API-first: no scrape, no unofficial Discord/web wrapper

## Implementation notes

- JDK HTTP only (no vendor SDK) unless a reason is stated.
- Timeouts, size caps, retry posture.
- Error mapping: HTTP 4xx/5xx → throw `StudioQuackError` (or call `helper.fail`) so the dispatcher posts `/v1/helper/fail` and the engine routes to the error boundary (see `docs/architecture.md`); never put a top-level `error` key on OUTPUT.
