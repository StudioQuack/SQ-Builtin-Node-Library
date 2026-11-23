# Numbers

- **Status:** done (package)
- **Catalog:** `docs/catalog/utilities-core.md`
- **Folder:** `nodes/numbers/`
- **Researched:** 2026-08-20
- **Official API:** IEEE 754 + Unicode LDML numbers (in-process)
- **Docs:** https://www.unicode.org/reports/tr35/tr35-numbers.html
- **Auth:** none — secret binding: `(none)`
- **Handler:** `NumbersNode` (`SQ_TEMPLATE_ID_NUMBERS`)

## Scope

In-process numeric helpers. `random_int` is CSPRNG.


## Operations

INPUT `operation` overrides the instance select. INPUT `op` overrides the instance math operator. Blank INPUT falls back to config (`format`, `+`).

| operation | Kind | Path / RPC | When to use |
|---|---|---|---|
| `format` | in-process | LDML | Display |
| `round` | in-process | IEEE 754 | Round |
| `math` | in-process | +−×÷% | Arithmetic |
| `random_int` | in-process | SecureRandom | CSPRNG int |

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | format | no | Verb |
| `precision` | number | no | 2 | no | Scale |
| `op` | select | no | + | no | Arithmetic |
| `timeout_seconds` | number | no | 25 | no | Budget |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | Failures never include secrets |
| `output` | OUTPUT | object — `{ status, … }` or `{ error }` | JSON string from the handler |

FUNCTION ports: none.

## Legal

- No third-party API. Do not use random_int for cryptographic keys (use Crypto).

## Implementation notes

- SecureRandom for random_int. Division by zero → error.
- Timeouts from `timeout_seconds` (default 25). Size-cap payloads.
- Errors map to `{ error }` on OUTPUT; never throw past dispatch; never echo secrets.
- `NumbersNode` runs in-process. INPUT `operation` selects format, round, math, or random_int.
