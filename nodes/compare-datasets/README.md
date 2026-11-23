# Compare Datasets

- **Status:** done (package)
- **Catalog:** `docs/catalog/utilities-core.md`
- **Folder:** `nodes/compare-datasets/`
- **Researched:** 2026-08-20
- **Official API:** RFC 8259 JSON (in-process — no third-party network API)
- **Docs:** https://www.rfc-editor.org/rfc/rfc8259
- **Auth:** none — secret binding: `(none)`
- **Handler:** `OfficialApiNode` via `api_spec.json` (`SQ_TEMPLATE_ID_COMPARE_DATASETS`)

## Scope

Compares input arrays `a` and `b` using `match_fields`. Emits only_a / only_b / both (and optional fuzzy). No network.


## Operations (`operation` config values)

| operation | Kind | Path / RPC | When to use |
|---|---|---|---|
| `compare` | in-process | RFC 8259 | Diff `{a,b}` |

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | compare | no | Verb |
| `match_fields` | string | no | | no | Pairing keys |
| `difference_mode` | select | no | all | no | Which buckets |
| `fuzzy` | select | no | false | no | Near match |
| `timeout_seconds` | number | no | 25 | no | Run budget |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | Failures never include secrets |
| `output` | OUTPUT | object — `{ status, … }` or `{ error }` | JSON string from the handler |

FUNCTION ports: none.

## Legal

- No third-party API. Array contents may be personal data — minimize.

## Implementation notes

- Bound array length. Fuzzy match is optional and off by default.
- Timeouts from `timeout_seconds` (default 25). Size-cap payloads.
- Errors map to `{ error }` on OUTPUT; never throw past dispatch; never echo secrets.
- Do not write a new Java class; `OfficialApiNode` loads this folder.
