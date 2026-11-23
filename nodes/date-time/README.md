# Date & Time

- **Status:** done (package)
- **Catalog:** `docs/catalog/utilities-core.md`
- **Folder:** `nodes/date-time/`
- **Researched:** 2026-08-22
- **Official API:** ISO-8601, RFC 3339 (updated by RFC 9557), IANA Time Zone Database
- **Docs:** https://www.rfc-editor.org/rfc/rfc3339 · https://www.rfc-editor.org/rfc/rfc9557
- **Auth:** none — secret binding: `(none)`
- **Handler:** `OfficialApiNode` via `api_spec.json` (`SQ_TEMPLATE_ID_DATE_TIME`)

## Scope

In-process date math on the daemon clock. Timezone is an IANA name (e.g. America/New_York).


## Operations (`operation` config values)

| operation | Kind | Path / RPC | When to use |
|---|---|---|---|
| `now`…`round` | in-process | RFC 3339 | Clock/math |

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | now | no | Which capability to run. EXACT accepted values: now / add / subtract / format / extract / diff / round. Required. Example: 'now'. |
| `timezone` | string | no | UTC | no | IANA TZ. Default UTC. |
| `format` | string | no | iso | no | DateTimeFormatter pattern or iso. Default iso. |
| `unit` | select | no | days | no | seconds/minutes/hours/days/weeks/months/years. |
| `datetime` | string | no |  | no | RFC 3339 / ISO-8601. add/subtract uses now when this is empty. INPUT wins. |
| `amount` | number | no |  | no | How many units to add or subtract. Empty uses 1. INPUT wins. |
| `other` | string | no |  | no | The other RFC 3339 datetime. INPUT wins. |
| `part` | string | no |  | no | EXACT: year, month, day, hour, minute, second, dow. INPUT wins. |
| `timeout_seconds` | number | no | 25 | no | Connect+read / run budget in seconds. Positive integer. Blank/0/negative → 25. Keep at or below the sandbox request budget. Example: 25. |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | Failures never include secrets |
| `output` | OUTPUT | object — `{ status, … }` or `{ error }` | JSON string from the handler |

FUNCTION ports: none.

## Legal

- No third-party time API.

## Implementation notes

- java.time. Invalid TZ → error. No network.
- Timeouts from `timeout_seconds` (default 25). Size-cap payloads.
- Errors map to `{ error }` on OUTPUT; never throw past dispatch; never echo secrets.
- Do not write a new Java class; `OfficialApiNode` loads this folder.
